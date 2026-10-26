package dev.broadband.toolbox

import android.os.SystemClock
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TaskSession(val request: TaskRequestData, private val output: (TaskEventData) -> Unit) {
    val id get() = request.requestId
    val cancelled = AtomicBoolean(false)
    private val terminal = AtomicBoolean(false)
    private val sequence = AtomicLong(0)
    private val started = SystemClock.elapsedRealtime()
    private val resources = CopyOnWriteArrayList<Closeable>()
    private val cancelActions = CopyOnWriteArrayList<() -> Unit>()
    @Volatile var cancellationReason = "CANCELLED"
    @Volatile var thread: Thread? = null
    @Volatile var terminalWriter: ((ByteArray) -> Unit)? = null
    @Volatile var terminalResize: ((Int, Int) -> Unit)? = null
    val finished get() = terminal.get()

    fun emit(state: String, data: JSONObject = JSONObject(), code: String? = null, message: String? = null) {
        if (terminal.get()) return
        output(TaskEventData(id, request.kind, state, sequence.incrementAndGet(),
            SystemClock.elapsedRealtime() - started, data.toString(), code, message))
    }

    fun finish(state: String, data: JSONObject = JSONObject(), code: String? = null, message: String? = null) {
        if (!terminal.compareAndSet(false, true)) return
        resources.forEach { runCatching { it.close() } }
        resources.clear()
        terminalWriter = null; terminalResize = null
        output(TaskEventData(id, request.kind, state, sequence.incrementAndGet(),
            SystemClock.elapsedRealtime() - started, data.toString(), code, message))
    }

    fun checkActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException(cancellationReason)
    }

    fun <T : Closeable> own(resource: T): T {
        resources += resource
        if (cancelled.get()) runCatching { resource.close() }
        return resource
    }
    fun onCancel(action: () -> Unit) {
        cancelActions += action
        if (cancelled.get()) action()
    }
    fun cancel(reason: String = "CANCELLED") {
        if (terminal.get() || !cancelled.compareAndSet(false, true)) return
        cancellationReason = reason
        cancelActions.forEach { runCatching { it() } }
        resources.forEach { runCatching { it.close() } }
        thread?.interrupt()
    }
    fun delay(millis: Long) {
        val until = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < until) {
            checkActive()
            Thread.sleep((until - SystemClock.elapsedRealtime()).coerceIn(1, 100))
        }
    }
}
