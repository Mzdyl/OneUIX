package io.github.soclear.oneuix.data

import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.PreferenceJson
import io.github.soclear.oneuix.common.SPenTranslationSource
import io.github.soclear.oneuix.common.decodePreference
import io.github.soclear.oneuix.common.decodeStoredPreference
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceCodecTest {
    @Test
    fun migratesLegacyOtherFields() {
        val preference = decodePreference(
            """
            {
              "galaxyStore": {
                "blockGalaxyStoreAds": false,
                "changeRegion": true,
                "regionCode": "HK"
              },
              "dualApp": { "makeAllUserAppsAvailable": false },
              "weather": { "setWeatherProviderCN": true },
              "launcher": {
                "showMemoryUsageInRecents": true,
                "hideRecentsCloseAllButton": true,
                "hideAppsSearchBar": true,
                "removeShortcutBadge": true
              },
              "browser": {
                "showMorePlaybackSpeeds": true,
                "redirectCustomTab": true,
                "spoofBrowserCountryCodeToUS": true
              },
              "video": { "showMorePlaybackSpeeds": true },
              "gallery": {
                "supportAllGallerySettings": false,
                "supportSharedAlbumsInHide": true,
                "hideVideoEditorStudio": true
              },
              "notes": { "supportAllNotesFeatures": false },
              "calendar": { "enableChineseHolidayDisplay": true },
              "messaging": { "supportBlockMessage": false },
              "themeCenter": { "setThemeTrialNeverExpired": false },
              "photoRetouching": {
                "noAIWatermark": false,
                "enableSketch": true
              },
              "healthMonitor": { "bypassHealthMonitorCountryCheck": true },
              "sPen": { "useGoogleTranslate": true },
              "watchPairing": {
                "bypassRegionCheck": true,
                "connectionMode": 2,
                "forceChinaGmsCore": true
              }
            }
            """.trimIndent()
        )

        assertFalse(preference.other.blockGalaxyStoreAds)
        assertTrue(preference.other.changeRegion)
        assertEquals("HK", preference.other.regionCode)
        assertFalse(preference.other.makeAllUserAppsAvailable)
        assertTrue(preference.other.setWeatherProviderCN)
        assertTrue(preference.other.showMemoryUsageInRecents)
        assertTrue(preference.other.hideRecentsCloseAllButton)
        assertTrue(preference.other.hideAppsSearchBar)
        assertTrue(preference.other.removeShortcutBadge)
        assertTrue(preference.other.showMorePlaybackSpeeds)
        assertTrue(preference.other.redirectCustomTab)
        assertTrue(preference.other.spoofBrowserCountryCodeToUS)
        assertFalse(preference.other.supportAllGallerySettings)
        assertTrue(preference.other.supportSharedAlbumsInHide)
        assertTrue(preference.other.hideVideoEditorStudio)
        assertFalse(preference.other.supportAllNotesFeatures)
        assertTrue(preference.other.enableChineseHolidayDisplay)
        assertFalse(preference.other.supportBlockMessage)
        assertFalse(preference.other.setThemeTrialNeverExpired)
        assertFalse(preference.other.noAIWatermark)
        assertTrue(preference.other.enableSketch)
        assertTrue(preference.other.bypassHealthMonitorCountryCheck)
        assertEquals(SPenTranslationSource.GOOGLE, preference.other.sPenTranslationSource)
        assertTrue(preference.other.bypassWatchPairingRegionCheck)
        assertEquals(2, preference.other.watchPairingConnectionMode)
        assertTrue(preference.other.supplementChinaWearOsGms)
    }

    @Test
    fun otherFieldsTakePriorityOverLegacyFields() {
        val preference = decodePreference(
            """
            {
              "browser": { "showMorePlaybackSpeeds": false },
              "other": { "showMorePlaybackSpeeds": true }
            }
            """.trimIndent()
        )

        assertTrue(preference.other.showMorePlaybackSpeeds)
    }

    @Test
    fun legacyCmcSwitchEnablesGranularOptions() {
        val preference = decodePreference("""{"call":{"supportCallAndTextOnOtherDevices":true}}""")

        assertTrue(preference.call.bypassSameWifiRestriction)
        assertTrue(preference.call.unlockCmcMobileNetwork)
        assertTrue(preference.call.bypassChinaSimRestriction)
    }

    @Test
    fun explicitCmcChoicesSurviveLegacyMigration() {
        val preference = decodePreference(
            """{"call":{"supportCallAndTextOnOtherDevices":true,"bypassSameWifiRestriction":false,"unlockCmcMobileNetwork":false,"bypassChinaSimRestriction":false,"mdecDeviceType":2}}"""
        )

        assertFalse(preference.call.bypassSameWifiRestriction)
        assertFalse(preference.call.unlockCmcMobileNetwork)
        assertFalse(preference.call.bypassChinaSimRestriction)
        assertEquals(2, preference.call.mdecDeviceType)
    }

    @Test
    fun selfFeaturesSurviveBackupRoundTrip() {
        val preference = decodePreference(
            """
            {
              "bixby":{"injectModel":true,"labsMgr":true,"wwvBypass":true},
              "call":{"mdecDeviceType":2,"enableVirtualLanP2p":true,"virtualLanPeerIp":"10.0.0.160"},
              "other":{"useSPenGoogleTranslate":true,"bypassWatchPairingRegionCheck":true,"watchPairingConnectionMode":2,"supportGalleryGoogleSync":true,"enableGoogleQuickShare":true},
              "nfc":{"enableSimulation":false,"activeUid":"01:02:03:04"},
              "futureUpstreamOption":true
            }
            """.trimIndent()
        )
        val backup = io.github.soclear.oneuix.common.IgnoreUnknownKeysJson.encodeToString(
            Preference.serializer(), preference
        )

        assertEquals(preference, decodePreference(backup))
        assertTrue(preference.bixby.labsMgr)
        assertTrue(preference.call.enableVirtualLanP2p)
        assertEquals(SPenTranslationSource.GOOGLE, preference.other.sPenTranslationSource)
        assertEquals("01:02:03:04", preference.nfc.activeUid)
    }


    @Test
    fun migratesEnabledSPenSwitch() {
        val preference = decodePreference("""{"other":{"useSPenGoogleTranslate":true}}""")
        assertEquals(SPenTranslationSource.GOOGLE, preference.other.sPenTranslationSource)
    }

    @Test
    fun absentAndDisabledSPenSwitchesKeepSystemDefault() {
        for (json in listOf(
            "{}",
            """{"other":{"useSPenGoogleTranslate":false}}""",
            """{"sPen":{"useGoogleTranslate":false}}""",
            """{"sPen":{"useGoogleTranslate":true},"other":{"useSPenGoogleTranslate":false}}"""
        )) {
            assertEquals(SPenTranslationSource.DEFAULT, decodePreference(json).other.sPenTranslationSource)
        }
    }

    @Test
    fun explicitSPenChoiceIncludingDefaultOverridesLegacySwitches() {
        for (source in SPenTranslationSource.entries) {
            val preference = decodePreference(
                """{
                    "sPen":{"useGoogleTranslate":true},
                    "other":{"useSPenGoogleTranslate":true,"sPenTranslationSource":"${source.name}"}
                }"""
            )
            assertEquals(source, preference.other.sPenTranslationSource)
        }
    }

    @Test
    fun translationChoicesSurviveSaveAndReloadWithoutLegacyKeys() {
        val migrated = decodePreference("""{"other":{"useSPenGoogleTranslate":true}}""")
        for (source in SPenTranslationSource.entries) {
            val preference = migrated.copy(other = migrated.other.copy(sPenTranslationSource = source))
            for (codec in listOf(PreferenceJson, io.github.soclear.oneuix.common.IgnoreUnknownKeysJson)) {
                val json = codec.encodeToString(Preference.serializer(), preference)
                assertFalse(json.contains("useSPenGoogleTranslate"))
                assertEquals(preference, decodePreference(json))
            }
        }
    }

    @Test
    fun unknownTranslationChoiceFallsBackToSystemDefault() {
        val preference = decodePreference(
            """{"other":{"sPenTranslationSource":"FUTURE","useSPenGoogleTranslate":true}}"""
        )
        assertEquals(SPenTranslationSource.DEFAULT, preference.other.sPenTranslationSource)
    }

    @Test
    fun savedDefaultsAreValidRemotePreferencesRatherThanMissingData() {
        val json = io.github.soclear.oneuix.common.IgnoreUnknownKeysJson.encodeToString(
            Preference.serializer(), Preference()
        )
        assertEquals("{}", json)
        assertEquals(Preference(), decodeStoredPreference(json))
        assertEquals(Preference(), decodeStoredPreference("  { }  "))
    }

    @Test
    fun onlyBlankStoredPreferencesAreMissingAndCorruptionRemainsAnError() {
        assertNull(decodeStoredPreference(""))
        assertNull(decodeStoredPreference(" \n\t"))
        assertThrows(SerializationException::class.java) { decodeStoredPreference("{broken") }
    }
}
