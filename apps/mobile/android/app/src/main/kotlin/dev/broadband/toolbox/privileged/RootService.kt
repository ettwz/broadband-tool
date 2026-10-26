package dev.broadband.toolbox.privileged

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.annotation.Keep
import dev.broadband.toolbox.BuildConfig
import dev.broadband.toolbox.FlutterError
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Shizuku-owned, bounded process. No shell endpoint and no direct su fallback. */
@Keep
class RootService @Keep constructor(context: Context) : IRootService.Stub() {
    private val appUid = context.applicationInfo.uid
    private val generation = System.currentTimeMillis()
    private val jobs = ConcurrentHashMap<String, RootWork>()
    private val workers = Executors.newFixedThreadPool(3)
    private val watchdog = Executors.newSingleThreadScheduledExecutor()
    private val packets = PacketBroker()
    private val operations = RootOperations(packets)
    private val dns = SystemDnsAdapter()
    private val ethernet = EthernetAdapter(context)
    private val transactions = EthernetTransactions(ethernet, java.io.File(context.filesDir, "ethernet-recovery.json"),
        { SystemClock.elapsedRealtime() }, { System.currentTimeMillis() },
        runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown"))
    private val restoring = java.util.concurrent.atomic.AtomicBoolean(false)
    private val auditing = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var client: IBinder? = null
    @Volatile private var lastHeartbeat = SystemClock.elapsedRealtime()
    @Volatile private var stopping = false
    private val clientDeath = IBinder.DeathRecipient { disconnect("CLIENT_DISCONNECTED") }

    init {
        watchdog.scheduleAtFixedRate({
            if (SystemClock.elapsedRealtime() - lastHeartbeat > 6500) disconnect("HEARTBEAT_LOST")
            else if (transactions.needsRestore()) restoreAsync("期限已到")
        }, 1, 1, TimeUnit.SECONDS)
        watchdog.scheduleAtFixedRate({
            if (!stopping && auditing.compareAndSet(false, true)) workers.execute {
                try { transactions.auditActive() } finally { auditing.set(false) }
            }
        }, 5, 5, TimeUnit.SECONDS)
    }
    private fun enforceCaller(requireRoot: Boolean = true) {
        if (Binder.getCallingUid() != appUid) throw SecurityException("Only the owning app may call this service")
        if (stopping) throw IllegalStateException("Root service is stopping")
        if (requireRoot && Process.myUid() != 0) throw SecurityException("Root mode required")
    }
    override fun connect(client: IBinder): String {
        enforceCaller(false)
        this.client?.unlinkToDeath(clientDeath, 0)
        this.client = client
        client.linkToDeath(clientDeath, 0)
        lastHeartbeat = SystemClock.elapsedRealtime()
        return snapshot().toString()
    }
    override fun heartbeat(): String {
        enforceCaller(false)
        lastHeartbeat = SystemClock.elapsedRealtime()
        return snapshot().toString()
    }
    private fun snapshot(): JSONObject {
        val root = Process.myUid() == 0
        val interfaces = RootInterfaces.list()
        val rawReason = if (!root) "需要 Root 模式" else interfaces.firstOrNull { it.ethernet }?.let { packets.probe(it) }
            ?: if (interfaces.none { it.ethernet }) "没有支持的物理接口" else null
        fun capability(name: String, supported: Boolean, reason: String) = JSONObject().put("name", name).put("supported", supported).put("reason", reason)
        return JSONObject().put("uid", Process.myUid()).put("generation", generation)
            .put("interfaces", JSONArray(interfaces.map { it.json(generation) }))
            .put("capabilities", JSONArray(listOf(
                capability("capture", root && rawReason == null, rawReason ?: "原始报文权限可用"),
                capability("link", root && rawReason == null, rawReason ?: "ARP / DHCP 报文权限可用"),
                capability("systemDns", root && dns.available, dns.reason),
                capability("ethernet", root && ethernet.qualified, ethernet.reason))))
            .put("recovery", transactions.status() ?: JSONObject.NULL)
    }
    @Synchronized override fun start(request: String, output: ParcelFileDescriptor?, callback: IRootEvents): String {
        enforceCaller()
        try {
            if (request.length > 16384) throw RootFailure("INVALID_INPUT", "参数过长")
            if (client?.isBinderAlive != true || SystemClock.elapsedRealtime() - lastHeartbeat > 6500) throw RootFailure("PRIVILEGE_LOST", "授权连接已失效")
            val data = JSONObject(request)
            if (data.getLong("generation") != generation) throw RootFailure("PRIVILEGE_LOST", "权限服务已经更换，请重新开始")
            val id = data.getString("id")
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) throw RootFailure("INVALID_INPUT", "无效请求标识")
            if (jobs.containsKey(id)) throw RootFailure("BUSY", "此操作已经开始")
            val kind = data.getString("kind")
            if (kind !in setOf("root.capture", "root.arp", "root.dhcp", "root.systemDns", "root.interfaceInfo", "root.ethernetPreview", "root.ethernetApply", "root.ethernetConfirm", "root.ethernetRestore")) throw RootFailure("UNSUPPORTED", "未知高级操作")
            dev.broadband.toolbox.TaskValidation.validate(kind, data.getJSONObject("parameters"))
            if (transactions.blocksDiagnostics() && kind !in setOf("root.ethernetConfirm", "root.ethernetRestore")) throw RootFailure("RECOVERY_PENDING", "请先确认或恢复临时有线配置")
            if (kind == "root.ethernetApply" && jobs.isNotEmpty()) throw RootFailure("BUSY", "应用配置前请停止诊断和抓包")
            if (kind == "root.capture" && jobs.values.any { it.kind == kind }) throw RootFailure("BUSY", "已有抓包正在运行")
            if (kind != "root.capture" && jobs.values.any { it.kind != "root.capture" }) throw RootFailure("BUSY", "已有诊断正在运行")
            val work = RootWork(data, callback)
            jobs[id] = work
            workers.execute {
                var state = "completed"
                var failure = JSONObject()
                try {
                    work.check()
                    if (kind == "root.interfaceInfo") {
                        val ref = RootInterfaces.require(data.getString("interfaceId"))
                        work.emit("result", JSONObject(EthernetNative().snapshot(ref.index)).put("interface", ref.name).put("mtu", ref.mtu))
                    } else if (kind.startsWith("root.ethernet")) {
                        val p = data.getJSONObject("parameters")
                        val transaction = when (kind) {
                            "root.ethernetPreview" -> transactions.preview(data.getString("interfaceId"), p)
                            "root.ethernetApply" -> transactions.apply(p.getString("transactionId"), BuildConfig.DEBUG && p.optBoolean("qualificationRun", false))
                            "root.ethernetConfirm" -> transactions.confirm(p.getString("transactionId"))
                            else -> transactions.restore(p.getString("transactionId"))
                        }
                        work.emit("result", JSONObject().put("transaction", transaction))
                    } else if (kind == "root.systemDns") {
                        work.emit("result", dns.flush(data.getJSONObject("parameters").getInt("netId")))
                    } else operations.run(work, output)
                    work.check()
                } catch (error: Exception) {
                    val code = when (error) { is RootFailure -> error.code; is FlutterError -> error.code; else -> "ENGINE_FAILED" }
                    state = if (code == "CANCELLED") "cancelled" else if (work.cancelled.get() != null) "interrupted" else "failed"
                    failure = JSONObject().put("errorCode", code).put("message", error.message ?: "高级操作失败")
                } finally {
                    runCatching { output?.close() }
                    jobs.remove(id)
                    work.emit(state, failure)
                }
            }
            return JSONObject().put("accepted", true).toString()
        } catch (error: Exception) {
            output?.close()
            return JSONObject().put("accepted", false).put("errorCode", (error as? RootFailure)?.code ?: "INVALID_INPUT")
                .put("message", error.message ?: "请求无效").toString()
        }
    }
    override fun cancel(requestId: String) { enforceCaller(); jobs[requestId]?.cancel() }
    override fun stopAll() { enforceCaller(false); jobs.values.forEach { it.cancel("APP_BACKGROUND") }; restoreAsync("应用已进入后台") }
    private fun restoreAsync(reason: String) {
        val status = transactions.status() ?: return
        if (status.optString("state") in setOf("restored", "preview", "recovery_pending") || !restoring.compareAndSet(false, true)) return
        workers.execute {
            try { transactions.restore(status.getString("transactionId"), reason) }
            finally { restoring.set(false) }
        }
    }
    private fun disconnect(reason: String) {
        synchronized(this) { if (stopping) return; stopping = true }
        jobs.values.forEach { it.cancel(reason) }
        restoreAsync("应用连接已断开")
        // A dead client cannot keep a root capture or worker process alive indefinitely.
        watchdog.schedule({ packets.close(); exitProcess(0) }, if (restoring.get()) 15000 else 1500, TimeUnit.MILLISECONDS)
    }
    override fun destroy() {
        val caller = Binder.getCallingUid()
        if (caller != appUid && caller != 0 && caller != 2000) throw SecurityException("Unauthorized destroy")
        disconnect("SERVICE_DESTROYED")
    }
}
