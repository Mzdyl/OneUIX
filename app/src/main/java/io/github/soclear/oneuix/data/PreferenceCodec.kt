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
    val legacy = root["other"] as? JsonObject ?: return root
    val migrated = root.toMutableMap()

    fun move(legacyField: String, groupName: String, fieldName: String = legacyField) {
        val value = legacy[legacyField] ?: return
        val group = (migrated[groupName] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        if (fieldName !in group) {
            group[fieldName] = value
            migrated[groupName] = JsonObject(group)
        }
    }

    move("blockGalaxyStoreAds", "galaxyStore")
    move("makeAllUserAppsAvailable", "dualApp")
    move("setWeatherProviderCN", "weather")
    move("showMemoryUsageInRecents", "launcher")
    move("showMorePlaybackSpeeds", "browser")
    move("showMorePlaybackSpeeds", "video")
    move("redirectCustomTab", "browser")
    move("supportAllGallerySettings", "gallery")
    move("supportAllNotesFeatures", "notes")
    move("enableChineseHolidayDisplay", "calendar")
    move("supportBlockMessage", "messaging")
    move("setThemeTrialNeverExpired", "themeCenter")
    move("spoofBrowserCountryCodeToUS", "browser")
    move("noAIWatermark", "photoRetouching")
    move("bypassHealthMonitorCountryCheck", "healthMonitor")
    move("useSPenGoogleTranslate", "sPen", "useGoogleTranslate")
    move("hideAppsSearchBar", "launcher")
    move("removeShortcutBadge", "launcher")
    move("bypassWatchPairingRegionCheck", "watchPairing", "bypassRegionCheck")
    move("watchPairingConnectionMode", "watchPairing", "connectionMode")
    move("supplementChinaWearOsGms", "watchPairing", "forceChinaGmsCore")

    return JsonObject(migrated)
}
