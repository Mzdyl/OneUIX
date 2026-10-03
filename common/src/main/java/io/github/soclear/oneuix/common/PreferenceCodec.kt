package io.github.soclear.oneuix.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
        migrateLegacyChoices(migrateLegacyCallFields(migrateLegacyOtherFields(root)))
    )
}

private fun migrateLegacyCallFields(root: JsonObject): JsonObject {
    val call = root["call"] as? JsonObject ?: return root
    val legacySupport = (call["supportCallAndTextOnOtherDevices"] as? JsonPrimitive)?.booleanOrNull ?: false
    if (!legacySupport) return root

    val callMap = call.toMutableMap()
    if ("bypassSameWifiRestriction" !in callMap) {
        callMap["bypassSameWifiRestriction"] = JsonPrimitive(true)
    }
    if ("unlockCmcMobileNetwork" !in callMap) {
        callMap["unlockCmcMobileNetwork"] = JsonPrimitive(true)
    }
    if ("bypassChinaSimRestriction" !in callMap) {
        callMap["bypassChinaSimRestriction"] = JsonPrimitive(true)
    }
    val migrated = root.toMutableMap()
    migrated["call"] = JsonObject(callMap)
    return JsonObject(migrated)
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
    copyToOther("gallery", "supportGalleryGoogleSync")
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

private fun migrateLegacyChoices(root: JsonObject): JsonObject {
    val other = root["other"] as? JsonObject ?: return root
    if ("sPenTranslationSource" in other) return root
    val useGoogle = (other["useSPenGoogleTranslate"] as? JsonPrimitive)?.booleanOrNull ?: return root
    val source = if (useGoogle) SPenTranslationSource.GOOGLE else SPenTranslationSource.DEFAULT
    val migratedOther = JsonObject(other + ("sPenTranslationSource" to JsonPrimitive(source.name)))
    return JsonObject(root + ("other" to migratedOther))
}

fun decodeStoredPreference(string: String): Preference? =
    string.takeUnless { it.isBlank() }?.let(::decodePreference)
