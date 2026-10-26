package dev.broadband.toolbox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address
import java.net.NetworkInterface

@Suppress("DEPRECATION", "MissingPermission")
class NetworkRegistry(
    private val context: Context,
    private val changed: () -> Unit,
    private val invalidated: (String, String) -> Unit,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val networks = linkedMapOf<String, Pair<Network, NetworkData>>()
    private val signatures = mutableMapOf<String, String>()
    private var serial = 0L
    private var explicitlySelected = false
    var selectedId: String? = null
        private set
    var accessPoints: List<WifiAccessPointData> = emptyList()
        private set
    var scanStatus = "idle"
        private set
    var scanUpdatedAt = 0L
        private set
    private var lastScanMicros = 0L
    private var lastRequestedAt = -30_000L
    private var disposed = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) readScan()
                else { scanStatus = "SCAN_THROTTLED"; changed() }
            } else refresh()
        }
    }

    init {
        manager.registerNetworkCallback(NetworkRequest.Builder().clearCapabilities().build(), callback, main)
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        refresh()
    }

    fun permissions() = PermissionData(
        fineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        locationEnabled = location.isLocationEnabled,
        wifiEnabled = wifi?.isWifiEnabled == true,
        localNetwork = true, // targetSdk 36: Android 17's target-37 permission is not requested.
    )

    fun refresh() {
        if (disposed) return
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { refresh() }; return }
        val lost = mutableListOf<Pair<String, String>>()
        synchronized(lock) {
            val available = manager.allNetworks.toList()
            val ids = available.map { it.networkHandle.toString() }.toSet()
            for (id in networks.keys.toList()) if (id !in ids) {
                networks.remove(id); signatures.remove(id)
                if (selectedId == id) selectedId = null
                lost += id to "NETWORK_LOST"
            }
            for (network in available) {
                val capabilities = manager.getNetworkCapabilities(network) ?: continue
                val properties = manager.getLinkProperties(network) ?: continue
                val id = network.networkHandle.toString()
                val addresses = properties.linkAddresses.filter { it.address is Inet4Address }
                val gateway = properties.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                    ?.gateway?.hostAddress
                val name = properties.interfaceName ?: "未知接口"
                val ipv4Routes = properties.routes.filter { it.destination.address is Inet4Address }.map { it.toString() }.sorted()
                val signature = "$name|${addresses.joinToString()}|${properties.dnsServers}|$ipv4Routes|${properties.httpProxy}"
                val old = networks[id]?.second
                val generation = if (signatures[id] == signature && old != null) old.generation else ++serial
                if (old != null && old.generation != generation) lost += id to "NETWORK_CHANGED"
                signatures[id] = signature
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    else -> "other"
                }
                val info = if (transport == "wifi") {
                    (if (Build.VERSION.SDK_INT >= 29) capabilities.transportInfo as? WifiInfo else null)
                        ?: runCatching { wifi?.connectionInfo }.getOrNull()
                } else null
                val ssid = info?.ssid?.removeSurrounding("\"")?.takeUnless { it == "<unknown ssid>" }
                val bssid = info?.bssid?.takeUnless { it == "02:00:00:00:00:00" }
                val mtu = (if (Build.VERSION.SDK_INT >= 29) properties.mtu.takeIf { it > 0 } else null) ?: runCatching {
                    NetworkInterface.getByName(name)?.mtu?.takeIf { it > 0 }
                }.getOrNull()
                networks[id] = network to NetworkData(
                    id, generation, transport, name,
                    ssid ?: when (transport) {
                        "wifi" -> "Wi-Fi"; "ethernet" -> "有线网卡"; "vpn" -> "VPN"; "cellular" -> "蜂窝网络"; else -> name
                    },
                    addresses.mapNotNull { it.address.hostAddress }, addresses.map { it.prefixLength.toLong() },
                    properties.dnsServers.mapNotNull { it.hostAddress }, network == manager.activeNetwork,
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), transport == "vpn",
                    gateway, mtu?.toLong(), ssid, bssid, info?.rssi?.toLong(),
                    info?.frequency?.takeIf { it > 0 }?.toLong(), info?.linkSpeed?.takeIf { it > 0 }?.toLong(),
                )
            }
            if (selectedId == null && !explicitlySelected) {
                selectedId = networks.values.firstOrNull { it.second.transport == "wifi" }?.second?.id
            }
        }
        for ((id, reason) in lost) invalidated(id, reason)
        changed()
    }

    fun data(): List<NetworkData> = synchronized(lock) { networks.values.map { it.second } }

    fun select(id: String) {
        synchronized(lock) {
            val value = networks[id]?.second ?: throw FlutterError("NETWORK_LOST", "该网络已断开")
            if (value.transport !in setOf("wifi", "ethernet")) throw FlutterError("UNSUPPORTED", "请选择 Wi-Fi 或有线网络")
            explicitlySelected = true
            selectedId = id
        }
        changed()
    }

    fun requireNetwork(id: String?, generation: Long?): Pair<Network, NetworkData> = synchronized(lock) {
        val pair = networks[id] ?: throw FlutterError("NETWORK_LOST", "请先选择可用网络")
        if (pair.second.generation != generation) throw FlutterError("NETWORK_CHANGED", "网络配置已改变，请重新开始")
        if (pair.second.addresses.isEmpty()) throw FlutterError("NO_IPV4", "所选网络尚未取得 IPv4 地址")
        if (pair.second.transport !in setOf("wifi", "ethernet")) throw FlutterError("UNSUPPORTED", "不使用蜂窝网络自动回退")
        if (manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            throw FlutterError("VPN_ROUTE_UNVERIFIED", "检测到 VPN，当前无法确认普通工具的指定接口路径；请关闭 VPN 后重试")
        }
        pair
    }

    fun requestScan() {
        val permission = permissions()
        if (!permission.fineLocation) throw FlutterError("PERMISSION_DENIED", "扫描 Wi-Fi 需要精确定位权限")
        if (!permission.locationEnabled) throw FlutterError("LOCATION_DISABLED", "请开启系统定位服务后扫描")
        if (!permission.wifiEnabled) throw FlutterError("WIFI_DISABLED", "请先开启 Wi-Fi")
        val now = SystemClock.elapsedRealtime()
        if (now - lastRequestedAt < 30_000) {
            scanStatus = "SCAN_THROTTLED"; changed(); return
        }
        lastRequestedAt = now
        scanStatus = if (wifi?.startScan() == true) "scanning" else "SCAN_THROTTLED"
        changed()
    }

    private fun readScan() {
        val permission = permissions()
        if (!permission.fineLocation || !permission.locationEnabled) return
        val results = try { wifi?.scanResults.orEmpty() } catch (_: SecurityException) {
            scanStatus = "PERMISSION_DENIED"; changed(); return
        }
        val latest = results.maxOfOrNull { it.timestamp } ?: 0
        val fresh = latest > lastScanMicros
        if (fresh) {
            lastScanMicros = latest
            scanUpdatedAt = System.currentTimeMillis() - ((SystemClock.elapsedRealtime() * 1000 - latest).coerceAtLeast(0) / 1000)
        }
        val incoming = results.map { result ->
            val width = when (result.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20L
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40L
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80L
                ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160L
                ScanResult.CHANNEL_WIDTH_320MHZ -> 320L
                else -> 0L
            }
            WifiAccessPointData(result.SSID.ifBlank { "隐藏网络" }, result.BSSID ?: "", result.frequency.toLong(),
                result.centerFreq0.toLong(), result.centerFreq1.toLong(), width, result.level.toLong(),
                result.timestamp, result.capabilities ?: "未知")
        }
        val cutoff = SystemClock.elapsedRealtime() * 1000 - 600000000L
        val retained = accessPoints.filter { it.timestampMicros > 0 && it.timestampMicros >= cutoff }.associateBy { it.bssid }.toMutableMap()
        incoming.filter { it.bssid.isNotEmpty() }.forEach { retained[it.bssid] = it }
        accessPoints = retained.values.sortedByDescending { it.rssi }
        scanStatus = if (fresh) "ready" else "stale"
        changed()
    }

    fun dispose() {
        disposed = true
        runCatching { manager.unregisterNetworkCallback(callback) }
        runCatching { context.unregisterReceiver(receiver) }
    }
}
