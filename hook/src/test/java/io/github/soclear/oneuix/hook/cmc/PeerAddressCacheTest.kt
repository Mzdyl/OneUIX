package io.github.soclear.oneuix.hook.cmc

import org.junit.Assert.*
import org.junit.Test

class PeerAddressCacheTest {
    @Test
    fun manualAddressWinsAndInvalidManualAddressDoesNotPickAnotherPeer() {
        val cache = PeerAddressCache()
        cache.remember("phone", "10.0.0.2")
        assertEquals("10.0.0.3", cache.resolve("10.0.0.3", "phone"))
        assertNull(cache.resolve("300.1.1.1"))
        assertNull(cache.resolve("10.0.0.1", localAddress = "10.0.0.1"))
    }

    @Test
    fun deviceLookupDoesNotUseAnotherDevicesAddress() {
        val cache = PeerAddressCache()
        cache.remember("urn:duid:phone", "10.0.0.2")
        cache.remember("tablet", "10.0.0.3")
        assertEquals("10.0.0.2", cache.resolve(deviceId = "phone"))
        assertEquals("10.0.0.3", cache.resolve(deviceId = "urn:duid:tablet"))
        assertNull(cache.resolve(deviceId = "unknown"))
        assertNull(cache.resolve())
        assertEquals("10.0.0.3", cache.resolve(currentAddress = "10.0.0.3"))
    }

    @Test
    fun changedAddressReplacesOldAddressAndExpiresWithoutRefresh() {
        var now = 0L
        val cache = PeerAddressCache(nowMillis = { now }, ttlMillis = 100)
        cache.remember("phone", "10.0.0.2")
        now = 50
        cache.remember("phone", "10.0.0.3")
        now = 100
        assertEquals(listOf("10.0.0.3"), cache.addresses())
        now = 150
        assertNull(cache.resolve(deviceId = "phone"))
        assertTrue(cache.addresses().isEmpty())
    }

    @Test
    fun oldPersonalAddressesAreTreatedLikeAnyOtherPeer() {
        val cache = PeerAddressCache()
        cache.remember("phone", "10.0.0.120")
        assertEquals("10.0.0.120", cache.resolve())
        cache.remember("phone", "10.0.0.150")
        assertEquals("10.0.0.150", cache.resolve())
    }

    @Test
    fun unrelatedArpHostsCannotBecomePeersAndInvalidAddressesAreIgnored() {
        val cache = PeerAddressCache()
        assertNull(cache.resolve(currentAddress = "10.0.0.99"))
        for (ip in listOf("0.0.0.0", "127.0.0.2", "224.0.0.1", "255.255.255.255", "1.2.3.999", "host", "::1")) {
            cache.remember(null, ip)
        }
        assertTrue(cache.addresses().isEmpty())
        cache.remember(null, "10.0.0.2")
        assertEquals("10.0.0.2", cache.resolve())
        cache.remember("phone", "10.0.0.2")
        cache.remember("phone", "10.0.0.3")
        assertEquals(listOf("10.0.0.3"), cache.addresses())
    }

    @Test
    fun capacityEvictsLeastRecentlySeenPeer() {
        var now = 0L
        val cache = PeerAddressCache(nowMillis = { now }, capacity = 2)
        cache.remember("a", "10.0.0.1")
        now++
        cache.remember("b", "10.0.0.2")
        now++
        cache.remember("a", "10.0.0.1")
        now++
        cache.remember("c", "10.0.0.3")
        assertNull(cache.resolve(deviceId = "b"))
        assertEquals("10.0.0.1", cache.resolve(deviceId = "a"))
    }
}
