package dev.broadband.toolbox

import org.junit.Assert.*
import org.junit.Test

class TelnetCodecTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test fun fragmentedNegotiationDoesNotLeakControlBytes() {
        val replies = mutableListOf<ByteArray>()
        val echo = mutableListOf<Boolean>()
        val codec = TelnetCodec(replies::add, echo::add)
        assertArrayEquals("A".toByteArray(), codec.decode(bytes(65, 255)))
        assertArrayEquals(byteArrayOf(), codec.decode(bytes(251)))
        assertArrayEquals("B".toByteArray(), codec.decode(bytes(1, 66)))
        assertArrayEquals(bytes(255, 253, 1), replies.single())
        assertEquals(listOf(false), echo)
        codec.decode(bytes(255, 251, 1))
        assertEquals(1, replies.size)
    }

    @Test fun escapedIacAndUtf8RemainByteExact() {
        val codec = TelnetCodec({}, {})
        assertArrayEquals(bytes(255), codec.decode(bytes(255, 255)))
        val text = "网络工具".toByteArray(Charsets.UTF_8)
        val first = codec.decode(text.copyOfRange(0, 2))
        val second = codec.decode(text.copyOfRange(2, text.size))
        assertArrayEquals(text, first + second)
    }

    @Test fun refusesUnsupportedOptionWithoutEchoingText() {
        val replies = mutableListOf<ByteArray>()
        val codec = TelnetCodec(replies::add, {})
        assertEquals(0, codec.decode(bytes(255, 253, 99)).size)
        assertArrayEquals(bytes(255, 252, 99), replies.single())
    }

    @Test fun windowSizeEscapes255AndTerminalTypeResponds() {
        val replies = mutableListOf<ByteArray>()
        val codec = TelnetCodec(replies::add, {})
        codec.decode(bytes(255, 253, 31))
        codec.resize(255, 256)
        assertArrayEquals(bytes(255, 250, 31, 0, 255, 255, 1, 0, 255, 240), replies.last())
        codec.decode(bytes(255, 253, 24))
        codec.decode(bytes(255, 250, 24, 1, 255))
        codec.decode(bytes(240))
        assertArrayEquals(bytes(255, 250, 24, 0) + "xterm-256color".toByteArray() + bytes(255, 240), replies.last())
    }

    @Test fun inputLineEndingHonorsBinaryNegotiation() {
        val codec = TelnetCodec({}, {})
        assertArrayEquals("hello\r\n".toByteArray(), codec.encodeInput("hello\r".toByteArray()))
        assertArrayEquals("hello\r\n".toByteArray(), codec.encodeInput("hello\r\n".toByteArray()))
        codec.decode(bytes(255, 253, 0))
        assertArrayEquals(bytes(13, 255, 255), codec.encodeInput(bytes(13, 255)))
    }
}
