package dev.broadband.toolbox

import java.io.ByteArrayOutputStream

/** Telnet framing and negotiations; terminal rendering is handled by xterm.dart. */
class TelnetCodec(private val reply: (ByteArray) -> Unit, private val echoChanged: (Boolean) -> Unit) {
    private var state = 0
    private var command = 0
    private val sub = ByteArrayOutputStream()
    private val local = mutableSetOf<Int>()
    private val remote = mutableSetOf<Int>()
    private var columns = 80
    private var rows = 24

    fun decode(bytes: ByteArray, length: Int = bytes.size): ByteArray {
        val output = ByteArrayOutputStream()
        for (i in 0 until length) {
            val value = bytes[i].toInt() and 255
            when (state) {
                0 -> if (value == 255) state = 1 else output.write(value)
                1 -> when (value) {
                    255 -> { output.write(255); state = 0 }
                    251, 252, 253, 254 -> { command = value; state = 2 }
                    250 -> { sub.reset(); state = 3 }
                    else -> state = 0
                }
                2 -> { negotiate(command, value); state = 0 }
                3 -> if (value == 255) state = 4 else if (sub.size() < 4096) sub.write(value)
                4 -> when (value) {
                    240 -> { subnegotiation(sub.toByteArray()); sub.reset(); state = 0 }
                    255 -> { if (sub.size() < 4096) sub.write(255); state = 3 }
                    else -> { sub.reset(); state = 0 }
                }
            }
        }
        return output.toByteArray()
    }

    private fun negotiate(command: Int, option: Int) {
        when (command) {
            251 -> if (option in setOf(0, 1, 3)) {
                if (remote.add(option)) send(253, option)
                if (option == 1) echoChanged(false)
            } else send(254, option)
            252 -> {
                if (remote.remove(option)) send(254, option)
                if (option == 1) echoChanged(true)
            }
            253 -> if (option in setOf(0, 3, 24, 31)) {
                if (local.add(option)) send(251, option)
                if (option == 31) sendSize()
            } else send(252, option)
            254 -> if (local.remove(option)) send(252, option)
        }
    }

    private fun send(command: Int, option: Int) = reply(byteArrayOf(255.toByte(), command.toByte(), option.toByte()))
    private fun subnegotiation(data: ByteArray) {
        if (data.size >= 2 && data[0].toInt() == 24 && data[1].toInt() == 1 && 24 in local) {
            val name = "xterm-256color".toByteArray(Charsets.US_ASCII)
            reply(byteArrayOf(255.toByte(), 250.toByte(), 24, 0) + name + byteArrayOf(255.toByte(), 240.toByte()))
        }
    }

    fun resize(columns: Int, rows: Int) {
        this.columns = columns.coerceIn(1, 65535); this.rows = rows.coerceIn(1, 65535)
        if (31 in local) sendSize()
    }
    private fun sendSize() {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(255.toByte(), 250.toByte(), 31))
        for (value in intArrayOf(columns shr 8, columns and 255, rows shr 8, rows and 255)) {
            output.write(value); if (value == 255) output.write(value)
        }
        output.write(byteArrayOf(255.toByte(), 240.toByte()))
        reply(output.toByteArray())
    }

    fun encodeInput(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        for (i in data.indices) {
            val value = data[i].toInt() and 255
            output.write(value)
            if (value == 255) output.write(255)
            // xterm emits CR for Enter. NVT expects CR LF unless binary is negotiated.
            if (value == 13 && 0 !in local && (i + 1 == data.size || data[i + 1].toInt() != 10)) output.write(10)
        }
        return output.toByteArray()
    }
}
