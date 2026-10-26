package dev.broadband.toolbox

import android.net.Network
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class DnsEngine {
    private data class Cached(val expires: Long, val json: String)
    private val cache = linkedMapOf<String, Cached>()

    @Synchronized fun clear(): Int { val size = cache.size; cache.clear(); return size }

    fun run(session: TaskSession, network: Network, info: NetworkData, params: JSONObject) {
        val typeName = InputRules.choice(params, "type", "A", setOf("A", "CNAME", "MX", "TXT", "NS", "SOA", "PTR"))
        val entered = params.optString("name").trim()
        val queryName = if (typeName == "PTR" && entered.matches(Regex("[0-9.]+"))) {
            InputRules.ipv4(entered)
            entered.split('.').reversed().joinToString(".") + ".in-addr.arpa"
        } else InputRules.host(entered)
        val name = Name.fromString(if (queryName.endsWith('.')) queryName else "$queryName.")
        val type = Type.value(typeName)
        val explicit = params.optString("server").trim()
        val server = if (explicit.isNotBlank()) InputRules.ipv4(explicit) else {
            info.dnsServers.firstNotNullOfOrNull { runCatching { InputRules.ipv4(it) }.getOrNull() }
                ?: throw FlutterError("NO_IPV4_DNS", "当前网络没有 IPv4 DNS，请填写 DNS 服务器")
        }
        val port = InputRules.integer(params, "port", 53, 1, 65535)
        val timeout = InputRules.integer(params, "timeoutMs", 3000, 200, 10000)
        val retries = InputRules.integer(params, "retries", 1, 0, 3)
        var transport = InputRules.choice(params, "transport", "udp", setOf("udp", "tcp"))
        val bypass = params.optBoolean("bypassCache", true)
        val cacheKey = "${info.id}:${info.generation}|$server|$port|$name|$type|$transport"
        if (!bypass) synchronized(this) {
            val existing = cache[cacheKey]
            if (existing != null && existing.expires > SystemClock.elapsedRealtime()) {
                session.emit("result", JSONObject(existing.json).put("fromCache", true))
                return
            }
            cache.remove(cacheKey)
        }
        val question = Record.newRecord(name, type, DClass.IN)
        val query = Message.newQuery(question)
        val request = query.toWire()
        var response: ByteArray? = null
        val started = SystemClock.elapsedRealtime()
        var sourceIp: String? = null
        var tcpFallback = false
        for (attempt in 0..retries) {
            session.checkActive()
            try {
                if (transport == "udp") {
                    val socket = session.own(DatagramSocket(null))
                    socket.use {
                        network.bindSocket(it)
                        it.soTimeout = timeout
                        it.connect(server, port)
                        sourceIp = it.localAddress.hostAddress
                        it.send(DatagramPacket(request, request.size))
                        val buffer = ByteArray(65535)
                        val packet = DatagramPacket(buffer, buffer.size)
                        it.receive(packet)
                        response = buffer.copyOf(packet.length)
                    }
                    val bytes = response!!
                    if (bytes.size < 12 || ((bytes[0].toInt() and 255) shl 8 or (bytes[1].toInt() and 255)) != query.header.id) {
                        throw FlutterError("INVALID_RESPONSE", "DNS 回复标识不匹配")
                    }
                    if (bytes[2].toInt() and 2 != 0) { transport = "tcp"; tcpFallback = true }
                }
                if (transport == "tcp") {
                    val socket = session.own(network.socketFactory.createSocket())
                    socket.use {
                        it.connect(InetSocketAddress(server, port), timeout)
                        it.soTimeout = timeout
                        sourceIp = it.localAddress.hostAddress
                        val output = DataOutputStream(it.getOutputStream())
                        output.writeShort(request.size); output.write(request); output.flush()
                        val input = DataInputStream(it.getInputStream())
                        val size = input.readUnsignedShort()
                        if (size < 12) throw FlutterError("INVALID_RESPONSE", "DNS TCP 回复过短")
                        response = ByteArray(size).also(input::readFully)
                    }
                }
                break
            } catch (error: SocketTimeoutException) {
                if (attempt == retries) throw error
                session.emit("progress", JSONObject().put("message", "DNS 请求超时，正在重试"))
            }
        }
        session.checkActive()
        val bytes = response ?: throw SocketTimeoutException("DNS timeout")
        val message = Message(bytes)
        if (message.header.id != query.header.id || !message.header.getFlag(Flags.QR.toInt()) ||
            message.question?.name != question.name || message.question?.type != question.type ||
            message.question?.dClass != question.dClass) throw FlutterError("INVALID_RESPONSE", "DNS 回复与当前问题不匹配")
        val result = JSONObject().put("name", name.toString()).put("type", typeName)
            .put("server", server.hostAddress).put("port", port).put("transport", transport.uppercase())
            .put("elapsedMs", SystemClock.elapsedRealtime() - started).put("rcode", Rcode.string(message.rcode))
            .put("flags", message.header.printFlags()).put("bytes", bytes.size).put("fromCache", false)
            .put("tcpFallback", tcpFallback).put("sourceIPv4", sourceIp).put("networkId", info.id)
        val sectionNames = listOf("question", "answer", "authority", "additional")
        for (section in 0..3) {
            val records = JSONArray()
            for (record in message.getSection(section)) {
                records.put(JSONObject().put("name", record.name.toString()).put("type", Type.string(record.type))
                    .put("class", DClass.string(record.dClass)).put("ttl", record.ttl)
                    .put("value", record.rdataToString()))
            }
            result.put(sectionNames[section], records)
        }
        val ttl = message.getSection(Section.ANSWER).minOfOrNull { it.ttl }
        if (!bypass && message.rcode == Rcode.NOERROR && ttl != null && ttl > 0) synchronized(this) {
            cache[cacheKey] = Cached(SystemClock.elapsedRealtime() + ttl.coerceAtMost(86400) * 1000, result.toString())
            while (cache.size > 128) cache.remove(cache.keys.first())
        }
        session.emit("result", result)
    }
}
