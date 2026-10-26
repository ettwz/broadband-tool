package dev.broadband.toolbox

import dev.broadband.toolbox.privileged.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class EthernetTransactionsTest {
    private lateinit var folder: File
    private lateinit var backend: FakeEthernet
    private lateinit var manager: EthernetTransactions
    private var now = 1000L
    private val journal get() = File(folder, "recovery.json")
    @Before fun setUp() {
        folder = java.nio.file.Files.createTempDirectory("ethernet-transactions-").toFile()
        backend = FakeEthernet(); now = 1000
        manager = create()
    }
    private fun create() = EthernetTransactions(backend, journal, { now }, { 1000000 + now }, "boot-test")
    @After fun clean() { folder.deleteRecursively() }
    private fun plan() = JSONObject().put("mode", "static").put("address", "192.0.2.22").put("prefix", 24)
        .put("gateway", "").put("dns", JSONArray()).put("leaseMinutes", 1).put("mtu", 1400)
    private fun preview(p: JSONObject = plan()): String = manager.preview("usb-1", p).getString("transactionId")
    @Test fun unqualifiedDeviceCanPreviewButCannotWrite() {
        backend.ready = false
        val id = preview()
        assertTrue(journal.isFile)
        assertEquals(0, backend.writes)
        val error = assertThrows(RootFailure::class.java) { manager.apply(id) }
        assertEquals("CAPABILITY_DENIED", error.code)
        assertEquals(0, backend.writes)
    }
    @Test fun unconfirmedChangeRestoresDhcpAndOriginalMtu() {
        val id = preview(); manager.apply(id)
        assertEquals("awaiting_confirmation", manager.status()!!.getString("state"))
        assertEquals(1400, backend.mtu)
        now += 30001
        assertTrue(manager.needsRestore())
        manager.restore(id, "确认超时")
        assertEquals("restored", manager.status()!!.getString("state"))
        assertEquals("dhcp", backend.config.getString("mode"))
        assertEquals(1500, backend.mtu)
        assertEquals("192.0.2.77", backend.address)
    }
    @Test fun confirmationDoesNotExtendLeaseAndExpiryRestores() {
        val id = preview(); manager.apply(id); manager.confirm(id)
        val expiry = manager.status()!!.getLong("expiresElapsed")
        now += 5000; manager.confirm(id)
        assertEquals(expiry, manager.status()!!.getLong("expiresElapsed"))
        now = expiry + 1; assertTrue(manager.needsRestore())
        manager.restore(id, "会话到期")
        assertEquals("restored", manager.status()!!.getString("state"))
    }
    @Test fun externalConfigurationIsNotOverwrittenDuringRestore() {
        val id = preview(); manager.apply(id); manager.confirm(id)
        backend.config = JSONObject().put("mode", "static").put("address", "192.0.2.99").put("prefix", 24)
        val writes = backend.writes
        manager.restore(id)
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertEquals(writes, backend.writes)
        assertEquals("192.0.2.99", backend.config.getString("address"))
        assertThrows(RootFailure::class.java) { preview() }
    }
    @Test fun sameInterfaceNameWithDifferentIdentityCannotReceiveOldSnapshot() {
        val id = preview(); manager.apply(id)
        backend.identity = "replacement-usb"
        val writes = backend.writes
        manager.restore(id)
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertEquals(writes, backend.writes)
    }
    @Test fun processRestartReadsJournalAndVerifiesActualStateBeforeRestoring() {
        val id = preview(); manager.apply(id); manager.confirm(id)
        manager = create()
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        manager.restore(id)
        assertEquals("restored", manager.status()!!.getString("state"))
        assertEquals("dhcp", backend.config.getString("mode"))
    }
    @Test fun brokenRecoveryRecordFreezesNewConfiguration() {
        journal.writeText("broken")
        manager = create()
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertThrows(RootFailure::class.java) { preview() }
        assertEquals(0, backend.writes)
        assertEquals("broken", journal.readText())
    }
    @Test fun routeRestoreRemovesOnlyTheOwnedRoute() {
        val p = plan().put("routeDestination", "198.51.100.0/24").put("routeGateway", "")
        val id = preview(p); manager.apply(id)
        backend.routes.add(FakeEthernet.routeJson("203.0.113.0/24", ""))
        manager.restore(id)
        assertEquals("restored", manager.status()!!.getString("state"))
        assertEquals(listOf("203.0.113.0/24"), backend.routes.map { it.getString("destination") })
    }
    @Test fun partialApplyFailureRestoresBeforeReportingFailure() {
        val id = preview(); backend.failMtuOnce = true
        assertThrows(RootFailure::class.java) { manager.apply(id) }
        assertEquals("restored", manager.status()!!.getString("state"))
        assertEquals("dhcp", backend.config.getString("mode"))
    }
    @Test fun interruptedRouteWriteKeepsAmbiguousRecoveryPending() {
        val id = preview(plan().put("routeDestination", "198.51.100.0/24"))
        backend.failRouteAfterWrite = true
        assertThrows(RootFailure::class.java) { manager.apply(id) }
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertEquals(1, backend.routes.size)
    }
    @Test fun previewRejectsOffSubnetGatewayAndDirectedBroadcast() {
        assertThrows(RootFailure::class.java) { preview(plan().put("gateway", "198.51.100.1")) }
        assertThrows(RootFailure::class.java) { preview(plan().put("address", "192.0.2.255")) }
        assertEquals(0, backend.writes)
    }
    @Test fun activeAuditMarksInterfaceChangeWithoutWritingToReplacement() {
        val id = preview(); manager.apply(id); manager.confirm(id)
        backend.identity = "new-usb"
        val writes = backend.writes
        manager.auditActive()
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertEquals(writes, backend.writes)
    }
    @Test fun directKernelAddressChangeIsNotOverwritten() {
        val id = preview(); manager.apply(id); manager.confirm(id)
        backend.address = "192.0.2.99"
        val writes = backend.writes
        manager.restore(id)
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
        assertEquals(writes, backend.writes)
    }
    @Test fun missingOwnedRouteEndsTheActiveState() {
        val id = preview(plan().put("routeDestination", "198.51.100.0/24"))
        manager.apply(id); manager.confirm(id); backend.routes.clear()
        manager.auditActive()
        assertEquals("recovery_pending", manager.status()!!.getString("state"))
    }
    private class FakeEthernet : EthernetBackend {
        var ready = true
        override val qualified get() = ready
        override val reason = "test backend qualification"
        var config = JSONObject().put("mode", "dhcp")
        var address = "192.0.2.10"
        var mtu = 1500
        var identity = "usb-1"
        var writes = 0
        var failMtuOnce = false
        var failRouteAfterWrite = false
        val routes = mutableListOf<JSONObject>()
        override fun resolve(id: String): RootInterface {
            if (id != identity) throw RootFailure("INTERFACE_CHANGED", "different interface")
            return RootInterface(id, "eth1", 17, byteArrayOf(2, 3, 4, 5, 6, 7), listOf(address), listOf(24), true, mtu, "ethernet", true, true)
        }
        override fun snapshot(ref: RootInterface): JSONObject {
            resolve(ref.id)
            return JSONObject().put("system", config).put("parcel", config.toString()).put("mtu", mtu).put("up", true)
                .put("addresses", JSONArray(listOf(address))).put("prefixes", JSONArray(listOf(24))).put("netId", 123)
                .put("kernel", JSONObject().put("minMtu", 576).put("maxMtu", 9000).put("routes", JSONArray(routes)))
        }
        override fun target(original: String, plan: JSONObject): String = JSONObject().put("mode", plan.getString("mode")).also {
            if (plan.getString("mode") == "static") it.put("address", plan.getString("address")).put("prefix", plan.getInt("prefix"))
        }.toString()
        override fun apply(ref: RootInterface, target: String) {
            resolve(ref.id); writes++; config = JSONObject(target)
            address = if (config.getString("mode") == "static") config.getString("address") else "192.0.2.77"
        }
        override fun waitFor(ref: RootInterface, expectedParcel: String, plan: JSONObject?, timeoutMs: Long): JSONObject {
            if (config.toString() != expectedParcel) throw RootFailure("CONFIG_VERIFY_FAILED", "mismatch")
            return snapshot(ref)
        }
        override fun setMtu(ref: RootInterface, mtu: Int) {
            if (failMtuOnce) { failMtuOnce = false; throw RootFailure("CONFIG_VERIFY_FAILED", "driver rejected MTU") }
            writes++; this.mtu = mtu
        }
        override fun route(ref: RootInterface, netId: Int, destination: String, gateway: String, add: Boolean) {
            writes++
            if (add) routes.add(routeJson(destination, gateway)) else routes.removeAll { it.getString("destination") == destination && it.getString("gateway") == gateway }
            if (add && failRouteAfterWrite) throw RootFailure("PRIVILEGE_LOST", "reply was lost after write")
        }
        override fun reconnect(ref: RootInterface) { writes++ }
        companion object {
            fun routeJson(destination: String, gateway: String) = JSONObject().put("destination", destination).put("gateway", gateway).put("table", 1017).put("type", 1)
        }
    }
}
