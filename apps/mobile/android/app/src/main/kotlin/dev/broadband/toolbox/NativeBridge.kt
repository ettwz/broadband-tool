package dev.broadband.toolbox

import java.io.File
import androidx.annotation.Keep

@Keep
fun interface NativeCallback {
    fun onNativeEvent(kind: String, data: String)
}

class NativeBridge(libraryDirectory: String? = null) {
    init {
        if (libraryDirectory == null) System.loadLibrary("broadband_native")
        else System.load(File(libraryDirectory, "libbroadband_native.so").absolutePath)
    }
    external fun createJob(): Long
    external fun cancelJob(handle: Long)
    external fun destroyJob(handle: Long)
    external fun runProbe(handle: Long, kind: String, target: String, network: Long,
                          count: Int, interval: Int, timeout: Int, payload: Int,
                          callback: NativeCallback): Int
}
