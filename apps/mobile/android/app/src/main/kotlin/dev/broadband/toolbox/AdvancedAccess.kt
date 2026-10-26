package dev.broadband.toolbox

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import dev.broadband.toolbox.privileged.IRootEvents
import dev.broadband.toolbox.privileged.IRootService
import dev.broadband.toolbox.privileged.RootService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** App authorization and actual worker identity remain separate. */
class AdvancedAccess(private val context: Context, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadScheduledExecutor()
    private val clientLife = Binder()
    @Volatile private var worker: IRootService? = null
    @Volatile private var workerState: JSONObject? = null
    @Volatile private var binding = false
    @Volatile private var closed = false
    @Volatile private var foreground = true
    private var generation = 0L
    private var pending: ((Result<PrivilegeData>) -> Unit)? = null
    val captures = File(context.filesDir, "captures")
    private val recoveryFile = File(context.filesDir, "ethernet-recovery-shadow.json")
    @Volatile private var recoveryShadow = runCatching { if (recoveryFile.isFile) JSONObject(recoveryFile.readText()) else null }.getOrNull()
    private val serviceArgs = Shizuku.UserServiceArgs(ComponentName(context, RootService::class.java))
        .daemon(true).processNameSuffix("privileged").debuggable(BuildConfig.DEBUG).version(BuildConfig.ROOT_SERVICE_VERSION)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            io.execute {
                runCatching {
                    val remote = IRootService.Stub.asInterface(binder)
                    val state = JSONObject(remote.connect(clientLife))
                    binder.linkToDeath({ lost() }, 0)
                    if (!closed) { worker = remote; acceptState(state) }
                }.onFailure { lost() }
                binding = false
                main.post { changed() }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { lost() }
    }
    private val received = Shizuku.OnBinderReceivedListener { generation++; main.post { bindIfAllowed(); changed() } }
    private val dead = Shizuku.OnBinderDeadListener {
        lost()
        main.post {
            pending?.invoke(Result.failure(FlutterError("SHIZUKU_UNAVAILABLE", "Shizuku 已断开")))
            pending = null; changed()
        }
    }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code == 7431) main.post {
            generation++; bindIfAllowed()
            pending?.invoke(Result.success(snapshot())); pending = null; changed()
        }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        io.scheduleAtFixedRate({
            if (closed || !foreground) return@scheduleAtFixedRate
            val allowed = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && Shizuku.getUid() == 0 }.getOrDefault(false)
            if (!allowed) { if (worker != null) lost(); return@scheduleAtFixedRate }
            val remote = worker
            if (remote != null) runCatching { acceptState(JSONObject(remote.heartbeat())) }.onFailure { lost() }
            else main.post { bindIfAllowed() }
            main.post { if (!closed) changed() }
        }, 1, 2, TimeUnit.SECONDS)
    }
    private fun lost() {
        worker = null; workerState = null; binding = false; generation++
        main.post { if (!closed) changed() }
    }
    private fun acceptState(state: JSONObject) {
        workerState = state
        val recovery = state.optJSONObject("recovery")
        if (recovery != null && recovery.optString("state") !in setOf("preview", "restored")) {
            recoveryShadow = JSONObject(recovery.toString())
            runCatching { recoveryFile.writeText(recovery.toString()) }
        } else if (recovery?.optString("state") == "restored") {
            recoveryShadow = null; recoveryFile.delete()
        }
    }
    private fun recovery(state: JSONObject?): JSONObject? = state?.optJSONObject("recovery") ?: recoveryShadow?.let {
        JSONObject(it.toString()).put("state", "recovery_pending").put("message", "恢复未完成：高级执行端已断开，请重新连接并核对原配置")
    }
    fun configBusy(): Boolean = recovery(workerState)?.optString("state") in setOf("prepared", "applying", "awaiting_confirmation", "restoring")
    private fun bindIfAllowed() {
        if (closed || !foreground || binding || worker != null) return
        if (!runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && Shizuku.getUid() == 0 }.getOrDefault(false)) return
        binding = true
        runCatching { Shizuku.bindUserService(serviceArgs, connection) }.onFailure { binding = false }
        main.postDelayed({ if (!closed && worker == null) { binding = false; changed() } }, 10000)
    }
    fun snapshot(): PrivilegeData {
        val installed = runCatching { context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0) }.isSuccess
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val allowed = alive && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        val uid = if (allowed) runCatching { Shizuku.getUid().toLong() }.getOrNull() else null
        val state = workerState?.takeIf { allowed && uid == 0L && worker?.asBinder()?.isBinderAlive == true }
        val workerUid = state?.optLong("uid")
        val status = when {
            !installed -> "not_installed"
            !alive -> "not_running"
            !allowed -> "unauthorized"
            uid != 0L -> "shell_mode"
            workerUid != 0L -> "capability_limited"
            else -> "root_ready"
        }
        val reason = when (status) {
            "not_installed" -> "请先安装并启动 Shizuku"
            "not_running" -> "请在 Shizuku 中以 Root 模式启动服务"
            "unauthorized" -> "请通过 Shizuku 授权本应用"
            "shell_mode" -> "当前为 ADB 模式，需要 Root 模式"
            "root_ready" -> "高级访问已连接；各功能按系统支持情况启用"
            else -> if (binding) "正在连接高级执行服务" else "高级执行服务尚未就绪"
        }
        val capabilities = state?.getJSONArray("capabilities")?.let { items ->
            (0 until items.length()).map { items.getJSONObject(it).let { item -> CapabilityData(item.getString("name"), item.getBoolean("supported"), item.getString("reason")) } }
        } ?: listOf("capture", "link", "systemDns", "ethernet").map { CapabilityData(it, false, reason) }
        val interfaces = state?.getJSONArray("interfaces")?.let { items ->
            (0 until items.length()).map { items.getJSONObject(it).let { item ->
                InterfaceData(item.getString("id"), item.getString("name"), item.getLong("index"), item.getLong("generation"), item.getString("transport"),
                    item.getJSONArray("addresses").let { a -> (0 until a.length()).map(a::getString) },
                    item.getJSONArray("prefixes").let { a -> (0 until a.length()).map(a::getLong) }, item.getBoolean("up"), item.getLong("mtu"))
            } }
        } ?: emptyList()
        return PrivilegeData(status, state?.getLong("generation") ?: generation, allowed, capabilities, interfaces, uid, workerUid, reason, recovery(state)?.toString())
    }
    fun request(callback: (Result<PrivilegeData>) -> Unit) {
        val current = snapshot()
        if (current.status in setOf("not_installed", "not_running")) { callback(Result.success(current)); return }
        if (current.authorized) { bindIfAllowed(); callback(Result.success(snapshot())); return }
        if (pending != null) { callback(Result.failure(FlutterError("BUSY", "已有授权请求正在处理"))); return }
        pending = callback
        runCatching { Shizuku.requestPermission(7431) }.onFailure { pending = null; callback(Result.failure(FlutterError("SHIZUKU_UNAVAILABLE", "无法请求 Shizuku 授权"))) }
    }
    fun validate(request: TaskRequestData) {
        val state = snapshot()
        if (state.status != "root_ready" || state.workerUid != 0L || request.privilegeGeneration != state.generation) throw FlutterError("PRIVILEGE_LOST", state.message)
        val capability = when (request.kind) { "root.capture" -> "capture"; "root.arp", "root.dhcp" -> "link"; "root.systemDns" -> "systemDns"; else -> "ethernet" }
        val supported = state.capabilities.firstOrNull { it.name == capability }
        val recoveryOperation = request.kind in setOf("root.interfaceInfo", "root.ethernetPreview", "root.ethernetConfirm", "root.ethernetRestore")
        val qualification = BuildConfig.DEBUG && request.kind == "root.ethernetApply" && JSONObject(request.parametersJson).optBoolean("qualificationRun")
        if (supported?.supported != true && !recoveryOperation && !qualification) throw FlutterError("CAPABILITY_DENIED", supported?.reason ?: "功能不可用")
        if (request.kind !in setOf("root.systemDns", "root.ethernetConfirm", "root.ethernetRestore", "root.ethernetApply") && state.interfaces.none { it.id == request.interfaceId }) throw FlutterError("INTERFACE_CHANGED", "请选择当前可用接口")
        if (request.kind in setOf("root.ethernetPreview", "root.ethernetApply") && recoveryShadow != null && workerState?.optJSONObject("recovery") == null) throw FlutterError("RECOVERY_PENDING", "执行端缺少原恢复记录，请先在系统设置核对网络状态")
    }
    fun run(session: TaskSession) {
        validate(session.request)
        val remote = worker ?: throw FlutterError("PRIVILEGE_LOST", "高级服务已断开")
        val request = session.request
        val done = CountDownLatch(1)
        var terminal: JSONObject? = null
        var finalState: String? = null
        val captureFile = if (request.kind == "root.capture") prepareCapture(session.id, JSONObject(request.parametersJson).optInt("maxMb", 20)) else null
        val output = captureFile?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY) }
        val callback = object : IRootEvents.Stub() {
            override fun onEvent(id: String, state: String, payload: String) {
                if (id != session.id) return
                val data = JSONObject(payload)
                if (state in setOf("completed", "cancelled", "interrupted", "failed")) { terminal = data; finalState = state; done.countDown() }
                else {
                    if (data.optJSONObject("transaction") != null) {
                        workerState?.let { previous -> acceptState(JSONObject(previous.toString()).put("recovery", data.getJSONObject("transaction"))) }
                        main.post { changed() }
                    }
                    if (state == "result" && captureFile != null) File(captures, "${session.id}.json").writeText(data.toString())
                    session.emit(state, data)
                }
            }
        }
        session.onCancel { runCatching { remote.cancel(session.id) } }
        try {
            session.checkActive()
            val accepted = JSONObject(remote.start(JSONObject().put("id", session.id).put("kind", request.kind)
                .put("generation", request.privilegeGeneration).put("interfaceId", request.interfaceId)
                .put("parameters", JSONObject(request.parametersJson)).toString(), output, callback))
            if (!accepted.optBoolean("accepted")) throw FlutterError(accepted.optString("errorCode"), accepted.optString("message"))
            output?.close()
            val end = android.os.SystemClock.elapsedRealtime() + 315000
            var cancelBy = Long.MAX_VALUE
            while (true) {
                try { if (done.await(100, TimeUnit.MILLISECONDS)) break }
                catch (_: InterruptedException) { Thread.interrupted(); runCatching { remote.cancel(session.id) }; cancelBy = android.os.SystemClock.elapsedRealtime() + 2500 }
                if (!remote.asBinder().isBinderAlive || worker !== remote) throw FlutterError("PRIVILEGE_LOST", "高级服务已断开，操作已中止")
                if (android.os.SystemClock.elapsedRealtime() > minOf(end, cancelBy)) throw FlutterError("TIMEOUT", "等待高级执行端停止超时")
            }
            if (finalState == "failed" || finalState == "interrupted") throw FlutterError(terminal?.optString("errorCode") ?: "ENGINE_FAILED", terminal?.optString("message"))
            session.checkActive()
        } finally { output?.close() }
    }
    private fun prepareCapture(id: String, maxMb: Int): File {
        captures.mkdirs()
        val total = captures.listFiles()?.filter { it.extension == "pcap" }?.sumOf(File::length) ?: 0
        if (total + maxMb.coerceIn(5, 100) * 1000000L > 200000000) throw FlutterError("CAPTURE_QUOTA", "抓包文件空间不足，请先清理已有文件")
        return File(captures, "$id.pcap")
    }
    fun captureFile(id: String): File {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) throw FlutterError("INVALID_INPUT", "无效文件标识")
        val metadata = File(captures, "$id.json")
        val file = File(captures, "$id.pcap")
        if (!metadata.isFile || !file.isFile || !JSONObject(metadata.readText()).optBoolean("fileComplete")) throw FlutterError("CAPTURE_INCOMPLETE", "抓包文件尚未完成")
        return file
    }
    fun listCaptures(): List<CaptureFileData> = captures.listFiles()?.filter { it.extension == "pcap" }?.sortedByDescending(File::lastModified)?.map { file ->
        val data = runCatching { JSONObject(File(captures, "${file.nameWithoutExtension}.json").readText()) }.getOrNull()
        CaptureFileData(file.nameWithoutExtension, file.lastModified(), file.length(), data?.optLong("packets") ?: 0,
            data?.optBoolean("fileComplete") ?: false, data?.optString("interface") ?: "未知接口")
    } ?: emptyList()
    fun deleteCapture(id: String) {
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) throw FlutterError("INVALID_INPUT", "无效文件标识")
        val file = File(captures, "$id.pcap")
        if (file.exists() && !file.delete()) throw FlutterError("IO_ERROR", "无法删除抓包文件")
        File(captures, "$id.json").delete()
    }
    fun stopAll() { worker?.let { remote -> io.execute { runCatching { remote.stopAll() } } } }
    fun onForeground() { foreground = true; bindIfAllowed() }
    fun onBackground() { foreground = false; stopAll() }
    fun close() {
        closed = true
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permission)
        runCatching { worker?.stopAll() }
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        io.shutdown(); worker = null
    }
}
