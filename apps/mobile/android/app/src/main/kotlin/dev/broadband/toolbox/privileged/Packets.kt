package dev.broadband.toolbox.privileged

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** Ethernet frames only. Unknown/truncated frames never acquire invented fields. */
object Packets {
    fun u16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
    fun ip(bytes: ByteArray, offset: Int): String = (offset until offset + 4).joinToString(".") { (bytes[it].toInt() and 255).toString() }
    fun mac(bytes: ByteArray, offset: Int): String = (offset until offset + 6).joinToString(":") { "%02x".format(bytes[it].toInt() and 255) }
    fun ipBytes(address: String): ByteArray = address.split('.').map { it.toInt().toByte() }.toByteArray()
    private fun put16(bytes: ByteArray, offset: Int, number: Int) { bytes[offset] = (number shr 8).toByte(); bytes[offset + 1] = number.toByte() }
    data class Frame(val bytes: ByteArray, val timestampUs: Long) {
        val etherType = if (bytes.size >= 14) u16(bytes, 12) else 0
        val arp: Boolean get() = etherType == 0x0806 && bytes.size >= 42 && u16(bytes, 14) == 1 && u16(bytes, 16) == 0x0800 && bytes[18].toInt() == 6 && bytes[19].toInt() == 4
        val ipOffset: Int get() = if (etherType == 0x0800 && bytes.size >= 34 && (bytes[14].toInt() and 0xf0) == 0x40) 14 else -1
        val transportOffset: Int get() {
            if (ipOffset < 0) return -1
            val header = (bytes[14].toInt() and 15) * 4
            if (header < 20 || bytes.size < 14 + header + 4 || u16(bytes, 20) and 0x1fff != 0) return -1
            return 14 + header
        }
        val protocol: Int get() = if (ipOffset < 0) -1 else bytes[23].toInt() and 255
        val sourcePort: Int get() = if (transportOffset >= 0 && protocol in setOf(6, 17)) u16(bytes, transportOffset) else -1
        val destinationPort: Int get() = if (transportOffset >= 0 && protocol in setOf(6, 17)) u16(bytes, transportOffset + 2) else -1
        fun matches(filter: String, address: String?, port: Int?): Boolean = when (filter) {
            "ipv4_arp" -> ipOffset >= 0 || arp
            "dns" -> protocol in setOf(6, 17) && (sourcePort == 53 || destinationPort == 53)
            "dhcp" -> protocol == 17 && setOf(sourcePort, destinationPort) == setOf(67, 68)
            "host_port" -> arp || (ipOffset >= 0 && (address == null || ip(bytes, 26) == address || ip(bytes, 30) == address) && (port == null || sourcePort == port || destinationPort == port))
            else -> false
        }
        fun summary(): String = when {
            arp -> "ARP"
            protocol == 1 -> "ICMP"
            sourcePort == 53 || destinationPort == 53 -> "DNS"
            matches("dhcp", null, null) -> "DHCP"
            protocol == 6 -> "TCP"
            protocol == 17 -> "UDP"
            else -> "IPv4"
        }
        fun arpReply(): JSONObject? = if (!arp) null else JSONObject().put("operation", u16(bytes, 20))
            .put("senderIp", ip(bytes, 28)).put("senderMac", mac(bytes, 22))
            .put("targetIp", ip(bytes, 38)).put("targetMac", mac(bytes, 32))
        fun dhcp(localMac: ByteArray, requiredXid: Long? = null): JSONObject? {
            if (!matches("dhcp", null, null)) return null
            val boot = transportOffset + 8
            if (bytes.size < boot + 240 || bytes[boot + 1].toInt() != 1 || bytes[boot + 2].toInt() != 6) return null
            if (!bytes.copyOfRange(boot + 28, boot + 34).contentEquals(localMac)) return null
            if (!bytes.copyOfRange(boot + 236, boot + 240).contentEquals(byteArrayOf(99, 130.toByte(), 83, 99))) return null
            val xid = ByteBuffer.wrap(bytes, boot + 4, 4).int.toLong() and 0xffffffffL
            if (requiredXid != null && xid != requiredXid) return null
            val result = JSONObject().put("xid", xid).put("offeredIp", ip(bytes, boot + 16)).put("sourceIp", ip(bytes, 26))
            var offset = boot + 240
            var type = 0
            while (offset < bytes.size) {
                val option = bytes[offset++].toInt() and 255
                if (option == 255) break
                if (option == 0) continue
                if (offset >= bytes.size) return null
                val size = bytes[offset++].toInt() and 255
                if (offset + size > bytes.size) return null
                when {
                    option == 53 && size == 1 -> type = bytes[offset].toInt() and 255
                    option == 54 && size == 4 -> result.put("server", ip(bytes, offset))
                    option == 1 && size == 4 -> result.put("mask", ip(bytes, offset))
                    option in setOf(3, 6) && size > 0 && size % 4 == 0 -> result.put(if (option == 3) "gateways" else "dns", JSONArray((offset until offset + size step 4).map { ip(bytes, it) }))
                    option == 51 && size == 4 -> result.put("leaseSeconds", ByteBuffer.wrap(bytes, offset, 4).int.toLong() and 0xffffffffL)
                }
                offset += size
            }
            val label = mapOf(1 to "Discover", 2 to "Offer", 3 to "Request", 5 to "ACK", 6 to "NAK", 7 to "Release", 8 to "Inform")[type] ?: return null
            return result.put("message", label).put("timestampUs", timestampUs)
        }
    }

