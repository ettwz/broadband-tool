package dev.broadband.toolbox

import android.net.DnsResolver
import android.net.Network
import android.os.Build
import android.os.CancellationSignal
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class NetworkIo {
    private val resolverPool = Executors.newFixedThreadPool(2)

    fun resolve4(host: String, network: Network, session: TaskSession): List<InetAddress> {
        val clean = InputRules.host(host)
        session.checkActive()
        if (clean.all { it.isDigit() || it == '.' }) return listOf(InputRules.ipv4(clean))
        val result = CompletableFuture<List<InetAddress>>()
        if (Build.VERSION.SDK_INT >= 29) {
            val signal = CancellationSignal()
            result.whenComplete { _, _ -> if (result.isCancelled) signal.cancel() }
            session.onCancel { signal.cancel(); result.cancel(true) }
            DnsResolver.getInstance().query(network, clean, DnsResolver.TYPE_A, DnsResolver.FLAG_EMPTY,
                resolverPool, signal, object : DnsResolver.Callback<List<InetAddress>> {
                    override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                        if (rcode != 0) result.completeExceptionally(FlutterError("DNS_FAILED",
                            if (rcode == 3) "当前网络的 DNS 未找到该域名" else "当前网络的 DNS 解析失败（返回码 $rcode）"))
                        else result.complete(answer.filterIsInstance<Inet4Address>())
                    }
                    override fun onError(error: DnsResolver.DnsException) {
                        result.completeExceptionally(FlutterError("DNS_FAILED", "当前网络无法解析目标域名"))
                    }
                })
        } else {
            // Android 9 has no cancellable public DnsResolver API. Bound resolver
            // work is limited to two threads; a cancelled task discards the result.
            val work = resolverPool.submit {
                try { result.complete(network.getAllByName(clean).filterIsInstance<Inet4Address>()) }
                catch (_: UnknownHostException) { result.completeExceptionally(FlutterError("DNS_FAILED", "当前网络无法解析目标域名")) }
                catch (error: Exception) { result.completeExceptionally(error) }
            }
            session.onCancel { work.cancel(true); result.cancel(true) }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            session.checkActive()
            try {
                val addresses = result.get(100, TimeUnit.MILLISECONDS).filter {
                    runCatching { InputRules.ipv4(it.hostAddress ?: "") }.isSuccess
                }
                if (addresses.isEmpty()) throw FlutterError("NO_IPV4", "目标域名没有可用的 IPv4 地址")
                return addresses
            } catch (_: TimeoutException) { /* Check cancellation between waits. */ }
        }
        result.cancel(true)
        throw FlutterError("DNS_FAILED", "当前网络域名解析超时")
    }

    fun close() { resolverPool.shutdownNow() }
}
