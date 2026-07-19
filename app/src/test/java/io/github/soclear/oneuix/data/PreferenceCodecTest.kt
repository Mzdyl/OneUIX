package io.github.soclear.oneuix.data

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
              "other": {
                "blockGalaxyStoreAds": false,
                "makeAllUserAppsAvailable": false,
                "setWeatherProviderCN": true,
                "showMemoryUsageInRecents": true,
                "showMorePlaybackSpeeds": true,
                "redirectCustomTab": true,
                "supportAllGallerySettings": false,
                "supportAllNotesFeatures": false,
                "enableChineseHolidayDisplay": true,
                "supportBlockMessage": false,
                "setThemeTrialNeverExpired": false,
                "spoofBrowserCountryCodeToUS": true,
                "noAIWatermark": false,
                "bypassHealthMonitorCountryCheck": true,
                "useSPenGoogleTranslate": true,
                "hideAppsSearchBar": true,
                "removeShortcutBadge": true,
                "bypassWatchPairingRegionCheck": true,
                "watchPairingConnectionMode": 2,
                "supplementChinaWearOsGms": true
              }
            }
            """.trimIndent()
        )

        assertFalse(preference.galaxyStore.blockGalaxyStoreAds)
        assertFalse(preference.dualApp.makeAllUserAppsAvailable)
        assertTrue(preference.weather.setWeatherProviderCN)
        assertTrue(preference.launcher.showMemoryUsageInRecents)
        assertTrue(preference.browser.showMorePlaybackSpeeds)
        assertTrue(preference.video.showMorePlaybackSpeeds)
        assertTrue(preference.browser.redirectCustomTab)
        assertFalse(preference.gallery.supportAllGallerySettings)
        assertFalse(preference.notes.supportAllNotesFeatures)
        assertTrue(preference.calendar.enableChineseHolidayDisplay)
        assertFalse(preference.messaging.supportBlockMessage)
        assertFalse(preference.themeCenter.setThemeTrialNeverExpired)
        assertTrue(preference.browser.spoofBrowserCountryCodeToUS)
        assertFalse(preference.photoRetouching.noAIWatermark)
        assertTrue(preference.healthMonitor.bypassHealthMonitorCountryCheck)
        assertTrue(preference.sPen.useGoogleTranslate)
        assertTrue(preference.launcher.hideAppsSearchBar)
        assertTrue(preference.launcher.removeShortcutBadge)
        assertTrue(preference.watchPairing.bypassRegionCheck)
        assertEquals(2, preference.watchPairing.connectionMode)
        assertTrue(preference.watchPairing.forceChinaGmsCore)
    }

    @Test
    fun currentFieldsTakePriorityOverLegacyFields() {
        val preference = decodePreference(
            """
            {
              "browser": { "showMorePlaybackSpeeds": false },
              "other": { "showMorePlaybackSpeeds": true }
            }
            """.trimIndent()
        )

        assertFalse(preference.browser.showMorePlaybackSpeeds)
        assertTrue(preference.video.showMorePlaybackSpeeds)
    }
}
