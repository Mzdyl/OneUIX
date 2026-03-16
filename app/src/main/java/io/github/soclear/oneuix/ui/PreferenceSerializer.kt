package io.github.soclear.oneuix.ui

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.json.Json
import io.github.soclear.oneuix.data.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

// 使用自定义 Json 配置，忽略未知字段以兼容旧版本数据
private val json = Json {
    ignoreUnknownKeys = true  // 忽略旧版本中存在但新版本已删除的字段
    isLenient = true          // 宽松模式，允许一些非标准 JSON
    encodeDefaults = true     // 编码默认值
    coerceInputValues = true  // 强制使用默认值替代解析失败的值
}

object PreferenceSerializer : Serializer<Preference> {
    override suspend fun readFrom(input: InputStream): Preference {
        return try {
            val jsonString = input.readBytes().decodeToString()
            // 如果是空文件或无效 JSON，返回默认值
            if (jsonString.isBlank() || jsonString == "{}") {
                return defaultValue
            }
            json.decodeFromString(
                deserializer = Preference.serializer(),
                string = jsonString
            )
        } catch (e: Exception) {
            // 记录错误但不打印堆栈（避免日志刷屏）
            android.util.Log.w("OneUIX", "PreferenceSerializer: ${e.message}")
            defaultValue
        }
    }

    override suspend fun writeTo(t: Preference, output: OutputStream) = withContext(Dispatchers.IO) {
        output.write(
            json.encodeToString(
                serializer = Preference.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }

    override val defaultValue: Preference  = Preference()
}

val Context.dataStore by dataStore("whatever", PreferenceSerializer)
