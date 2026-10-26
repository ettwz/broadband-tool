package dev.broadband.toolbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

class OrdinaryTasks(private val context: Context, private val registry: NetworkRegistry) {
    val dns = DnsEngine()
    val io = NetworkIo()
    private val native by lazy { NativeBridge() }
    private val processStops = Executors.newSingleThreadScheduledExecutor()

    fun run(session: TaskSession) {
        val params = JSONObject(session.request.parametersJson)
        if (session.request.kind == "clearAppDns") {
            session.emit("result", JSONObject().put("cleared", dns.clear()).put("scope", "application"))
            return
        }
        val (network, info) = registry.requireNetwork(session.request.networkId, session.request.networkGeneration)
        session.emit("running", JSONObject().put("networkId", info.id).put("interface", info.interfaceName)
            .put("networkName", info.displayName).put("family", "IPv4"))
        when (session.request.kind) {
            "ping", "trace" -> probe(session, network, info, params)
            "tcping" -> tcping(session, network, params)
            "telnet" -> telnet(session, network, params)
            "dns" -> dns.run(session, network, info, params)
            "systemResolve" -> {
                val name = InputRules.host(params.optString("name"))
                val addresses = io.resolve4(name, network, session)
                session.emit("result", JSONObject().put("name", name)
                    .put("addresses", org.json.JSONArray(addresses.map { it.hostAddress }))
                    .put("mode", "system").put("networkId", info.id))
            }
            "publicIp" -> publicIp(session, network, info)
            "iperf" -> iperf(session, network, info, params)
            "webspeed" -> {
                ensureNoConfiguredProxy(network)
                val provider = InputRules.choice(params, "provider", "ustc", if (BuildConfig.DEBUG) setOf("ustc", "fast", "test") else setOf("ustc", "fast"))
                val url = when (provider) {
                    "ustc" -> "https://test.ustc.edu.cn/"
                    "fast" -> "https://fast.com/"
                    else -> {
                        val uri = java.net.URI(params.getString("url"))
                        if (uri.scheme !in setOf("http", "https") || uri.host != "127.0.0.1" || uri.port !in 1..65535) throw FlutterError("INVALID_INPUT", "测试网页仅允许回环地址")
                        uri.toString()
                    }
                }
                session.own(WebSpeedProxy(session, network, info, io, provider, url)).use { it.serve() }
            }
            else -> throw FlutterError("UNSUPPORTED", "不支持该工具")
        }
    }

    private fun probe(session: TaskSession, network: Network, info: NetworkData, params: JSONObject) {
        val trace = session.request.kind == "trace"
        val host = InputRules.host(params.optString("host"))
        if (trace && !host.all { it.isDigit() || it == '.' }) {
            session.emit("resolving", JSONObject().put("host", host).put("networkId", info.id))
        }
        val target = io.resolve4(host, network, session).first().hostAddress!!
        if (trace) session.emit("target", JSONObject().put("host", host).put("target", target).put("networkId", info.id))
        val count = if (trace) 30 else InputRules.integer(params, "count", 10, 1, 1000)
        val interval = if (trace) 0 else InputRules.integer(params, "intervalMs", 1000, 200, 10000)
        val timeout = if (trace) 1000 else InputRules.integer(params, "timeoutMs", 2000, 200, 10000)
        val payload = if (trace) 0 else InputRules.integer(params, "payloadBytes", 56, 8, 1400)
        val mutex = Any()
        var handle = 0L
        var nativeError: JSONObject? = null
        synchronized(mutex) { session.checkActive(); handle = native.createJob() }
        session.onCancel { synchronized(mutex) { if (handle != 0L) native.cancelJob(handle) } }
        try {
            val result = native.runProbe(handle, session.request.kind, target, network.networkHandle,
                count, interval, timeout, payload, NativeCallback { kind, json ->
                    val data = JSONObject(json).put("host", host).put("target", target).put("networkId", info.id)
                    if (kind == "error") nativeError = data
                    else session.emit(if (kind == "summary") "result" else kind, data)
                })
            session.checkActive()
            if (result != 0) {
                val number = nativeError?.optInt("errno") ?: -1
                val code = if (number == 1 || number == 13) "UNSUPPORTED" else "ENGINE_FAILED"
                throw FlutterError(code, if (code == "UNSUPPORTED") "当前系统不允许此类探测" else "探测失败，系统错误 $number", nativeError?.toString())
            }
        } finally {
            synchronized(mutex) { if (handle != 0L) { native.destroyJob(handle); handle = 0L } }
        }
    }

