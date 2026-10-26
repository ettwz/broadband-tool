package dev.broadband.toolbox

import java.net.IDN
import java.net.Inet4Address
import java.net.InetAddress
import org.json.JSONObject

object InputRules {
    fun ipv4(value: String): Inet4Address {
        val pieces = value.split('.')
        if (pieces.size != 4 || pieces.any {
                it.isEmpty() || it.length > 3 || !it.all { digit -> digit in '0'..'9' } ||
                (it.length > 1 && it.startsWith('0')) || (it.toIntOrNull() ?: 256) > 255
            }) throw FlutterError("INVALID_INPUT", "请输入有效的 IPv4 地址")
        val bytes = pieces.map { it.toInt().toByte() }.toByteArray()
        val address = InetAddress.getByAddress(bytes) as Inet4Address
        if (address.isAnyLocalAddress || bytes[0].toInt() == 0 || address.isMulticastAddress ||
            (bytes[0].toInt() and 255) >= 224) {
            throw FlutterError("INVALID_INPUT", "目标必须是单播 IPv4 地址")
        }
        return address
    }

    fun host(value: String): String {
        val clean = value.trim()
        if (clean.isEmpty() || clean.length > 253 || clean.any { it.code < 33 || it in ";|&$`/\\:<>\"'" }) {
            throw FlutterError("INVALID_INPUT", "请输入 IPv4 或有效域名")
        }
        if (clean.all { it.isDigit() || it == '.' }) return ipv4(clean).hostAddress!!
        val ascii = try { IDN.toASCII(clean, IDN.USE_STD3_ASCII_RULES).trimEnd('.') }
                    catch (_: IllegalArgumentException) { throw FlutterError("INVALID_INPUT", "域名格式不正确") }
        if (ascii.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) {
            throw FlutterError("INVALID_INPUT", "域名格式不正确")
        }
        return ascii.lowercase()
    }

    fun integer(params: JSONObject, name: String, default: Int, min: Int, max: Int): Int {
        val raw = if (params.has(name)) params.get(name) else default
        val value = when (raw) {
            is Number -> raw.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
        if (value == null || value !in min.toLong()..max.toLong()) {
            throw FlutterError("INVALID_INPUT", "$name 必须在 $min–$max 之间")
        }
        return value.toInt()
    }

    fun choice(params: JSONObject, name: String, default: String, allowed: Set<String>): String {
        val value = params.optString(name, default)
        if (value !in allowed) throw FlutterError("INVALID_INPUT", "$name 选项不支持")
        return value
    }
}
