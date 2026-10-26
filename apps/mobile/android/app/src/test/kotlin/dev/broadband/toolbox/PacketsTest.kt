package dev.broadband.toolbox

import dev.broadband.toolbox.privileged.Packets
import dev.broadband.toolbox.privileged.PcapWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketsTest {
    private val mac = byteArrayOf(2, 3, 4, 5, 6, 7)
    @Test fun addressProbeUsesZeroSourceAndNeverClaimsAnAddress() {
        val bytes = Packets.arpRequest(mac, "192.0.2.24")
        val frame = Packets.Frame(bytes, 1000)
        val arp = frame.arpReply()!!
        assertEquals(1, arp.getInt("operation"))
        assertEquals("0.0.0.0", arp.getString("senderIp"))
        assertEquals("192.0.2.24", arp.getString("targetIp"))
        assertEquals("00:00:00:00:00:00", arp.getString("targetMac"))
        assertTrue(frame.matches("ipv4_arp", null, null))
        assertFalse(frame.matches("dns", null, null))
    }
    @Test fun dhcpDiscoveryHasCorrectChecksumAndIdentityWithoutRequest() {
        val bytes = Packets.discover(mac, 0x12345678, 2)
        val frame = Packets.Frame(bytes, 42000)
        assertEquals(68, frame.sourcePort)
        assertEquals(67, frame.destinationPort)
        var sum = (14 until 34 step 2).sumOf { Packets.u16(bytes, it) }
        while (sum shr 16 != 0) sum = (sum and 65535) + (sum shr 16)
        assertEquals(65535, sum)
        val data = frame.dhcp(mac, 0x12345678)!!
        assertEquals("Discover", data.getString("message"))
        assertEquals("0.0.0.0", data.getString("offeredIp"))
        assertNull(frame.dhcp(mac, 0x55555555))
        assertNull(frame.dhcp(byteArrayOf(1, 2, 3, 4, 5, 6)))
    }
    @Test fun truncatedDhcpOptionsAndUnrelatedFramesAreIgnored() {
        val bytes = Packets.discover(mac, 123, 0)
        val truncated = bytes.copyOf(bytes.size - 2)
        assertNull(Packets.Frame(truncated, 0).dhcp(mac))
        assertNull(Packets.Frame(byteArrayOf(1, 2, 3), 0).dhcp(mac))
        assertFalse(Packets.Frame(byteArrayOf(), 0).matches("ipv4_arp", null, null))
    }
    @Test fun pcapRecordsRealOriginalLengthAndTruncation() {
        val output = ByteArrayOutputStream()
        PcapWriter(output, 64).use { it.write(Packets.Frame(ByteArray(100) { n -> n.toByte() }, 1234567890)) }
        val bytes = output.toByteArray()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0xa1b2c3d4.toInt(), data.int)
        assertEquals(2, data.short.toInt()); assertEquals(4, data.short.toInt())
        data.position(16); assertEquals(64, data.int); assertEquals(1, data.int)
        assertEquals(1234, data.int); assertEquals(567890, data.int)
        assertEquals(64, data.int); assertEquals(100, data.int)
        assertEquals(24 + 16 + 64, bytes.size)
    }
    @Test fun captureFilterDoesNotExpandWhenDhcpObserverIsPresent() {
        val dhcp = Packets.Frame(Packets.discover(mac, 1, 0), 0)
        assertNotNull(dhcp.dhcp(mac))
        assertTrue(dhcp.matches("dhcp", null, null))
        assertFalse(dhcp.matches("dns", null, null))
        assertFalse(dhcp.matches("host_port", "192.0.2.1", null))
        val gateway = Packets.Frame(Packets.arpRequest(mac, "192.0.2.1"), 0)
        assertTrue(gateway.matches("host_port", "192.0.2.1", 80))
    }
}
