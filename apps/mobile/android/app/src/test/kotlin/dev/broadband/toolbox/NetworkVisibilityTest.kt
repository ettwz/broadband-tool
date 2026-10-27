package dev.broadband.toolbox

import org.junit.Assert.*
import org.junit.Test

class NetworkVisibilityTest {
    private data class Entry(val id: String, val transport: String, val internet: Boolean)
    private fun visible(vararg entries: Entry) = entries
        .filter { showInNetworkOverview(it.transport, it.internet) }.map { it.id }

    @Test fun singleSimHidesImsButKeepsTheDataNetwork() {
        assertEquals(listOf("data"), visible(
            Entry("ims", "cellular", false), Entry("data", "cellular", true)))
    }

    @Test fun dualSimHidesBothImsNetworksWithoutInventingASimRow() {
        assertEquals(listOf("data"), visible(
            Entry("ims-sim1", "cellular", false), Entry("ims-sim2", "cellular", false),
            Entry("data", "cellular", true)))
    }

    @Test fun twoActualDataNetworksAreNotDeduplicatedByTransport() {
        assertEquals(listOf("data1", "data2"), visible(
            Entry("data1", "cellular", true), Entry("data2", "cellular", true)))
    }

    @Test fun noDataNetworkProducesNoCellularPlaceholder() {
        assertEquals(emptyList<String>(), visible(Entry("ims", "cellular", false)))
    }

    @Test fun localOnlyWifiEthernetAndVpnArePreserved() {
        for (transport in listOf("wifi", "ethernet", "vpn", "other")) {
            assertTrue(showInNetworkOverview(transport, false))
        }
    }
}
