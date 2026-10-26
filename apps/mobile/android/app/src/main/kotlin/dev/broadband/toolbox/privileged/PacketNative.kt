package dev.broadband.toolbox.privileged

class PacketNative {
    external fun open(index: Int): Int
    external fun read(fd: Int): ByteArray?
    external fun send(fd: Int, index: Int, bytes: ByteArray)
    external fun stats(fd: Int): LongArray
    external fun close(fd: Int)
    companion object { init { System.loadLibrary("broadband_native") } }
}
