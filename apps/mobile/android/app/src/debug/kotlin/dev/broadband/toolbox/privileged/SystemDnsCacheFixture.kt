package dev.broadband.toolbox.privileged

import android.net.DnsResolver
import android.net.Network
import android.os.CancellationSignal
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import dalvik.system.PathClassLoader
import dev.broadband.toolbox.dnsapi.ResolverParamsParcel
import dev.broadband.toolbox.FlutterError
import dev.broadband.toolbox.NetworkIo
import dev.broadband.toolbox.TaskRequestData
import dev.broadband.toolbox.TaskSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Debug-only app_process fixture. It never changes an existing network or DNS configuration. */
object SystemDnsCacheFixture {
    @JvmStatic fun main(args: Array<String>) {
        check(Process.myUid() == 0) { "This device fixture requires root" }
        Looper.prepareMainLooper()
        thread(name = "dns-fixture-deadline", isDaemon = true) {
            Thread.sleep(30000)
            System.err.println("DNS_FIXTURE_FAILED: exceeded 30 second deadline")
            exitProcess(2)
        }
        thread(name = "dns-cache-fixture") {
            try {
                println(run().toString())
                exitProcess(0)
            } catch (error: Throwable) {
                val cause = generateSequence(error) { it.cause }.last()
                System.err.println("DNS_FIXTURE_FAILED: ${cause.javaClass.simpleName}: ${cause.message}")
                exitProcess(1)
            }
        }
        Looper.loop()
    }

