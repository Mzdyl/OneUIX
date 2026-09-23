package io.github.soclear.oneuix.data

import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.decodePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(preference.other.useSPenGoogleTranslate)
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
}
