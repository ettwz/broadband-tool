package dev.broadband.toolbox

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class PlatformController(private val activity: Activity, private val flutter: NetworkFlutterApi) : NetworkHostApi {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val workers = Executors.newFixedThreadPool(3)
    private val tasks = ConcurrentHashMap<String, TaskSession>()
    private var disposed = false
    private var permissionCallback: ((Result<PermissionData>) -> Unit)? = null
    private var exportPending: Pair<java.io.File, (Result<String>) -> Unit>? = null
    private val registry = NetworkRegistry(activity, { main.post { publishSnapshot() } }, { network, reason ->
        tasks.values.filter { !it.finished && it.request.networkId == network }.forEach { it.cancel(reason) }
    })
    private val advanced = AdvancedAccess(activity) { main.post { publishSnapshot() } }
    private val ordinary = OrdinaryTasks(activity, registry)

    override fun getAppVersion(): String = BuildConfig.VERSION_NAME

    private fun snapshot() = PlatformSnapshot(
        "android", Build.VERSION.SDK_INT.toLong(), registry.data(), registry.permissions(),
        registry.accessPoints, registry.scanStatus, registry.scanUpdatedAt, advanced.snapshot(), registry.selectedId,
    )

    private fun publishSnapshot() {
        if (disposed) return
        val data = snapshot()
        scope.launch { runCatching { flutter.onSnapshot(data) } }
    }

    override fun getSnapshot(callback: (Result<PlatformSnapshot>) -> Unit) {
        registry.refresh()
        callback(runCatching { snapshot() })
    }

    override fun selectNetwork(networkId: String) {
        tasks.values.filter { !it.finished }.forEach { it.cancel("NETWORK_CHANGED") }
        advanced.stopAll()
        registry.select(networkId)
    }

    override fun requestNetworkPermissions(callback: (Result<PermissionData>) -> Unit) {
        if (registry.permissions().fineLocation) { callback(Result.success(registry.permissions())); return }
        if (permissionCallback != null) { callback(Result.failure(FlutterError("BUSY", "正在请求权限"))); return }
        permissionCallback = callback
        activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7430)
    }

    fun onPermissionsResult(requestCode: Int) {
        if (requestCode != 7430) return
        permissionCallback?.invoke(Result.success(registry.permissions()))
        permissionCallback = null
        registry.refresh()
    }

    override fun requestWifiScan() {
        if (tasks.values.any { !it.finished && it.request.kind != "root.capture" }) throw FlutterError("BUSY", "诊断运行期间暂停主动 Wi-Fi 扫描")
        registry.requestScan()
    }

    @Synchronized override fun startTask(request: TaskRequestData): String {
        if (disposed) throw FlutterError("ENGINE_FAILED", "应用正在退出")
        if (!request.requestId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) throw FlutterError("INVALID_INPUT", "无效的请求标识")
        tasks[request.requestId]?.let { return it.id }
        if (request.parametersJson.length > 16384) throw FlutterError("INVALID_INPUT", "参数过长")
        val parameters = runCatching { JSONObject(request.parametersJson) }.getOrElse { throw FlutterError("INVALID_INPUT", "参数格式不正确") }
        TaskValidation.validate(request.kind, parameters)
        val privileged = request.kind.startsWith("root.")
        if (advanced.configBusy() && request.kind !in setOf("root.ethernetConfirm", "root.ethernetRestore")) throw FlutterError("CONFIG_CONFLICT", "请先确认或恢复临时有线配置")
        if (privileged) advanced.validate(request)
        if (!privileged && request.kind !in setOf("ping", "trace", "tcping", "telnet", "dns", "systemResolve", "publicIp", "iperf", "clearAppDns", "webspeed")) {
            throw FlutterError("UNSUPPORTED", "该功能尚未连接到当前平台")
        }
        if (request.kind == "root.capture") {
            if (tasks.values.any { !it.finished && it.request.kind == "root.capture" }) throw FlutterError("BUSY", "已有抓包正在运行")
        } else if (tasks.values.any { !it.finished && it.request.kind != "root.capture" }) throw FlutterError("BUSY", "请先停止当前任务")
        var effectiveRequest = request
        if ((!privileged && request.kind != "clearAppDns") || request.kind == "root.systemDns") {
            val (network, _) = registry.requireNetwork(request.networkId, request.networkGeneration)
            if (request.kind == "root.systemDns") {
                effectiveRequest = request.copy(parametersJson = JSONObject(request.parametersJson).put("netId", network.toString().toInt()).toString())
            }
        }
        val session = TaskSession(effectiveRequest) { event ->
            main.post {
                if (!disposed) {
                    scope.launch { runCatching { flutter.onTaskEvent(event) } }
                    if (event.state in setOf("completed", "cancelled", "failed", "interrupted")) updateAwake()
                }
            }
        }
        tasks[session.id] = session
        updateAwake()
        workers.execute {
            session.thread = Thread.currentThread()
            try {
                session.checkActive()
                if (privileged) advanced.run(session) else ordinary.run(session)
                session.checkActive()
                session.finish("completed")
            } catch (error: Exception) {
                if (session.cancelled.get() || error is InterruptedException) {
                    session.finish(if (session.cancellationReason == "CANCELLED") "cancelled" else "interrupted",
                        code = session.cancellationReason, message = when (session.cancellationReason) {
                            "NETWORK_CHANGED" -> "网络配置已改变，任务已停止"
                            "NETWORK_LOST" -> "所选网络已断开，未切换到其他网络"
                            "APP_BACKGROUND" -> "应用进入后台，任务已停止"
                            else -> "任务已停止"
                        })
                } else {
                    val actual = if (error is java.util.concurrent.ExecutionException) error.cause ?: error else error
                    val code = when (actual) {
                        is FlutterError -> actual.code
                        is SocketTimeoutException -> "TIMEOUT"
                        is SecurityException -> "PERMISSION_DENIED"
                        else -> "ENGINE_FAILED"
                    }
                    session.finish("failed", code = code, message = actual.message?.take(400) ?: "网络操作失败")
                }
            } finally {
                session.thread = null
                Thread.interrupted()
                if (tasks.size > 128) tasks.entries.filter { it.value.finished }.take(tasks.size - 100).forEach { tasks.remove(it.key) }
            }
        }
        return session.id
    }

    override fun stopTask(taskId: String) { tasks[taskId]?.cancel() }
    override fun sendTerminalInput(taskId: String, bytes: ByteArray) {
        if (bytes.size > 65536) throw FlutterError("INVALID_INPUT", "单次输入过长")
        val session = tasks[taskId] ?: throw FlutterError("NETWORK_LOST", "会话已结束")
        val writer = session.terminalWriter ?: throw FlutterError("NETWORK_LOST", "终端尚未连接")
        workers.execute { runCatching { writer(bytes) }.onFailure { session.cancel("NETWORK_LOST") } }
    }
    override fun resizeTerminal(taskId: String, columns: Long, rows: Long) {
        tasks[taskId]?.terminalResize?.invoke(columns.toInt().coerceIn(1, 1000), rows.toInt().coerceIn(1, 1000))
    }
    override fun requestAdvancedAccess(callback: (Result<PrivilegeData>) -> Unit) = advanced.request(callback)

    override fun openSystemSettings(section: String) {
        val intent = when (section) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "shizuku" -> activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            "app" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            else -> throw FlutterError("INVALID_INPUT", "不支持的设置入口")
        }
        activity.startActivity(intent)
    }
    override fun exportCapture(captureId: String, callback: (Result<String>) -> Unit) {
        if (exportPending != null) { callback(Result.failure(FlutterError("BUSY", "已有文件正在导出"))); return }
        val file = runCatching { advanced.captureFile(captureId) }.getOrElse { callback(Result.failure(it)); return }
        exportPending = file to callback
        activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.tcpdump.pcap"
            putExtra(Intent.EXTRA_TITLE, "broadband-$captureId.pcap")
        }, 7432)
    }
    override fun listCaptures(): List<CaptureFileData> = advanced.listCaptures()
    override fun deleteCapture(captureId: String) {
        if (tasks[captureId]?.finished == false) throw FlutterError("BUSY", "请先停止此抓包")
        advanced.deleteCapture(captureId)
    }
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 7432) return
        val pending = exportPending ?: return
        exportPending = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) { pending.second(Result.failure(FlutterError("CANCELLED", "已取消导出"))); return }
        workers.execute {
            val result = runCatching {
                activity.contentResolver.openOutputStream(uri, "wt")?.use { output -> pending.first.inputStream().use { it.copyTo(output) } }
                    ?: throw java.io.IOException("无法写入所选位置")
                "抓包文件已导出"
            }
            main.post { pending.second(result) }
        }
    }
    override fun getSupportDirectory(callback: (Result<String>) -> Unit) {
        callback(Result.success(activity.filesDir.absolutePath))
    }

    fun onForeground() { registry.refresh(); advanced.onForeground() }
    fun onBackground() { tasks.values.filter { !it.finished }.forEach { it.cancel("APP_BACKGROUND") }; advanced.onBackground() }
    private fun updateAwake() {
        if (disposed) return
        if (tasks.values.any { !it.finished }) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun close() {
        onBackground(); activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); disposed = true
        registry.dispose(); advanced.close(); ordinary.close(); workers.shutdown(); scope.cancel()
    }
}