    fun arpRequest(localMac: ByteArray, target: String, source: String = "0.0.0.0"): ByteArray {
        require(localMac.size == 6)
        val bytes = ByteArray(42)
        bytes.fill(255.toByte(), 0, 6); localMac.copyInto(bytes, 6)
        put16(bytes, 12, 0x0806); put16(bytes, 14, 1); put16(bytes, 16, 0x0800)
        bytes[18] = 6; bytes[19] = 4; put16(bytes, 20, 1)
        localMac.copyInto(bytes, 22); ipBytes(source).copyInto(bytes, 28); ipBytes(target).copyInto(bytes, 38)
        return bytes
    }

    fun randomXid(): Long = SecureRandom().nextInt().toLong() and 0xffffffffL
    fun discover(localMac: ByteArray, xid: Long, seconds: Int): ByteArray {
        require(localMac.size == 6)
        // DHCPDISCOVER only: no REQUEST/RELEASE and no requested address option.
        val options = byteArrayOf(53, 1, 1, 61, 7, 1, *localMac, 55, 5, 1, 3, 6, 51, 54, 255.toByte())
        val bytes = ByteArray(14 + 20 + 8 + 240 + options.size)
        bytes.fill(255.toByte(), 0, 6); localMac.copyInto(bytes, 6); put16(bytes, 12, 0x0800)
        val ip = 14; val udp = 34; val boot = 42
        bytes[ip] = 0x45; put16(bytes, ip + 2, bytes.size - 14); bytes[ip + 8] = 64; bytes[ip + 9] = 17
        bytes.fill(255.toByte(), ip + 16, ip + 20)
        var sum = 0
        for (i in ip until ip + 20 step 2) sum += u16(bytes, i)
        while (sum shr 16 != 0) sum = (sum and 65535) + (sum shr 16)
        put16(bytes, ip + 10, sum.inv() and 65535)
        put16(bytes, udp, 68); put16(bytes, udp + 2, 67); put16(bytes, udp + 4, bytes.size - udp)
        bytes[boot] = 1; bytes[boot + 1] = 1; bytes[boot + 2] = 6
        ByteBuffer.wrap(bytes, boot + 4, 4).putInt(xid.toInt())
        put16(bytes, boot + 8, seconds.coerceIn(0, 65535)); put16(bytes, boot + 10, 0x8000)
        localMac.copyInto(bytes, boot + 28); byteArrayOf(99, 130.toByte(), 83, 99).copyInto(bytes, boot + 236)
        options.copyInto(bytes, boot + 240)
        return bytes
    }
}

class PcapWriter(private val output: OutputStream, private val snapLength: Int) : AutoCloseable {
    var size = 24L; private set
    init {
        require(snapLength in 64..65535)
        output.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(0xa1b2c3d4.toInt())
            .putShort(2).putShort(4).putInt(0).putInt(0).putInt(snapLength).putInt(1).array())
    }
    fun write(frame: Packets.Frame): Int {
        val length = minOf(frame.bytes.size, snapLength)
        output.write(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt((frame.timestampUs / 1000000).toInt()).putInt((frame.timestampUs % 1000000).toInt())
            .putInt(length).putInt(frame.bytes.size).array())
        output.write(frame.bytes, 0, length); size += 16 + length
        return length
    }
    override fun close() { output.close() }
}
