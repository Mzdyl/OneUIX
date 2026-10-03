package io.github.soclear.oneuix.hook.quickshare

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickShareConfigCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun missingOptionalMethodsAreCachedInsteadOfRepeatedlyScanning() {
        val file = temporaryFolder.newFile()
        var scans = 0
        repeat(3) {
            val config = QuickShareConfigCache.load(file, 10) {
                scans++
                QuickShareConfig(10, nearbyShareSupportedMethod = "nearby")
            }
            assertEquals("nearby", config.nearbyShareSupportedMethod)
            assertNull(config.sepGlobalCheckMethod)
        }
        assertEquals(1, scans)
    }

    @Test
    fun unsupportedVersionIsCachedAndAppUpgradeTriggersNewScan() {
        val file = temporaryFolder.newFile()
        var scans = 0
        for (version in listOf(10L, 10L, 11L, 11L)) {
            QuickShareConfigCache.load(file, version) {
                scans++
                QuickShareConfig(version)
            }
        }
        assertEquals(2, scans)
    }

    @Test
    fun oldSchemaAndCorruptedCacheAreRebuilt() {
        val file = temporaryFolder.newFile()
        for (data in listOf("{broken", """{"versionCode":10,"nearbyShareSupportedMethod":"old"}""")) {
            file.writeText(data)
            val config = QuickShareConfigCache.load(file, 10) { QuickShareConfig(10, nearbyShareSupportedMethod = "new") }
            assertEquals("new", config.nearbyShareSupportedMethod)
        }
    }

    @Test
    fun failedScanIsNotRecordedAsAnUnsupportedVersion() {
        val file = temporaryFolder.newFile()
        assertThrows(IllegalStateException::class.java) {
            QuickShareConfigCache.load(file, 10) { error("Temporary DexKit failure") }
        }
        val config = QuickShareConfigCache.load(file, 10) { QuickShareConfig(10, nearbyShareSupportedMethod = "found") }
        assertEquals("found", config.nearbyShareSupportedMethod)
    }
}
