package io.github.soclear.oneuix.hook.util

import de.robv.android.xposed.XSharedPreferences
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.data.Preference
import java.io.File

object PreferenceProvider {
    val preference: Preference? = try {
        IgnoreUnknownKeysJson.decodeFromString<Preference>(getPreferenceFile().readText())
    } catch (_: Throwable) {
        null
    }

    fun getPreferenceFile(): File {
        val path = XSharedPreferences(BuildConfig.APPLICATION_ID).file.parent
        return File(path, Preference.FILE_NAME)
    }
}
