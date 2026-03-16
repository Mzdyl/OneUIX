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
    coerceInputValues = true  // 强制使用默认值替代解析失败的值
}

object PreferenceProvider {
    private const val PREFERENCE_FILE_NAME = "preference.json"
    
    private var cachedFile: File? = null

    val preference: Preference? = try {
        getPreferenceFile()?.readText()?.let { json.decodeFromString<Preference>(it) }
    } catch (_: Throwable) {
        null
    }

    fun getPreferenceFile(): File? {
        // 返回缓存的文件
        cachedFile?.let { if (it.exists()) return it }
        
        return try {
            val parentPath = XSharedPreferences(BuildConfig.APPLICATION_ID).file?.parent
            if (parentPath.isNullOrBlank()) return null
            
            val file = File(parentPath, PREFERENCE_FILE_NAME)

            if (!file.exists()) {
                file.writeText("{}")
                @SuppressLint("SetWorldReadable")
                file.setReadable(true, false)
            }
            
            cachedFile = file
            file
        } catch (_: Throwable) {
            null
        }
    }
}
