package dev.broadband.toolbox

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InputRulesTest {
    @Test fun traceAcceptsHostnamesAndIpv4ThroughTheNativeRequestValidator() {
        for (host in listOf("example.com", "Example.COM.", "例子.测试", "localhost", "192.168.1.1")) {
            TaskValidation.validate("trace", JSONObject().put("host", host))
        }
        assertEquals("example.com", InputRules.host(" Example.COM. "))
    }

    @Test fun traceRejectsUrlsIpv6AndInvalidAddressesBeforeResolution() {
        for (host in listOf("https://example.com", "example.com:443", "2001:db8::1", "999.1.1.1", "224.1.1.1", "example.com;id", "a\nb.test")) {
            val error = assertThrows(FlutterError::class.java) { TaskValidation.validate("trace", JSONObject().put("host", host)) }
            assertEquals("INVALID_INPUT", error.code)
        }
    }

    @Test fun acceptsLocalAndPublicIpv4WithoutDnsResolution() {
        assertEquals("192.168.1.1", InputRules.ipv4("192.168.1.1").hostAddress)
        assertEquals("127.0.0.1", InputRules.ipv4("127.0.0.1").hostAddress)
        assertEquals("1.1.1.1", InputRules.host("1.1.1.1"))
    }

    @Test fun rejectsAmbiguousAddressesAndCommandFragments() {
        for (value in listOf("192.168.01.1", "999.1.1.1", "0.0.0.0", "0.1.2.3", "224.1.1.1", "255.255.255.255", "192.0.2.1;id", "192.0.2.1\nwhoami", "１２.1.1.1")) {
            try { InputRules.ipv4(value); fail("Accepted invalid IPv4: $value") }
            catch (error: FlutterError) { assertEquals("INVALID_INPUT", error.code) }
        }
        for (value in listOf("example.com;id", "http://example.com", "example.com:80", "a\u0000.test")) {
            try { InputRules.host(value); fail("Accepted invalid host") }
            catch (error: FlutterError) { assertEquals("INVALID_INPUT", error.code) }
        }
    }

    @Test fun integerValidationDoesNotSilentlyTruncateFractionsOrOverflow() {
        assertEquals(5201, InputRules.integer(JSONObject().put("port", "5201"), "port", 5201, 1, 65535))
        for (value in listOf<Any>(0, -1, 65536, 1.5, "9999999999999999999999")) {
            try { InputRules.integer(JSONObject().put("port", value), "port", 5201, 1, 65535); fail("Accepted invalid port") }
            catch (error: FlutterError) { assertEquals("INVALID_INPUT", error.code) }
        }
    }
}
