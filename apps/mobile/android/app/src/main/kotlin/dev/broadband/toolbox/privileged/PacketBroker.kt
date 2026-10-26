package dev.broadband.toolbox.privileged

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** One AF_PACKET owner; DHCP observers do not alter capture-file filters. */
class PacketBroker : AutoCloseable {
    interface Listener { fun packet(frame: Packets.Frame); fun failure(error: Exception) }
    private class Stream(val ref: RootInterface, val fd: Int) {
        val running = AtomicBoolean(true)
        val listeners = CopyOnWriteArrayList<Listener>()
        var received = 0L; var dropped = 0L
        var statsKnown = true
        lateinit var thread: Thread
    }
    private val native = PacketNative()
    private var current: Stream? = null
    inner class Lease internal constructor(private val refId: String, private val listener: Listener) : AutoCloseable {
        @Synchronized override fun close() {
            val stream = synchronized(this@PacketBroker) {
                current?.takeIf { it.ref.id == refId }?.also {
                    it.listeners.remove(listener)
                    if (it.listeners.isEmpty()) it.running.set(false)
                }
            }
            if (stream != null && !stream.running.get() && Thread.currentThread() != stream.thread) stream.thread.join(800)
        }
        fun send(bytes: ByteArray) = synchronized(this@PacketBroker) {
            val stream = current?.takeIf { it.ref.id == refId && it.running.get() }
                ?: throw RootFailure("INTERFACE_CHANGED", "捕获接口已停止")
            RootInterfaces.require(refId)
            native.send(stream.fd, stream.ref.index, bytes)
        }
        fun stats(): Pair<Long, Long?> = synchronized(this@PacketBroker) {
            val stream = current?.takeIf { it.ref.id == refId } ?: return@synchronized 0L to null
            collectStats(stream)
            stream.received to if (stream.statsKnown) stream.dropped else null
        }
    }
    @Synchronized fun acquire(ref: RootInterface, listener: Listener): Lease {
        if (!ref.ethernet) throw RootFailure("CAPABILITY_DENIED", "当前链路类型暂不支持原始报文")
        current?.let {
            if (it.ref.id != ref.id || !it.running.get()) throw RootFailure("BUSY", "另一个接口正在捕获报文")
            it.listeners.add(listener)
            return Lease(ref.id, listener)
        }
        val stream = Stream(ref, native.open(ref.index))
        stream.listeners.add(listener)
        current = stream
        stream.thread = Thread({
            try {
                var nextCheck = System.nanoTime()
                while (stream.running.get()) {
                    if (System.nanoTime() >= nextCheck) {
                        RootInterfaces.require(ref.id)
                        nextCheck = System.nanoTime() + 1_000_000_000L
                    }
                    val record = native.read(stream.fd) ?: continue
                    val stamp = ByteBuffer.wrap(record, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
                    val frame = Packets.Frame(record.copyOfRange(8, record.size), stamp)
                    stream.listeners.forEach { listener ->
                        try { listener.packet(frame) } catch (error: Exception) { listener.failure(error) }
                    }
                }
            } catch (error: Exception) { stream.listeners.forEach { it.failure(error) } }
            finally {
                synchronized(this@PacketBroker) {
                    collectStats(stream)
                    native.close(stream.fd)
                    if (current === stream) current = null
                }
            }
        }, "broadband-packets").apply { isDaemon = true; start() }
        return Lease(ref.id, listener)
    }
    private fun collectStats(stream: Stream) {
        runCatching { native.stats(stream.fd) }.onSuccess {
            stream.received += it[0]; stream.dropped += it[1]
        }.onFailure { stream.statsKnown = false }
    }
    fun probe(ref: RootInterface): String? = runCatching { native.open(ref.index).also { native.close(it) }; null }.getOrElse { it.message ?: "原始报文权限不可用" }
    override fun close() {
        val stream = synchronized(this) { current?.also { it.running.set(false) } }
        if (stream != null && Thread.currentThread() != stream.thread) stream.thread.join(800)
    }
}
