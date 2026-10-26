package dev.broadband.toolbox.privileged

import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

class RootFailure(val code: String, override val message: String) : Exception(message)

data class RootInterface(val id: String, val name: String, val index: Int, val mac: ByteArray,
    val addresses: List<String>, val prefixes: List<Int>, val up: Boolean, val mtu: Int,
    val transport: String, val ethernet: Boolean, val usb: Boolean) {
    fun json(generation: Long): JSONObject = JSONObject().put("id", id).put("name", name).put("index", index)
        .put("generation", generation).put("transport", transport).put("addresses", org.json.JSONArray(addresses))
        .put("prefixes", org.json.JSONArray(prefixes)).put("up", up).put("mtu", mtu).put("ethernet", ethernet)
}

object RootInterfaces {
    fun list(): List<RootInterface> = NetworkInterface.getNetworkInterfaces().toList().mapNotNull { item ->
        runCatching {
            if (!item.name.matches(Regex("[A-Za-z0-9_.:-]{1,15}")) || item.isLoopback) return@mapNotNull null
            val base = File("/sys/class/net/${item.name}")
            val mac = item.hardwareAddress ?: byteArrayOf()
            val device = File(base, "device").canonicalPath
            val usb = device.contains("/usb")
            val transport = when {
                File(base, "wireless").exists() || item.name.startsWith("wlan") -> "wifi"
                usb -> "ethernet"
                item.name.startsWith("eth") -> "ethernet"
                else -> "other"
            }
            val identity = "${item.index}:${item.name}:${mac.joinToString()}:$device"
            val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
            val addresses = item.interfaceAddresses.filter { it.address is Inet4Address }
            RootInterface(id, item.name, item.index, mac, addresses.map { it.address.hostAddress!! },
                addresses.map { it.networkPrefixLength.toInt() }, item.isUp, item.mtu, transport,
                File(base, "type").readText().trim() == "1" && mac.size == 6, usb)
        }.getOrNull()
    }
    fun require(id: String): RootInterface = list().firstOrNull { it.id == id }
        ?: throw RootFailure("INTERFACE_CHANGED", "接口已断开或身份发生变化，请重新选择")
}
