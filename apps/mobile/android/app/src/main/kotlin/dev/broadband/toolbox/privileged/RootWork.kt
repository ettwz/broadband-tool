package dev.broadband.toolbox.privileged

import android.os.SystemClock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

class RootWork(val request: JSONObject, private val events: IRootEvents) {
    val id: String = request.getString("id")
    val kind: String = request.getString("kind")
    val parameters: JSONObject = request.getJSONObject("parameters")
    val started = SystemClock.elapsedRealtime()
    val cancelled = AtomicReference<String?>(null)
    @Volatile var failure: Exception? = null
    fun cancel(reason: String = "CANCELLED") { cancelled.compareAndSet(null, reason) }
    fun check() {
        failure?.let { throw it }
        cancelled.get()?.let { throw RootFailure(it, "操作已停止") }
        if (SystemClock.elapsedRealtime() - started > 310000) throw RootFailure("TIMEOUT", "操作已达到最大时限")
    }
    fun delay(millis: Long) {
        val end = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < end) { check(); Thread.sleep((end - SystemClock.elapsedRealtime()).coerceIn(1, 100)) }
    }
    fun emit(state: String, data: JSONObject = JSONObject()) {
        try { events.onEvent(id, state, data.toString()) }
        catch (_: Exception) { cancel("PRIVILEGE_LOST") }
    }
}
