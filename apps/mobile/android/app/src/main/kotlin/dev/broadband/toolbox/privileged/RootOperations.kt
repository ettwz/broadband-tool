package dev.broadband.toolbox.privileged

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import dev.broadband.toolbox.InputRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class RootOperations(private val broker: PacketBroker) {
    fun run(work: RootWork, output: ParcelFileDescriptor?) {
        val ref = RootInterfaces.require(work.request.getString("interfaceId"))
        if (!ref.up) throw RootFailure("INTERFACE_CHANGED", "接口尚未启用")
        when (work.kind) {
            "root.capture" -> capture(work, ref, output ?: throw RootFailure("INVALID_INPUT", "缺少抓包输出文件"))
            "root.arp" -> arp(work, ref)
            "root.dhcp" -> dhcp(work, ref)
            else -> throw RootFailure("UNSUPPORTED", "不支持此操作")
        }
    }

    private fun capture(work: RootWork, ref: RootInterface, output: ParcelFileDescriptor) {
        val p = work.parameters
        val seconds = InputRules.integer(p, "duration", 60, 10, 300)
        val maximum = InputRules.integer(p, "maxMb", 20, 5, 100).toLong() * 1000000
        val snap = if (p.optBoolean("fullPayload", false)) 65535 else 256
        val filter = InputRules.choice(p, "filter", "ipv4_arp", setOf("ipv4_arp", "dns", "dhcp", "host_port"))
        val host = if (filter == "host_port" && p.optString("host").isNotBlank()) InputRules.ipv4(p.getString("host")).hostAddress else null
        val port = if (filter == "host_port" && p.has("port")) InputRules.integer(p, "port", 0, 1, 65535) else null
        if (filter == "host_port" && host == null && port == null) throw RootFailure("INVALID_INPUT", "请填写 IPv4 或端口")
        if (!OsConstants.S_ISREG(Os.fstat(output.fileDescriptor).st_mode)) throw RootFailure("INVALID_INPUT", "输出必须是普通文件")
        val lock = Any(); var accepting = true; var damaged = false; var count = 0L; var originalBytes = 0L
        val protocols = mutableMapOf<String, Long>(); val limit = AtomicBoolean(false)
        val writer = PcapWriter(BufferedOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(output)), snap)
        var lease: PacketBroker.Lease? = null
        var stats: Pair<Long, Long?> = 0L to null
        var complete = false
        try {
            lease = broker.acquire(ref, object : PacketBroker.Listener {
                override fun packet(frame: Packets.Frame) { synchronized(lock) {
                    if (!accepting || !frame.matches(filter, host, port)) return
                    if (writer.size + minOf(snap, frame.bytes.size) + 16 > maximum) { limit.set(true); accepting = false; return }
                    try { writer.write(frame) } catch (error: Exception) { damaged = true; accepting = false; throw error }
                    count++; originalBytes += frame.bytes.size
                    val protocol = frame.summary(); protocols[protocol] = (protocols[protocol] ?: 0) + 1
                } }
                override fun failure(error: Exception) { work.failure = error }
            })
            val initialStats = lease.stats()
            work.emit("running", JSONObject().put("interface", ref.name).put("linkType", "Ethernet").put("snapLength", snap))
            val end = SystemClock.elapsedRealtime() + seconds * 1000L
            while (SystemClock.elapsedRealtime() < end && !limit.get()) {
                work.delay(250)
                synchronized(lock) {
                    work.emit("capture", JSONObject().put("packets", count).put("bytes", writer.size).put("protocols", JSONObject(protocols as Map<*, *>)))
                }
            }
            stats = lease.stats().let { it.first - initialStats.first to it.second?.minus(initialStats.second ?: 0) }
        } finally {
            // Stop dispatching before closing/flushing; a completed file always has a whole final record.
            synchronized(lock) { accepting = false }
            if (stats.second == null) stats = lease?.stats() ?: stats
            lease?.close()
            try { writer.close(); complete = !damaged } finally {
                work.emit("result", JSONObject().put("captureId", work.id).put("interface", ref.name)
                    .put("packets", count).put("bytes", writer.size).put("originalBytes", originalBytes)
                    .put("dropped", stats.second ?: JSONObject.NULL).put("protocols", JSONObject(protocols as Map<*, *>))
                    .put("fileComplete", complete).put("snapLength", snap).put("linkType", "Ethernet")
                    .put("stopReason", work.cancelled.get() ?: if (limit.get()) "size_limit" else "time_limit"))
            }
        }
    }

    private fun arp(work: RootWork, ref: RootInterface) {
        val p = work.parameters
        val target = InputRules.ipv4(p.optString("host")).hostAddress!!
        val mode = InputRules.choice(p, "mode", "occupancy", setOf("occupancy", "gateway"))
        val source = if (mode == "occupancy") "0.0.0.0" else ref.addresses.firstOrNull()
            ?: throw RootFailure("IPV4_REQUIRED", "网关检查需要当前接口的 IPv4")
        val seen = ConcurrentHashMap<String, JSONObject>()
        val sentAt = java.util.concurrent.atomic.AtomicLong(SystemClock.elapsedRealtime())
        val ownMac = ref.mac.joinToString(":") { "%02x".format(it.toInt() and 255) }
        broker.acquire(ref, object : PacketBroker.Listener {
            override fun packet(frame: Packets.Frame) {
                val data = frame.arpReply() ?: return
                if (data.getString("senderMac") == ownMac) return
                val match = data.getString("senderIp") == target ||
                    (mode == "occupancy" && data.getString("senderIp") == "0.0.0.0" && data.getString("targetIp") == target)
                if (!match) return
                data.put("observedAfterProbeMs", (SystemClock.elapsedRealtime() - sentAt.get()).coerceAtLeast(0))
                seen[data.getString("senderMac")] = data
                work.emit("arp", data)
            }
            override fun failure(error: Exception) { work.failure = error }
        }).use { lease ->
            for (attempt in 1..3) {
                work.check(); sentAt.set(SystemClock.elapsedRealtime())
                lease.send(Packets.arpRequest(ref.mac, target, source))
                work.emit("probe", JSONObject().put("attempt", attempt).put("target", target).put("mode", mode))
                work.delay(1000)
            }
            work.delay(2000)
        }
        work.emit("result", JSONObject().put("target", target).put("responses", JSONArray(seen.values))
            .put("conflict", seen.size > 1).put("message", when {
                seen.size > 1 -> "同一 IPv4 出现多个 MAC，疑似地址冲突"
                seen.isNotEmpty() && mode == "occupancy" -> "观察到地址被使用"
                seen.isNotEmpty() -> "已观察到 ARP 回应"
                mode == "occupancy" -> "未观察到占用，不能据此保证地址空闲"
                else -> "未观察到网关回应"
            }))
    }

    private fun dhcp(work: RootWork, ref: RootInterface) {
        val p = work.parameters
        val mode = InputRules.choice(p, "mode", "observe", setOf("observe", "discover"))
        val seconds = InputRules.integer(p, "duration", 30, 5, 120)
        if (mode == "discover" && !p.optBoolean("acknowledged", false)) throw RootFailure("INVALID_INPUT", "发送前需要确认可能产生临时地址预留")
        val xid = if (mode == "discover") Packets.randomXid() else null
        val messages = java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        broker.acquire(ref, object : PacketBroker.Listener {
            override fun packet(frame: Packets.Frame) {
                val data = frame.dhcp(ref.mac, xid) ?: return
                data.put("elapsedMs", SystemClock.elapsedRealtime() - work.started)
                if (messages.size < 500) messages.add(data)
                work.emit("dhcp", data)
            }
            override fun failure(error: Exception) { work.failure = error }
        }).use { lease ->
            val end = SystemClock.elapsedRealtime() + seconds * 1000L
            if (xid != null) for (attempt in 0..2) {
                work.check(); lease.send(Packets.discover(ref.mac, xid, attempt))
                work.emit("probe", JSONObject().put("attempt", attempt + 1).put("xid", xid).put("message", "Discover"))
                work.delay(1000)
            }
            while (SystemClock.elapsedRealtime() < end) work.delay(100)
        }
        val servers = messages.mapNotNull { it.optString("server").takeIf(String::isNotEmpty) }.distinct()
        val observed = messages.map { it.getString("message") }.distinct()
        work.emit("result", JSONObject().put("mode", mode).put("messages", JSONArray(messages))
            .put("servers", JSONArray(servers)).put("multipleServers", servers.size > 1)
            .put("missingSteps", JSONArray(listOf("Discover", "Offer", "Request", "ACK").filter { it !in observed }))
            .put("message", if (xid != null) "仅发送 Discover，未申请或释放租约" else "仅记录观察到的本机 DHCP 报文"))
    }
}
