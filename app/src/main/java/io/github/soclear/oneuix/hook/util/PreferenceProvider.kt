package io.github.soclear.oneuix.hook.util

import android.annotation.SuppressLint
import de.robv.android.xposed.XSharedPreferences
import kotlinx.serialization.json.Json
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Preference
import java.io.File

// 使用自定义 Json 配置，忽略未知字段以兼容旧版本数据
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

object PreferenceProvider {
    private const val PREFERENCE_FILE_NAME = "preference.json"

    val preference: Preference? = try {
        json.decodeFromString<Preference>(getPreferenceFile().readText())
    } catch (_: Throwable) {
        null
    }

    fun getPreferenceFile(): File {
        val path = XSharedPreferences(BuildConfig.APPLICATION_ID).file.parent
        val file = File(path, PREFERENCE_FILE_NAME)

        if (!file.exists()) {
            file.writeText("{}")
            @SuppressLint("SetWorldReadable")
            file.setReadable(true, false)
        }

        return file
    }
}
