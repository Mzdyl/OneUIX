package io.github.soclear.oneuix.hook.quickshare

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class QuickShareConfig(
    val versionCode: Long,
    val schemaVersion: Int = 0,
    val nearbyShareSupportedMethod: String? = null,
    val isMoseySupportedMethod: String? = null,
    val sepGlobalCheckMethod: String? = null,
    val temporaryModeMethod: String? = null,
)

internal object QuickShareConfigCache {
    const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun load(file: File, versionCode: Long, discover: () -> QuickShareConfig): QuickShareConfig {
        val cached = runCatching { json.decodeFromString<QuickShareConfig>(file.readText()) }.getOrNull()
        if (cached?.versionCode == versionCode && cached.schemaVersion == SCHEMA_VERSION) return cached
        val config = discover().copy(versionCode = versionCode, schemaVersion = SCHEMA_VERSION)
        runCatching { file.writeText(json.encodeToString(config)) }
        return config
    }
}