    private fun tcping(session: TaskSession, network: Network, params: JSONObject) {
        val target = io.resolve4(params.optString("host"), network, session).first()
        val port = InputRules.integer(params, "port", 443, 1, 65535)
        val count = InputRules.integer(params, "count", 10, 1, 1000)
        val timeout = InputRules.integer(params, "timeoutMs", 2000, 200, 10000)
        val interval = InputRules.integer(params, "intervalMs", 1000, 200, 10000)
        var success = 0; var refused = 0; var timedOut = 0
        val times = mutableListOf<Double>()
        for (number in 1..count) {
            session.checkActive()
            val started = System.nanoTime()
            val data = JSONObject().put("sequence", number).put("address", target.hostAddress).put("port", port)
            try {
                val socket = session.own(network.socketFactory.createSocket())
                socket.use {
                    it.connect(InetSocketAddress(target, port), timeout)
                    val duration = (System.nanoTime() - started) / 1e6
                    success++; times += duration
                    data.put("status", "connected").put("rttMs", duration).put("sourceIPv4", it.localAddress.hostAddress)
                }
            } catch (error: SocketTimeoutException) {
                timedOut++; data.put("status", "timeout").put("rttMs", JSONObject.NULL)
            } catch (error: ConnectException) {
                if (error.message?.contains("ECONNREFUSED") == true || error.message?.contains("refused", true) == true) {
                    refused++; data.put("status", "refused").put("rttMs", JSONObject.NULL)
                } else throw error
            }
            session.checkActive()
            session.emit("sample", data)
            if (number != count) session.delay((interval - (System.nanoTime() - started) / 1_000_000).coerceAtLeast(0))
        }
        session.emit("result", JSONObject().put("sent", count).put("connected", success).put("refused", refused)
            .put("timeouts", timedOut).put("minMs", times.minOrNull() ?: JSONObject.NULL)
            .put("avgMs", if (times.isEmpty()) JSONObject.NULL else times.average())
            .put("maxMs", times.maxOrNull() ?: JSONObject.NULL))
    }

    private fun telnet(session: TaskSession, network: Network, params: JSONObject) {
        val target = io.resolve4(params.optString("host"), network, session).first()
        val port = InputRules.integer(params, "port", 23, 1, 65535)
        val timeout = InputRules.integer(params, "timeoutMs", 5000, 200, 10000)
        val socket = session.own(network.socketFactory.createSocket())
        socket.use {
            it.connect(InetSocketAddress(target, port), timeout)
            it.soTimeout = 500
            val writeLock = Any()
            val output = it.getOutputStream()
            val codec = TelnetCodec({ bytes -> synchronized(writeLock) { output.write(bytes); output.flush() } },
                { local -> session.emit("terminalState", JSONObject().put("localEcho", local)) })
            session.terminalWriter = { bytes -> synchronized(writeLock) { output.write(codec.encodeInput(bytes)); output.flush() } }
            session.terminalResize = { columns, rows -> synchronized(writeLock) { codec.resize(columns, rows) } }
            session.emit("connected", JSONObject().put("address", target.hostAddress).put("port", port)
                .put("sourceIPv4", it.localAddress.hostAddress).put("localEcho", false))
            val buffer = ByteArray(8192)
            while (!session.cancelled.get()) {
                val size = try { it.getInputStream().read(buffer) } catch (_: SocketTimeoutException) { continue }
                if (size < 0) break
                val text = synchronized(writeLock) { codec.decode(buffer, size) }
                if (text.isNotEmpty()) session.emit("terminal", JSONObject().put("bytes", Base64.encodeToString(text, Base64.NO_WRAP)))
            }
        }
        session.checkActive()
        session.emit("result", JSONObject().put("message", "对端已关闭连接"))
    }

