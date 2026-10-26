package dev.broadband.toolbox.privileged

import dev.broadband.toolbox.InputRules
import org.json.JSONArray
import org.json.JSONObject

/** No platform writes: shared by preview, apply and recovery input checking. */
object EthernetPlan {
    private fun ipv4(value: String): String {
        val address = InputRules.ipv4(value)
        if (address.isLoopbackAddress) throw RootFailure("INVALID_INPUT", "临时地址不能是回环地址")
        return address.hostAddress!!
    }
    private fun number(address: String): Long = address.split('.').fold(0L) { value, part -> (value shl 8) or part.toLong() }
    private fun mask(prefix: Int): Long = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    private fun hostInSubnet(address: String, prefix: Int) {
        val host = number(address) and mask(prefix).inv() and 0xffffffffL
        if (prefix < 31 && (host == 0L || host == (mask(prefix).inv() and 0xffffffffL))) throw RootFailure("INVALID_INPUT", "不能使用该子网的网络地址或广播地址")
    }
    fun parse(p: JSONObject): JSONObject {
        val mode = InputRules.choice(p, "mode", "dhcp", setOf("dhcp", "static"))
        val result = JSONObject().put("mode", mode).put("leaseMinutes", InputRules.integer(p, "leaseMinutes", 5, 1, 30))
            .put("reconnect", p.optBoolean("reconnect", false))
        if (p.has("mtu") && !p.isNull("mtu")) result.put("mtu", InputRules.integer(p, "mtu", 1500, 576, 65535))
        if (mode == "static") {
            val address = ipv4(p.optString("address"))
            val prefix = InputRules.integer(p, "prefix", 24, 1, 32)
            hostInSubnet(address, prefix)
            val gateway = p.optString("gateway").trim().takeIf { it.isNotEmpty() }?.let(::ipv4)
            if (gateway != null) {
                hostInSubnet(gateway, prefix)
                if (gateway == address || (number(address) and mask(prefix)) != (number(gateway) and mask(prefix))) throw RootFailure("INVALID_INPUT", "网关必须是当前子网内的另一个可用地址")
            }
            result.put("address", address).put("prefix", prefix).put("gateway", gateway ?: "")
            val dns = p.optJSONArray("dns") ?: JSONArray()
            if (dns.length() > 4) throw RootFailure("INVALID_INPUT", "最多填写 4 个 DNS 地址")
            result.put("dns", JSONArray((0 until dns.length()).map { ipv4(dns.getString(it)) }.distinct()))
        }
        val route = p.optString("routeDestination").trim()
        if (route.isNotEmpty()) {
            val parts = route.split('/')
            val prefix = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size != 2 || prefix == null || prefix !in 1..32) throw RootFailure("INVALID_INPUT", "附加路由需要明确网段，例如 192.168.10.0/24；不能修改全局默认路由")
            val destination = ipv4(parts[0])
            if ((number(destination) and mask(prefix)) != number(destination)) throw RootFailure("INVALID_INPUT", "路由地址必须是所填前缀的网络地址")
            result.put("routeDestination", "$destination/$prefix")
            val gateway = p.optString("routeGateway").trim().takeIf { it.isNotEmpty() }?.let(::ipv4) ?: ""
            if (gateway.isNotEmpty() && mode == "static") {
                val local = result.getString("address"); val localPrefix = result.getInt("prefix")
                hostInSubnet(gateway, localPrefix)
                if (gateway == local || (number(local) and mask(localPrefix)) != (number(gateway) and mask(localPrefix))) throw RootFailure("INVALID_INPUT", "路由网关必须可由所选接口直接到达")
            }
            result.put("routeGateway", gateway)
        }
        return result
    }
}
