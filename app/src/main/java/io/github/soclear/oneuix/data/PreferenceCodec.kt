package io.github.soclear.oneuix.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

val PreferenceJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

fun decodePreference(string: String): Preference {
    val root = PreferenceJson.parseToJsonElement(string).jsonObject
    return PreferenceJson.decodeFromJsonElement(
        Preference.serializer(),
        migrateLegacyOtherFields(root)
    )
}

private fun migrateLegacyOtherFields(root: JsonObject): JsonObject {
    val otherMap = (root["other"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()

    fun copyToOther(groupName: String, fieldName: String, otherField: String = fieldName) {
        val group = root[groupName] as? JsonObject ?: return
        val value = group[fieldName] ?: return
        if (otherField !in otherMap) {
            otherMap[otherField] = value
        }
    }

    copyToOther("galaxyStore", "blockGalaxyStoreAds")
    copyToOther("galaxyStore", "changeRegion")
    copyToOther("galaxyStore", "regionCode")
    copyToOther("dualApp", "makeAllUserAppsAvailable")
    copyToOther("weather", "setWeatherProviderCN")
    copyToOther("launcher", "showMemoryUsageInRecents")
    copyToOther("launcher", "hideRecentsCloseAllButton")
    copyToOther("launcher", "hideAppsSearchBar")
    copyToOther("launcher", "removeShortcutBadge")
    copyToOther("browser", "showMorePlaybackSpeeds")
    copyToOther("browser", "spoofBrowserCountryCodeToUS")
    copyToOther("browser", "redirectCustomTab")
    copyToOther("video", "showMorePlaybackSpeeds")
    copyToOther("gallery", "supportAllGallerySettings")
    copyToOther("gallery", "supportSharedAlbumsInHide")
    copyToOther("gallery", "hideVideoEditorStudio")
    copyToOther("notes", "supportAllNotesFeatures")
    copyToOther("calendar", "enableChineseHolidayDisplay")
    copyToOther("messaging", "supportBlockMessage")
    copyToOther("themeCenter", "setThemeTrialNeverExpired")
    copyToOther("photoRetouching", "noAIWatermark")
    copyToOther("photoRetouching", "enableSketch")
    copyToOther("healthMonitor", "bypassHealthMonitorCountryCheck")
    copyToOther("sPen", "useGoogleTranslate", "useSPenGoogleTranslate")
    copyToOther("watchPairing", "bypassRegionCheck", "bypassWatchPairingRegionCheck")
    copyToOther("watchPairing", "connectionMode", "watchPairingConnectionMode")
    copyToOther("watchPairing", "forceChinaGmsCore", "supplementChinaWearOsGms")

    val migrated = root.toMutableMap()
    migrated["other"] = JsonObject(otherMap)
    return JsonObject(migrated)
}
