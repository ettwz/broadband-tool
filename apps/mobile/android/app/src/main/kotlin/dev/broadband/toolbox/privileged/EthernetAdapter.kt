package dev.broadband.toolbox.privileged

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress

class EthernetAdapter(private val context: Context) : EthernetBackend {
    private val ipClass = Class.forName("android.net.IpConfiguration")
    private val staticClass = Class.forName("android.net.StaticIpConfiguration")
    private val native = EthernetNative()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var ethernet: Any? = null
    private var netd: Any? = null
    override val reason: String
    init {
        reason = runCatching {
            ethernet = service("ethernet", "android.net.IEthernetManager")
            netd = service("netd", "android.net.INetd")
            Class.forName("android.net.IEthernetManager").getMethod("getConfiguration", String::class.java)
            Class.forName("android.net.IEthernetManager").getMethod("setConfiguration", String::class.java, ipClass)
            "系统配置接口可识别；此设备尚未通过有线恢复验证，保持只读"
        }.getOrElse { "当前系统的有线配置接口不可用，保持只读" }
    }
    val available get() = ethernet != null && netd != null
    // Enabled only after controlled system/kernel/rollback qualification for this ROM.
    override val qualified get() = false
    override fun resolve(id: String) = RootInterfaces.require(id)
    override fun target(original: String, plan: JSONObject): String = encode(planned(decode(original), plan))
    override fun validate(ref: RootInterface, plan: JSONObject, original: JSONObject) {
        if (!ref.up) throw RootFailure("CAPABILITY_DENIED", "请先在系统中启用此接口，再预览临时配置")
        if (ref.addresses.size > 1) throw RootFailure("CAPABILITY_DENIED", "接口含有额外 IPv4 地址，不能保证系统配置恢复完整")
        if (plan.getString("mode") == "static" && RootInterfaces.list().any { it.id != ref.id && plan.getString("address") in it.addresses }) throw RootFailure("CONFIG_CONFLICT", "此地址已用于本机其他接口")
        val knownDestinations = ref.addresses.mapIndexed { index, address ->
            val value = address.split('.').fold(0L) { total, part -> (total shl 8) or part.toLong() }
            val prefix = ref.prefixes[index]
            val network = value and ((0xffffffffL shl (32 - prefix)) and 0xffffffffL)
            val text = (3 downTo 0).joinToString(".") { ((network shr (it * 8)) and 255).toString() }
            "$text/$prefix"
        }.toSet() + "0.0.0.0/0"
        val routes = original.getJSONObject("kernel").getJSONArray("routes")
        if ((0 until routes.length()).map(routes::getJSONObject).any { it.optInt("type") == 1 && it.getString("destination") !in knownDestinations }) throw RootFailure("CAPABILITY_DENIED", "接口已有额外路由，无法保证系统重配后完整恢复，保持只读")
    }
    override fun apply(ref: RootInterface, target: String) = setConfiguration(ref, decode(target))
    override fun reconnect(ref: RootInterface) {
        RootInterfaces.require(ref.id)
        val callback = runCatching { Class.forName("android.net.INetworkInterfaceOutcomeReceiver") }.getOrNull()
            ?: throw RootFailure("CAPABILITY_DENIED", "当前版本不支持按接口重新连接")
        call(ethernet, "disableInterface", arrayOf(String::class.java, callback), ref.name, null)
        try { Thread.sleep(400) } finally { call(ethernet, "enableInterface", arrayOf(String::class.java, callback), ref.name, null) }
    }
    private fun service(name: String, type: String): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name) as? IBinder
            ?: throw RootFailure("CAPABILITY_DENIED", "系统服务不可用：$name")
        return Class.forName("$type\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
    }
    private fun call(target: Any?, name: String, types: Array<Class<*>>, vararg values: Any?): Any? {
        if (target == null) throw RootFailure("CAPABILITY_DENIED", reason)
        try {
            val type = if (target === ethernet) Class.forName("android.net.IEthernetManager") else Class.forName("android.net.INetd")
            return type.getMethod(name, *types).invoke(target, *values)
        }
        catch (error: Exception) { throw RootFailure("SYSTEM_CONFIG_FAILED", error.cause?.message ?: error.message ?: "系统配置调用失败") }
    }
    fun configuration(ref: RootInterface): Any {
        if (!ref.usb || ref.transport != "ethernet") throw RootFailure("CAPABILITY_DENIED", "只允许修改系统识别的 USB 有线接口")
        val known = call(ethernet, "getAvailableInterfaces", emptyArray()) as? Array<*> ?: emptyArray<String>()
        if (ref.name !in known) throw RootFailure("CAPABILITY_DENIED", "Android 有线管理服务尚未识别此接口")
        return call(ethernet, "getConfiguration", arrayOf(String::class.java), ref.name)
            ?: throw RootFailure("CAPABILITY_DENIED", "无法读取原有线配置")
    }
    fun encode(config: Any): String {
        val parcel = Parcel.obtain()
        try { (config as Parcelable).writeToParcel(parcel, 0); return Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP) }
        finally { parcel.recycle() }
    }
    fun decode(value: String): Any {
        if (value.length > 32768) throw RootFailure("RECOVERY_PENDING", "原配置记录无效")
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size); parcel.setDataPosition(0)
            val creator = ipClass.getField("CREATOR").get(null) as Parcelable.Creator<*>
            return creator.createFromParcel(parcel) ?: throw RootFailure("RECOVERY_PENDING", "无法读取原配置记录")
        } finally { parcel.recycle() }
    }
    fun summary(config: Any): JSONObject {
        val mode = ipClass.getMethod("getIpAssignment").invoke(config).toString()
        if (mode !in setOf("DHCP", "STATIC")) throw RootFailure("CAPABILITY_DENIED", "原配置模式未知，无法保证恢复")
        val result = JSONObject().put("mode", mode.lowercase())
        val static = ipClass.getMethod("getStaticIpConfiguration").invoke(config)
        if (static != null) {
            val address = staticClass.getField("ipAddress").get(static) as? LinkAddress
            if (address != null && address.address !is Inet4Address) throw RootFailure("CAPABILITY_DENIED", "原配置包含不支持的静态地址")
            result.put("address", address?.address?.hostAddress ?: "").put("prefix", address?.prefixLength ?: 0)
                .put("gateway", (staticClass.getField("gateway").get(static) as? InetAddress)?.hostAddress ?: "")
            val dns = staticClass.getField("dnsServers").get(static) as? List<*> ?: emptyList<Any>()
            result.put("dns", JSONArray(dns.filterIsInstance<InetAddress>().map { it.hostAddress }))
        }
        return result
    }
    fun planned(original: Any, plan: JSONObject): Any {
        val config = decode(encode(original))
        val assignment = Class.forName("android.net.IpConfiguration\$IpAssignment")
        val mode = if (plan.getString("mode") == "static") "STATIC" else "DHCP"
        ipClass.getMethod("setIpAssignment", assignment).invoke(config, assignment.enumConstants.first { it.toString() == mode })
        val static = if (mode == "STATIC") staticClass.getDeclaredConstructor().newInstance().also {
            val linkAddress = LinkAddress::class.java.getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
                .newInstance(InetAddress.getByName(plan.getString("address")), plan.getInt("prefix"))
            staticClass.getField("ipAddress").set(it, linkAddress)
            staticClass.getField("gateway").set(it, plan.optString("gateway").takeIf(String::isNotEmpty)?.let(InetAddress::getByName))
            @Suppress("UNCHECKED_CAST") val servers = staticClass.getField("dnsServers").get(it) as MutableList<InetAddress>
            servers.clear()
            val dns = plan.getJSONArray("dns")
            for (index in 0 until dns.length()) servers += InetAddress.getByName(dns.getString(index))
        } else null
        ipClass.getMethod("setStaticIpConfiguration", staticClass).invoke(config, static)
        return config
    }
    override fun snapshot(ref: RootInterface): JSONObject {
        val current = RootInterfaces.require(ref.id)
        val config = configuration(current)
        val kernel = JSONObject(native.snapshot(current.index))
        return JSONObject().put("interfaceId", current.id).put("interface", current.name).put("mtu", current.mtu)
            .put("addresses", JSONArray(current.addresses)).put("prefixes", JSONArray(current.prefixes)).put("up", current.up)
            .put("system", summary(config)).put("parcel", encode(config)).put("kernel", kernel)
            .put("netId", network(current)?.toString()?.toIntOrNull() ?: JSONObject.NULL)
    }
    fun network(ref: RootInterface): Network? = connectivity.allNetworks.firstOrNull { connectivity.getLinkProperties(it)?.interfaceName == ref.name }
    fun setConfiguration(ref: RootInterface, config: Any) {
        RootInterfaces.require(ref.id)
        call(ethernet, "setConfiguration", arrayOf(String::class.java, ipClass), ref.name, config)
    }
    override fun setMtu(ref: RootInterface, mtu: Int) {
        RootInterfaces.require(ref.id)
        call(netd, "interfaceSetMtu", arrayOf(String::class.java, Int::class.javaPrimitiveType!!), ref.name, mtu)
    }
    override fun route(ref: RootInterface, netId: Int, destination: String, gateway: String, add: Boolean) {
        RootInterfaces.require(ref.id)
        if (network(ref)?.toString()?.toIntOrNull() != netId) throw RootFailure("INTERFACE_CHANGED", "有线网络标识已变化")
        call(netd, if (add) "networkAddRoute" else "networkRemoveRoute",
            arrayOf(Int::class.javaPrimitiveType!!, String::class.java, String::class.java, String::class.java), netId, ref.name, destination, gateway)
    }
    override fun waitFor(ref: RootInterface, expectedParcel: String, plan: JSONObject?, timeoutMs: Long): JSONObject {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        var last: JSONObject? = null
        do {
            last = snapshot(ref)
            val systemMatches = last.getString("parcel") == expectedParcel
            val addresses = last.getJSONArray("addresses")
            val static = plan?.getString("mode") == "static"
            val ipMatches = !static || (addresses.length() == 1 && addresses.getString(0) == plan!!.getString("address") && last.getJSONArray("prefixes").getInt(0) == plan.getInt("prefix"))
            val routes = last.getJSONObject("kernel").getJSONArray("routes")
            val defaults = (0 until routes.length()).map(routes::getJSONObject).filter { it.optInt("type") == 1 && it.getString("destination") == "0.0.0.0/0" }
            val gatewayMatches = !static || if (plan!!.optString("gateway").isEmpty()) defaults.isEmpty() else defaults.any { it.optString("gateway") == plan.getString("gateway") }
            if (systemMatches && ipMatches && gatewayMatches) return last
            Thread.sleep(150)
        } while (SystemClock.elapsedRealtime() < end)
        throw RootFailure("CONFIG_VERIFY_FAILED", "系统配置或接口地址未按预期生效，正在尝试恢复")
    }
}
