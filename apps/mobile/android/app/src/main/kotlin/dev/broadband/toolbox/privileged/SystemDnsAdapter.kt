package dev.broadband.toolbox.privileged

import android.os.Build
import android.os.IBinder
import android.os.Process
import dev.broadband.toolbox.dnsapi.IDnsResolver
import org.json.JSONObject

/** Frozen AOSP v10 client, with Binder descriptor and remote-version checks. */
class SystemDnsAdapter {
    internal var resolver: IDnsResolver? = null
        private set
    var resolverVersion: Int = 0
        private set
    val reason: String

    init {
        reason = if (Build.VERSION.SDK_INT < 29) {
            "Android 9 未提供此按网络清理接口"
        } else {
            runCatching {
                val binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java).invoke(null, "dnsresolver") as? IBinder
                    ?: throw IllegalStateException("系统 DNS 服务不可用")
                if (binder.interfaceDescriptor != IDnsResolver.DESCRIPTOR) {
                    throw IllegalStateException("系统 DNS 服务接口不匹配")
                }
                val client = IDnsResolver.Stub.asInterface(binder)
                resolverVersion = client.interfaceVersion
                if (resolverVersion < IDnsResolver.VERSION) {
                    throw IllegalStateException("当前系统解析器版本暂不支持")
                }
                if (!client.isAlive) throw IllegalStateException("系统 DNS 服务未就绪")
                resolver = client
                "可清理所选网络的系统解析缓存"
            }.getOrElse { it.message ?: "系统 DNS 清理接口不可用" }
        }
    }

    val available get() = resolver != null

    fun flush(netId: Int): JSONObject {
        if (netId <= 0 || netId > 65535) throw RootFailure("INVALID_INPUT", "无效的网络标识")
        if (Process.myUid() != 0) throw RootFailure("ROOT_MODE_REQUIRED", "需要 Shizuku Root 模式")
        val client = resolver ?: throw RootFailure("CAPABILITY_DENIED", reason)
        try {
            client.flushNetworkCache(netId)
        } catch (error: Exception) {
            throw RootFailure("SYSTEM_DNS_FAILED", error.message ?: "系统拒绝清理请求")
        }
        return JSONObject().put("accepted", true).put("netId", netId)
            .put("scope", "system_network").put("resolverVersion", resolverVersion)
            .put("message", "系统已接受清理请求；不包含应用私有缓存或上游 DNS 缓存")
    }
}
