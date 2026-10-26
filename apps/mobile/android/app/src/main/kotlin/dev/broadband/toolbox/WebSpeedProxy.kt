package dev.broadband.toolbox

import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A loopback proxy with no DIRECT fallback. TLS stays end-to-end in WebView. */
class WebSpeedProxy(
    private val session: TaskSession,
    private val network: Network,
    private val info: NetworkData,
    private val resolver: NetworkIo,
    private val provider: String,
    private val targetUrl: String,
) : Closeable {
    private val stopped = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = Executors.newCachedThreadPool()
    private val slots = Semaphore(16)
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val test = BuildConfig.DEBUG && provider == "test"

    fun serve() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            close(); throw FlutterError("UNSUPPORTED", "当前系统 WebView 无法绑定测速出口，请更新 WebView")
        }
        session.onCancel { close() }
        workers.execute {
            while (!stopped.get()) {
                val client = try { server.accept() } catch (_: Exception) { break }
                if (!client.inetAddress.isLoopbackAddress || !slots.tryAcquire()) { client.close(); continue }
                clients += client
                workers.execute {
                    try { handle(client) }
                    catch (_: Exception) { /* The page receives its connection error, no header/payload logging. */ }
                    finally { clients -= client; runCatching { client.close() }; slots.release() }
                }
            }
        }
        val ready = CompletableFuture<Unit>()
        Handler(Looper.getMainLooper()).post {
            try {
                val config = ProxyConfig.Builder().addProxyRule("http://127.0.0.1:${server.localPort}")
                    .removeImplicitRules().build()
                ProxyController.getInstance().setProxyOverride(config, { it.run() }) { ready.complete(Unit) }
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }
        ready.get(5, TimeUnit.SECONDS)
        session.checkActive()
        session.emit("webReady", JSONObject().put("url", targetUrl).put("provider", provider)
            .put("networkName", info.displayName).put("networkId", info.id).put("mode", "bound_ipv4_proxy"))
        session.delay(180000)
        session.emit("result", JSONObject().put("provider", provider).put("message", "网页会话已达到 3 分钟上限"))
    }

    private fun allowedHost(host: String): Boolean {
        if (test) return host == "127.0.0.1" || host == "localhost" || host == "api-ipv4.ip.sb"
        val domains = if (provider == "ustc") listOf("test.ustc.edu.cn") else
            listOf("fast.com", "netflix.com", "nflxvideo.net", "nflximg.net", "nflxext.com", "nflxso.net")
        return domains.any { host == it || host.endsWith(".$it") }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 5000
        val input = client.getInputStream()
        val first = readLine(input, 4096) ?: return
        val pieces = first.split(' ')
        if (pieces.size != 3) return reject(client, 400)
        val tunnel = pieces[0] == "CONNECT"
        val destination = if (tunnel) URI("https://${pieces[1]}") else runCatching { URI(pieces[1]) }.getOrNull()
        val host = destination?.host?.lowercase() ?: return reject(client, 400)
        val port = if (destination.port > 0) destination.port else if (tunnel) 443 else 80
        if (!allowedHost(host) || port !in 1..65535 || (!test && (!tunnel || port != 443))) return reject(client, 403)
        val headers = mutableListOf<String>()
        var length = first.length
        while (true) {
            val line = readLine(input, 8192) ?: return
            length += line.length
            if (length > 16384) return reject(client, 431)
            if (line.isEmpty()) break
            headers += line
        }
        session.checkActive()
        val addresses = resolver.resolve4(host, network, session).filterIsInstance<Inet4Address>()
        val address = addresses.firstOrNull { test || (!it.isLoopbackAddress && !it.isSiteLocalAddress && !it.isLinkLocalAddress && !it.isAnyLocalAddress) }
            ?: return reject(client, 403)
        val remote = network.socketFactory.createSocket()
        clients += remote
        try {
            remote.connect(InetSocketAddress(address, port), 5000)
            remote.soTimeout = 1000; client.soTimeout = 1000
            session.emit("webConnection", JSONObject().put("host", host).put("destinationIPv4", address.hostAddress)
                .put("sourceIPv4", remote.localAddress.hostAddress).put("networkId", info.id))
            if (tunnel) {
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
            } else {
                val path = destination.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/"
                val query = destination.rawQuery?.let { "?$it" } ?: ""
                val forwarded = buildString {
                    append("${pieces[0]} $path$query HTTP/1.1\r\n")
                    for (header in headers) if (!header.startsWith("Proxy-", true) && !header.startsWith("Connection:", true)) append("$header\r\n")
                    append("Connection: close\r\n\r\n")
                }
                remote.getOutputStream().write(forwarded.toByteArray(Charsets.ISO_8859_1))
            }
            val response = workers.submit {
                try { relay(remote, client) }
                finally { runCatching { remote.close() }; runCatching { client.close() } }
            }
            try { relay(client, remote) } finally {
                runCatching { remote.shutdownOutput() }
                runCatching { response.get(2, TimeUnit.SECONDS) }
            }
        } finally {
            clients -= remote; runCatching { remote.close() }
        }
    }

    private fun relay(source: Socket, destination: Socket) {
        val buffer = ByteArray(65536)
        val input = source.getInputStream(); val output = destination.getOutputStream()
        while (!stopped.get() && !session.cancelled.get()) {
            val size = try { input.read(buffer) } catch (_: SocketTimeoutException) { continue }
            if (size < 0) return
            output.write(buffer, 0, size)
        }
    }

    private fun reject(socket: Socket, status: Int) {
        socket.getOutputStream().write("HTTP/1.1 $status Rejected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
    }

    private fun readLine(input: InputStream, maximum: Int): String? {
        val output = java.io.ByteArrayOutputStream()
        while (output.size() < maximum) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == 10) return output.toString("ISO-8859-1").removeSuffix("\r")
            output.write(byte)
        }
        throw java.io.IOException("Proxy header too long")
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { server.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear(); workers.shutdownNow()
        // Keep the now-dead proxy configured until the view is disposed/replaced.
        // Clearing the override while WebView lives could silently resume DIRECT traffic.
    }
}
