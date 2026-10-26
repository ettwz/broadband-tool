package dev.broadband.toolbox

import org.json.JSONObject

/** Validate the entire request before resolution, socket creation or file creation. */
object TaskValidation {
    fun validate(kind: String, p: JSONObject) {
        fun number(name: String, default: Int, min: Int, max: Int) = InputRules.integer(p, name, default, min, max)
        fun host(name: String = "host") = InputRules.host(p.optString(name))
        when (kind) {
            "ping" -> { host(); number("count", 10, 1, 1000); number("intervalMs", 1000, 200, 10000); number("timeoutMs", 2000, 200, 10000); number("payloadBytes", 56, 8, 1400) }
            "trace" -> host()
            "tcping" -> { host(); number("port", 443, 1, 65535); number("count", 10, 1, 1000); number("intervalMs", 1000, 200, 10000); number("timeoutMs", 2000, 200, 10000) }
            "telnet" -> { host(); number("port", 23, 1, 65535); number("timeoutMs", 5000, 200, 10000) }
            "systemResolve" -> host("name")
            "iperf" -> {
                val role = InputRules.choice(p, "role", "client", setOf("client", "server"))
                number("port", 5201, if (role == "server") 1024 else 1, 65535)
                if (role == "client") {
                    host(); number("duration", 10, 5, 120)
                    val protocol = InputRules.choice(p, "protocol", "tcp", setOf("tcp", "udp"))
                    number("streams", 1, 1, if (protocol == "udp") 1 else 8)
                    if (protocol == "udp") number("rateMbps", 10, 1, 1000)
                }
            }
            "dns" -> {
                host("name"); if (p.optString("server").isNotEmpty()) InputRules.ipv4(p.getString("server"))
                number("port", 53, 1, 65535); number("timeoutMs", 3000, 200, 10000); number("retries", 1, 0, 3)
                InputRules.choice(p, "type", "A", setOf("A", "CNAME", "MX", "NS", "TXT", "SOA", "PTR"))
                InputRules.choice(p, "transport", "udp", setOf("udp", "tcp"))
            }
            "root.capture" -> {
                number("duration", 60, 10, 300); number("maxMb", 20, 5, 100)
                val filter = InputRules.choice(p, "filter", "ipv4_arp", setOf("ipv4_arp", "dns", "dhcp", "host_port"))
                if (filter == "host_port") {
                    if (p.optString("host").isNotBlank()) InputRules.ipv4(p.getString("host"))
                    if (p.has("port")) number("port", 0, 1, 65535)
                    if (p.optString("host").isBlank() && !p.has("port")) throw FlutterError("INVALID_INPUT", "请填写 IPv4 或端口")
                }
            }
            "root.arp" -> { InputRules.ipv4(p.optString("host")); InputRules.choice(p, "mode", "occupancy", setOf("occupancy", "gateway")) }
            "root.dhcp" -> {
                number("duration", 30, 5, 120)
                if (InputRules.choice(p, "mode", "observe", setOf("observe", "discover")) == "discover" && !p.optBoolean("acknowledged")) throw FlutterError("INVALID_INPUT", "发送发现请求前需要确认")
            }
        }
    }
}
