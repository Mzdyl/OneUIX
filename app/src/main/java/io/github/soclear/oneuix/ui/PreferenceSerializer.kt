package io.github.soclear.oneuix.ui

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.data.PreferenceJson
import io.github.soclear.oneuix.data.decodePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object PreferenceSerializer : Serializer<Preference> {
    override suspend fun readFrom(input: InputStream): Preference {
        return try {
            val jsonString = input.readBytes().decodeToString()
            // 如果是空文件或无效 JSON，返回默认值
            if (jsonString.isBlank() || jsonString == "{}") {
                return defaultValue
            }
            decodePreference(jsonString)
        } catch (e: Exception) {
            // 记录错误但不打印堆栈（避免日志刷屏）
            android.util.Log.w("OneUIX", "PreferenceSerializer: ${e.message}")
            defaultValue
        }
    }

    override suspend fun writeTo(t: Preference, output: OutputStream) = withContext(Dispatchers.IO) {
        output.write(
            PreferenceJson.encodeToString(
                serializer = Preference.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }

    override val defaultValue: Preference  = Preference()
}

val Context.dataStore by dataStore(Preference.DATASTORE_SENTINEL_NAME, PreferenceSerializer)