    private fun publicIp(session: TaskSession, network: Network, info: NetworkData) {
        ensureNoConfiguredProxy(network)
        val client = OkHttpClient.Builder().socketFactory(network.socketFactory)
            .dns(Dns { host -> io.resolve4(host, network, session) }).proxy(Proxy.NO_PROXY)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        val call = client.newCall(Request.Builder().url("https://api-ipv4.ip.sb/ip")
            .header("User-Agent", "BroadbandTool/0.1 Android").header("Accept", "text/plain").build())
        session.onCancel { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw FlutterError("PROVIDER_BLOCKED", "公网 IP 服务返回 ${response.code}")
                val body = response.body ?: throw FlutterError("INVALID_RESPONSE", "公网 IP 服务未返回内容")
                val stream = body.byteStream()
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(512)
                while (buffer.size() <= 4096) {
                    val size = stream.read(chunk, 0, minOf(chunk.size, 4097 - buffer.size()))
                    if (size < 0) break
                    buffer.write(chunk, 0, size)
                }
                val bytes = buffer.toByteArray()
                if (bytes.size > 4096) throw FlutterError("INVALID_RESPONSE", "公网 IP 响应过大")
                val value = String(bytes, Charsets.UTF_8).trim()
                val address = try { InputRules.ipv4(value) } catch (_: Exception) {
                    throw FlutterError("INVALID_RESPONSE", "服务未返回有效的纯文本 IPv4")
                }
                session.checkActive()
                session.emit("result", JSONObject().put("ip", address.hostAddress).put("provider", "IP.SB")
                    .put("networkId", info.id).put("networkName", info.displayName).put("timestamp", System.currentTimeMillis()))
            }
        } finally {
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    fun ensureNoConfiguredProxy(network: Network) {
        val proxy = context.getSystemService(ConnectivityManager::class.java).getLinkProperties(network)?.httpProxy
        if (proxy != null && (!proxy.host.isNullOrBlank() || proxy.pacFileUrl != Uri.EMPTY)) {
            throw FlutterError("PROXY_ROUTE_UNVERIFIED", "当前网络配置了系统代理，暂不绕过该代理执行公网测试")
        }
    }

    private fun iperf(session: TaskSession, network: Network, info: NetworkData, params: JSONObject) {
        val role = InputRules.choice(params, "role", "client", setOf("client", "server"))
        val port = InputRules.integer(params, "port", 5201, if (role == "server") 1024 else 1, 65535)
        val arguments = mutableListOf(File(context.applicationInfo.nativeLibraryDir, "libiperf_exec.so").absolutePath,
            "-4", "-p", "$port", "--json-stream", "--forceflush", "-B", info.addresses.first())
        if (role == "server") arguments += listOf("-s", "-1", "--idle-timeout", "300", "--server-max-duration", "120")
        else {
            val target = io.resolve4(params.optString("host"), network, session).first().hostAddress!!
            val seconds = InputRules.integer(params, "duration", 10, 5, 120)
            val protocol = InputRules.choice(params, "protocol", "tcp", setOf("tcp", "udp"))
            val streams = InputRules.integer(params, "streams", 1, 1, if (protocol == "udp") 1 else 8)
            arguments += listOf("-c", target, "-t", "$seconds", "-P", "$streams", "--connect-timeout", "5000")
            if (params.optBoolean("reverse", false)) arguments += "-R"
            if (protocol == "udp") {
                val rate = InputRules.integer(params, "rateMbps", 10, 1, 1000)
                arguments += listOf("-u", "-b", "${rate}M")
            }
        }
        val builder = ProcessBuilder(arguments).redirectErrorStream(true)
        builder.environment()["BROADBAND_NETWORK_HANDLE"] = network.networkHandle.toString()
        builder.environment()["TMPDIR"] = context.cacheDir.absolutePath
        session.checkActive()
        val process = builder.start()
        session.onCancel {
            process.destroy()
            processStops.schedule({ if (process.isAlive) process.destroyForcibly() }, 700, TimeUnit.MILLISECONDS)
        }
        session.emit("progress", JSONObject().put("message", if (role == "server") "正在启动服务端" else "正在连接 iperf3 服务端"))
        var error: String? = null
        var endSeen = false
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    session.checkActive()
                    if (line.startsWith('{')) {
                        val event = runCatching { JSONObject(line) }.getOrNull()
                        if (event != null) {
                            when (event.optString("event")) {
                                "error" -> error = event.opt("data")?.toString()
                                "listening" -> session.emit("listening", JSONObject().put("address", info.addresses.first()).put("port", port))
                                "start" -> { session.emit("running"); session.emit("iperf", event) }
                                "end" -> { endSeen = true; session.emit("result", JSONObject().put("iperf", event.opt("data")).put("role", role)) }
                                else -> session.emit("iperf", event)
                            }
                        }
                    } else if (line.contains("error", true)) error = line.take(300)
                }
            }
            val exit = process.waitFor()
            session.checkActive()
            if (exit != 0 || error != null) throw FlutterError(
                if (error?.contains("in use", true) == true) "PORT_IN_USE" else "ENGINE_FAILED",
                error ?: "iperf3 退出，状态 $exit")
            if (!endSeen) session.emit("result", JSONObject().put("message", if (role == "server") "等待超时，服务端已停止" else "测试已结束"))
        } finally {
            if (process.isAlive) { process.destroy(); if (!process.waitFor(700, TimeUnit.MILLISECONDS)) process.destroyForcibly() }
        }
    }

    fun close() { io.close(); processStops.shutdown() }
}
