package dev.broadband.toolbox.privileged

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Compare-before-restore transaction, with a durable intent before every mutation. */
class EthernetTransactions(private val backend: EthernetBackend, private val journal: File,
    private val elapsed: () -> Long, private val wall: () -> Long, private val bootId: String) {
    private var record: JSONObject? = null
    @Volatile private var published: JSONObject? = null
    init {
        if (journal.exists()) {
            try {
                if (journal.length() > 512000) throw IllegalStateException("oversized")
                record = JSONObject(journal.readText()).also {
                    require(it.getInt("schema") == 1)
                    if (it.optString("state") !in setOf("restored", "preview")) {
                        it.put("state", "recovery_pending").put("message", "检测到未结束的配置记录，请核对并恢复")
                    }
                }
                publish()
            } catch (_: Exception) {
                published = JSONObject().put("state", "recovery_pending").put("message", "恢复记录损坏，请在系统网络设置中检查此接口；新配置已冻结")
            }
        }
    }
    fun status(): JSONObject? = published?.let { JSONObject(it.toString()) }
    fun blocksDiagnostics(): Boolean = published?.optString("state") in setOf("prepared", "applying", "awaiting_confirmation", "restoring")
    fun needsRestore(): Boolean {
        val data = published ?: return false
        return when (data.optString("state")) {
            "awaiting_confirmation" -> elapsed() >= data.optLong("confirmElapsed")
            "active" -> elapsed() >= data.optLong("expiresElapsed")
            else -> false
        }
    }
    private fun publish() {
        val data = record ?: return
        val public = JSONObject().put("transactionId", data.optString("transactionId")).put("state", data.optString("state"))
            .put("interfaceId", data.optString("interfaceId")).put("interface", data.optString("interface"))
            .put("message", data.optString("message")).put("writable", backend.qualified).put("reason", backend.reason)
        for (name in listOf("plan", "originalSummary", "currentSummary", "confirmUntil", "expiresAt", "confirmElapsed", "expiresElapsed")) {
            if (data.has(name)) public.put(name, data.get(name))
        }
        published = public
    }
    private fun save() {
        val data = record ?: return
        journal.parentFile?.mkdirs()
        val temporary = File(journal.parentFile, "${journal.name}.new")
        if (java.nio.file.Files.isSymbolicLink(temporary.toPath()) || java.nio.file.Files.isSymbolicLink(journal.toPath())) throw RootFailure("RECOVERY_PENDING", "恢复文件路径异常")
        java.io.FileOutputStream(temporary).use { stream -> stream.write(data.toString().toByteArray()); stream.fd.sync() }
        if (!temporary.renameTo(journal)) throw RootFailure("RECOVERY_PENDING", "无法保存恢复记录，未执行下一步配置")
        publish()
    }
    private fun summary(snapshot: JSONObject): JSONObject = JSONObject().put("system", snapshot.getJSONObject("system"))
        .put("addresses", snapshot.getJSONArray("addresses")).put("prefixes", snapshot.getJSONArray("prefixes"))
        .put("mtu", snapshot.getInt("mtu")).put("up", snapshot.getBoolean("up"))
    @Synchronized fun preview(interfaceId: String, parameters: JSONObject): JSONObject {
        if (published?.optString("state") !in listOf(null, "restored", "preview")) throw RootFailure("CONFIG_CONFLICT", "请先处理未结束的临时配置")
        val ref = backend.resolve(interfaceId)
        val original = backend.snapshot(ref)
        val plan = EthernetPlan.parse(parameters)
        backend.validate(ref, plan, original)
        if (plan.has("mtu") && plan.getInt("mtu") != original.getInt("mtu")) {
            val kernel = original.getJSONObject("kernel")
            if (kernel.optInt("minMtu") <= 0 || kernel.optInt("maxMtu") <= 0) throw RootFailure("CAPABILITY_DENIED", "驱动未提供可验证的 MTU 范围，本接口只能保留原 MTU")
            if (plan.getInt("mtu") !in kernel.getInt("minMtu")..kernel.getInt("maxMtu")) throw RootFailure("INVALID_INPUT", "MTU 超出驱动支持范围")
        }
        if (plan.has("routeDestination") && routes(original).any { it.getString("destination") == plan.getString("routeDestination") }) throw RootFailure("CONFIG_CONFLICT", "此接口已存在相同目标路由，不能覆盖")
        record = JSONObject().put("schema", 1).put("transactionId", UUID.randomUUID().toString()).put("state", "preview")
            .put("interfaceId", ref.id).put("interface", ref.name).put("bootId", bootId).put("original", original)
            .put("originalSummary", summary(original)).put("plan", plan).put("targetParcel", backend.target(original.getString("parcel"), plan))
            .put("previewElapsed", elapsed()).put("message", "预览已保存；应用前仍会核对接口和原配置")
        save()
        return status()!!
    }
    private fun requireTransaction(id: String): JSONObject {
        val data = record ?: throw RootFailure("RECOVERY_PENDING", "没有可用的恢复记录")
        if (data.optString("transactionId") != id) throw RootFailure("CONFIG_CONFLICT", "配置事务已经变化，请重新查看")
        return data
    }
    @Synchronized fun apply(id: String, qualificationRun: Boolean = false): JSONObject {
        val data = requireTransaction(id)
        if (data.getString("state") in setOf("awaiting_confirmation", "active")) return status()!!
        if (data.getString("state") != "preview") throw RootFailure("CONFIG_CONFLICT", "此配置不能再次应用")
        if (!backend.qualified && !qualificationRun) throw RootFailure("CAPABILITY_DENIED", backend.reason)
        if (bootId.isBlank() || bootId == "unknown") throw RootFailure("CAPABILITY_DENIED", "无法确认系统启动身份，不能安全应用临时配置")
        if (data.getString("bootId") != bootId || elapsed() - data.getLong("previewElapsed") > 120000) throw RootFailure("CONFIG_CONFLICT", "预览已过期，请重新预览")
        val ref = backend.resolve(data.getString("interfaceId"))
        val current = backend.snapshot(ref)
        val original = data.getJSONObject("original")
        if (current.getString("parcel") != original.getString("parcel") || current.getInt("mtu") != original.getInt("mtu") || current.getBoolean("up") != original.getBoolean("up")) throw RootFailure("CONFIG_CONFLICT", "系统配置已被更改，请重新预览")
        val plan = data.getJSONObject("plan")
        data.put("state", "prepared").put("message", "原配置已保存，准备应用")
        save()
        try {
            data.put("state", "applying"); save()
            backend.apply(ref, data.getString("targetParcel"))
            if (plan.optBoolean("reconnect")) backend.reconnect(ref)
            var applied = backend.waitFor(ref, data.getString("targetParcel"), plan)
            if (plan.has("mtu") && applied.getInt("mtu") != plan.getInt("mtu")) backend.setMtu(ref, plan.getInt("mtu"))
            if (plan.has("routeDestination")) {
                if (applied.isNull("netId")) throw RootFailure("CONFIG_VERIFY_FAILED", "接口尚未加入 Android 网络，不能添加路由")
                val netId = applied.getInt("netId")
                data.put("routeNetId", netId).put("routeBefore", applied.getJSONObject("kernel").getJSONArray("routes")); save()
                backend.route(ref, netId, plan.getString("routeDestination"), plan.optString("routeGateway"), true)
                applied = backend.snapshot(ref)
                val added = routes(applied).filter { it.getString("destination") == plan.getString("routeDestination") && it.optString("gateway") == plan.optString("routeGateway") }
                if (added.size != 1) throw RootFailure("CONFIG_VERIFY_FAILED", "附加路由未得到唯一的内核读回结果")
                data.put("ownedRoute", added.single()); save()
            }
            applied = backend.snapshot(ref)
            if (applied.getString("parcel") != data.getString("targetParcel") || applied.getInt("mtu") != plan.optInt("mtu", original.getInt("mtu"))) throw RootFailure("CONFIG_VERIFY_FAILED", "系统配置或 MTU 读回不一致")
            val lease = plan.getLong("leaseMinutes") * 60000
            data.put("state", "awaiting_confirmation").put("currentSummary", summary(applied))
                .put("confirmElapsed", elapsed() + 30000).put("confirmUntil", wall() + 30000)
                .put("expiresElapsed", elapsed() + lease).put("expiresAt", wall() + lease)
                .put("message", "请在 30 秒内确认；未确认将恢复原配置")
            save()
            return status()!!
        } catch (error: Exception) {
            data.put("state", "apply_failed").put("message", error.message ?: "配置失败"); save()
            restore(id, "应用失败")
            throw RootFailure("CONFIG_APPLY_FAILED", "应用未完成；${published?.optString("message")}")
        }
    }
    @Synchronized fun confirm(id: String): JSONObject {
        val data = requireTransaction(id)
        if (data.getString("state") == "active") return status()!!
        if (data.getString("state") != "awaiting_confirmation" || elapsed() >= data.getLong("confirmElapsed")) {
            restore(id, "确认超时")
            throw RootFailure("CONFIG_CONFLICT", "确认窗口已结束，已开始恢复")
        }
        data.put("state", "active").put("message", "临时配置正在使用，到期或退出后恢复")
        save(); return status()!!
    }
    @Synchronized fun auditActive() {
        val data = record ?: return
        if (data.optString("state") != "active") return
        try {
            val ref = backend.resolve(data.getString("interfaceId"))
            val current = backend.snapshot(ref)
            val plan = data.getJSONObject("plan")
            val original = data.getJSONObject("original")
            if (current.getString("parcel") != data.getString("targetParcel") || current.getInt("mtu") != plan.optInt("mtu", original.getInt("mtu"))) throw RootFailure("CONFIG_CONFLICT", "系统配置已在临时会话外改变")
            if (plan.getString("mode") == "static" && (current.getJSONArray("addresses").length() != 1 || current.getJSONArray("addresses").getString(0) != plan.getString("address"))) throw RootFailure("CONFIG_CONFLICT", "接口地址已有其他变更")
            if (data.has("ownedRoute") && routes(current).none { it.toString() == data.getJSONObject("ownedRoute").toString() }) throw RootFailure("CONFIG_CONFLICT", "临时路由已被其他操作改变")
        } catch (error: Exception) {
            data.put("state", "recovery_pending").put("message", "恢复未完成：${error.message ?: "接口状态改变"}；未覆盖新状态")
            save()
        }
    }
    @Synchronized fun restore(id: String, reason: String = "用户结束"): JSONObject {
        val data = requireTransaction(id)
        if (data.getString("state") == "restored") return status()!!
        if (data.getString("state") == "preview" || data.getString("state") == "prepared") {
            data.put("state", "restored").put("message", "未应用配置，无需恢复"); save(); return status()!!
        }
        data.put("state", "restoring").put("message", "$reason，正在恢复"); save()
        try {
            val ref = backend.resolve(data.getString("interfaceId"))
            var current = backend.snapshot(ref)
            val original = data.getJSONObject("original")
            val plan = data.getJSONObject("plan")
            if (current.getString("parcel") !in setOf(original.getString("parcel"), data.getString("targetParcel"))) throw RootFailure("CONFIG_CONFLICT", "系统已有其他配置，未覆盖它")
            if (current.getInt("mtu") !in setOf(original.getInt("mtu"), plan.optInt("mtu", original.getInt("mtu")))) throw RootFailure("CONFIG_CONFLICT", "MTU 已被其他操作改变，未覆盖它")
            // A direct kernel address change need not change Android's persisted configuration.
            if (plan.getString("mode") == "static" && current.getString("parcel") == data.getString("targetParcel")) {
                val addresses = current.getJSONArray("addresses")
                val prefixes = current.getJSONArray("prefixes")
                val target = addresses.length() == 1 && addresses.getString(0) == plan.getString("address") && prefixes.optInt(0) == plan.getInt("prefix")
                val unchanged = addresses.toString() == original.getJSONArray("addresses").toString() && prefixes.toString() == original.getJSONArray("prefixes").toString()
                if (addresses.length() > 0 && !target && !unchanged) throw RootFailure("CONFIG_CONFLICT", "内核地址已有其他变更，未覆盖它")
            }
            if (data.has("routeNetId")) {
                val matching = routes(current).filter { it.getString("destination") == plan.getString("routeDestination") && it.optString("gateway") == plan.optString("routeGateway") }
                if (matching.isNotEmpty()) {
                    val owned = data.optJSONObject("ownedRoute") ?: throw RootFailure("RECOVERY_PENDING", "路由写入被中断，归属未能确认，请核对系统路由")
                    if (matching.size != 1 || matching.single().toString() != owned.toString()) throw RootFailure("CONFIG_CONFLICT", "路由已有其他变更，未覆盖它")
                    backend.route(ref, data.getInt("routeNetId"), plan.getString("routeDestination"), plan.optString("routeGateway"), false)
                }
            }
            if (current.getString("parcel") != original.getString("parcel")) backend.apply(ref, original.getString("parcel"))
            current = backend.waitFor(ref, original.getString("parcel"), original.getJSONObject("system"))
            if (current.getInt("mtu") != original.getInt("mtu")) backend.setMtu(ref, original.getInt("mtu"))
            current = backend.snapshot(ref)
            if (current.getString("parcel") != original.getString("parcel") || current.getInt("mtu") != original.getInt("mtu")) throw RootFailure("RECOVERY_PENDING", "原配置读回尚未一致")
            if (plan.getString("mode") == "static" && original.getJSONObject("system").getString("mode") == "dhcp") {
                if ((0 until current.getJSONArray("addresses").length()).any { current.getJSONArray("addresses").getString(it) == plan.getString("address") } &&
                    !(0 until original.getJSONArray("addresses").length()).any { original.getJSONArray("addresses").getString(it) == plan.getString("address") }) throw RootFailure("RECOVERY_PENDING", "系统已交还 DHCP，但临时地址仍可见，请继续核对")
            }
            data.put("state", "restored").put("currentSummary", summary(current)).put("message", if (original.getJSONObject("system").getString("mode") == "dhcp") "已恢复 DHCP 管理，地址可能重新分配" else "原配置已恢复并读回核对")
        } catch (error: Exception) {
            data.put("state", "recovery_pending").put("message", "恢复未完成：${error.message ?: "系统调用失败"}")
        }
        save(); return status()!!
    }
    private fun routes(snapshot: JSONObject): List<JSONObject> = snapshot.getJSONObject("kernel").getJSONArray("routes").let { array -> (0 until array.length()).map(array::getJSONObject) }
}
