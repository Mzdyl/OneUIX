package io.github.soclear.oneuix.hook.util

import de.robv.android.xposed.XSharedPreferences
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.data.decodePreference
import java.io.File

object PreferenceProvider {
    private var cachedFile: File? = null

    val preference: Preference? = try {
        getPreferenceFile()?.readText()?.let(::decodePreference)
    } catch (_: Throwable) {
        // 如果一个用户启用模块后，没有点过任何偏好设置
        // 那么调用 getPreferenceFile().readText() 会 FileNotFoundException
        // 导致 preference 为空，于是不会有任何 hook 生效
        // 意料之外，情理之中
        null
    }

    fun getPreferenceFile(): File? {
        cachedFile?.let { return it }

        return try {
            val parentPath = XSharedPreferences(BuildConfig.APPLICATION_ID).file?.parent
            if (parentPath.isNullOrBlank()) return null

            File(parentPath, Preference.FILE_NAME).also { cachedFile = it }
        } catch (_: Throwable) {
            null
        }
    }
}