    private fun run(): JSONObject {
        val adapter = SystemDnsAdapter()
        check(adapter.available) { adapter.reason }
        val resolver = adapter.resolver!!
        val loader = PathClassLoader("/system/framework/services.jar", javaClass.classLoader)
        val netdType = Class.forName("android.net.INetd", true, loader)
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "netd") as IBinder
        val netd = Class.forName("android.net.INetd\$Stub", true, loader)
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val create = netdType.methods.first { it.name == "networkCreate" && it.parameterCount == 1 }
        val destroy = netdType.getMethod("networkDestroy", Int::class.javaPrimitiveType)
        val ownedNetworks = mutableListOf<Int>()
        val ownedCaches = mutableListOf<Int>()
        val closed = AtomicBoolean(false)
        val name = "cache-${SystemClock.elapsedRealtime()}.broadband.example"
        val first = Server("127.0.0.31", name, 11)
        val second = Server("127.0.0.32", name, 21)
        fun clean() {
            if (!closed.compareAndSet(false, true)) return
            first.close(); second.close()
            var failure: Throwable? = null
            for (id in ownedCaches.reversed()) {
                try { resolver.destroyNetworkCache(id) } catch (error: Throwable) { failure = error }
            }
            for (id in ownedNetworks.reversed()) {
                try { destroy.invoke(netd, id) } catch (error: Throwable) { failure = error }
            }
            check(failure == null) { "Fixture cleanup failed: ${failure?.message}" }
        }
        val hook = Thread { runCatching { clean() }.onFailure { System.err.println(it.message) } }
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            // Like AOSP's resolver tests: physical network objects without an
            // interface, and loopback responders. Never destroy a pre-existing ID.
            for (id in 60100..60150) {
                val builderType = create.parameterTypes[0].declaredClasses.first { it.simpleName == "Builder" }
                val builder = builderType.getDeclaredConstructor().newInstance()
                builderType.getMethod("setNetId", Int::class.javaPrimitiveType).invoke(builder, id)
                builderType.getMethod("setNetworkType", Int::class.javaPrimitiveType).invoke(builder, 0) // PHYSICAL
                builderType.getMethod("setPermission", Int::class.javaPrimitiveType).invoke(builder, 0) // NONE
                val config = builderType.getMethod("build").invoke(builder)
                try { create.invoke(netd, config) }
                catch (error: java.lang.reflect.InvocationTargetException) {
                    val errno = runCatching { error.cause?.javaClass?.getField("errorCode")?.getInt(error.cause) }.getOrNull()
                    if (errno == 17) continue
                    throw error
                }
                ownedNetworks += id
                resolver.createNetworkCache(id)
                ownedCaches += id
                if (ownedNetworks.size == 2) break
            }
            check(ownedNetworks.size == 2) { "No free test network identifiers" }
            for ((index, id) in ownedNetworks.withIndex()) {
                resolver.setResolverConfiguration(ResolverParamsParcel().apply {
                    netId = id
                    sampleValiditySeconds = 300
                    successThreshold = 25
                    minSamples = 2
                    maxSamples = 4
                    baseTimeoutMsec = 1000
                    retryCount = 1
                    servers = arrayOf(if (index == 0) first.address else second.address)
                    domains = emptyArray()
                    tlsName = ""
                    tlsServers = emptyArray()
                    transportTypes = intArrayOf(7) // TEST
                })
            }
            val constructor = Network::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            val a = constructor.newInstance(ownedNetworks[0])
            val b = constructor.newInstance(ownedNetworks[1])
            check(query(a, name) == "192.0.2.11")
            check(query(b, name) == "192.0.2.21")
            val firstCount = first.queries.get()
            val secondCount = second.queries.get()
            check(firstCount > 0 && secondCount > 0)
            first.answer.set(12); second.answer.set(22)
            check(query(a, name) == "192.0.2.11")
            check(query(b, name) == "192.0.2.21")
            check(first.queries.get() == firstCount && second.queries.get() == secondCount) { "System cache did not serve the repeated queries" }
            val cleared = adapter.flush(ownedNetworks[0])
            check(query(a, name) == "192.0.2.12") { "Selected network retained the old cached answer" }
            check(first.queries.get() == firstCount + 1) { "Query did not reach the controlled DNS responder after flush" }
            check(query(b, name) == "192.0.2.21")
            check(second.queries.get() == secondCount) { "Other network cache was cleared" }
            val invalid = runCatching { adapter.flush(0) }.exceptionOrNull() as? RootFailure
            check(invalid?.code == "INVALID_INPUT")
            val queriesAfterFlush = first.queries.get()
            val io = NetworkIo()
            try {
                fun expectResolutionError(expected: String) {
                    adapter.flush(ownedNetworks[0])
                    val session = TaskSession(TaskRequestData("resolver-error", "trace", "{}")) { }
                    val error = runCatching { io.resolve4(name, a, session) }.exceptionOrNull()
                    val actual = if (error is java.util.concurrent.ExecutionException) error.cause else error
                    check((actual as? FlutterError)?.code == expected) { "Expected $expected, got $actual" }
                }
                first.responseCode.set(3)
                expectResolutionError("DNS_FAILED")
                first.responseCode.set(0)
                first.emptyAnswer.set(true)
                expectResolutionError("NO_IPV4")
            } finally { io.close() }
            return JSONObject().put("passed", true).put("uid", Process.myUid())
                .put("resolverVersion", adapter.resolverVersion).put("networks", JSONArray(ownedNetworks))
                .put("cacheHitBeforeFlush", true).put("newAnswerAfterFlush", "192.0.2.12")
                .put("otherNetworkStillCached", true).put("invalidNetworkRejected", true)
                .put("firstQueries", queriesAfterFlush).put("secondQueries", second.queries.get())
                .put("resolverFailureCodes", JSONArray(listOf("DNS_FAILED", "NO_IPV4")))
                .put("flush", cleared)
        } finally {
            clean()
            Runtime.getRuntime().removeShutdownHook(hook)
        }
    }

    /** FLAG_EMPTY checks/stores the system cache; no Java InetAddress cache is used. */
    private fun query(network: Network, name: String): String {
        val latch = CountDownLatch(1)
        val signal = CancellationSignal()
        var answer: String? = null
        var failure: Throwable? = null
        DnsResolver.getInstance().query(network, name, DnsResolver.TYPE_A, DnsResolver.FLAG_EMPTY,
            { it.run() }, signal, object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(addresses: List<InetAddress>, rcode: Int) {
                    if (rcode == 0) answer = addresses.firstOrNull()?.hostAddress
                    latch.countDown()
                }
                override fun onError(error: DnsResolver.DnsException) { failure = error; latch.countDown() }
            })
        if (!latch.await(5, TimeUnit.SECONDS)) { signal.cancel(); error("System DNS query timed out") }
        return answer ?: error("System DNS query failed: ${failure?.message}")
    }

    private class Server(val address: String, private val hostname: String, initial: Int) {
        val queries = AtomicInteger()
        val answer = AtomicInteger(initial)
        val responseCode = AtomicInteger(0)
        val emptyAnswer = AtomicBoolean(false)
        private val socket = DatagramSocket(null).apply { bind(InetSocketAddress(address, 53)) }
        init {
            thread(name = "dns-fixture-$address", isDaemon = true) {
                val bytes = ByteArray(1500)
                while (!socket.isClosed) {
                    try {
                        val request = DatagramPacket(bytes, bytes.size)
                        socket.receive(request)
                        var offset = 12
                        val labels = mutableListOf<String>()
                        while (offset < request.length && bytes[offset].toInt() != 0) {
                            val size = bytes[offset++].toInt() and 255
                            require(size in 1..63 && offset + size < request.length)
                            labels += String(bytes, offset, size, Charsets.US_ASCII)
                            offset += size
                        }
                        offset++
                        require(offset + 4 <= request.length)
                        val type = (bytes[offset].toInt() and 255) * 256 + (bytes[offset + 1].toInt() and 255)
                        if (!labels.joinToString(".").equals(hostname, true) || type != 1) continue
                        offset += 4
                        queries.incrementAndGet()
                        val rcode = responseCode.get()
                        val hasAnswer = rcode == 0 && !emptyAnswer.get()
                        val buffer = ByteArrayOutputStream()
                        DataOutputStream(buffer).use { out ->
                            out.write(bytes, 0, 2); out.writeShort(0x8180 or rcode)
                            out.writeShort(1); out.writeShort(if (hasAnswer) 1 else 0); out.writeShort(0); out.writeShort(0)
                            out.write(bytes, 12, offset - 12)
                            if (hasAnswer) {
                                out.writeShort(0xc00c); out.writeShort(1); out.writeShort(1)
                                out.writeInt(120); out.writeShort(4)
                                out.write(byteArrayOf(192.toByte(), 0, 2, answer.get().toByte()))
                            }
                        }
                        val response = buffer.toByteArray()
                        socket.send(DatagramPacket(response, response.size, request.socketAddress))
                    } catch (error: Exception) {
                        if (!socket.isClosed) System.err.println("DNS fixture packet error: ${error.javaClass.simpleName}")
                    }
                }
            }
        }
        fun close() { socket.close() }
    }
}
