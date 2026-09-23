package io.github.soclear.oneuix.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.soclear.oneuix.XposedServiceManager
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.decodePreference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

object PreferenceSerializer : Serializer<Preference> {
    private const val TAG = "PreferenceSerializer"

    fun readLegacyFile(): Preference? = try {
        val legacyFile = listOf(
            "/data/user_de/0/io.github.soclear.oneuix/files/datastore/preference",
            "/data/user/0/io.github.soclear.oneuix/files/datastore/preference",
            "/data/user_de/0/io.github.mzdyl.oneuix/files/datastore/preference",
            "/data/user/0/io.github.mzdyl.oneuix/files/datastore/preference"
        ).map { java.io.File(it) }.firstOrNull { it.exists() && it.canRead() }
        legacyFile?.readText()?.let(::decodePreference)
    } catch (_: Throwable) {
        null
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): Preference = try {
        val service = XposedServiceManager.xposedService
        val remotePreference = if (service != null) {
            val parcelFileDescriptor: ParcelFileDescriptor? = try {
                service.openRemoteFile(Preference.FILE_NAME)
            } catch (_: Throwable) {
                null
            }
            if (parcelFileDescriptor != null) {
                ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { inputStream ->
                    if (inputStream.channel.size() > 0L) {
                        val jsonString = inputStream.readBytes().decodeToString()
                        if (jsonString.isNotBlank() && jsonString != "{}") {
                            decodePreference(jsonString)
                        } else null
                    } else null
                }
            } else null
        } else null

        if (remotePreference != null) {
            remotePreference
        } else {
            val legacy = readLegacyFile()
            if (legacy != null && service != null) {
                try {
                    val pfd = service.openRemoteFile(Preference.FILE_NAME)
                    if (pfd != null) {
                        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                            out.channel.truncate(0)
                            IgnoreUnknownKeysJson.encodeToStream(Preference.serializer(), legacy, out)
                            out.channel.force(true)
                        }
                    }
                } catch (_: Throwable) {}
            }
            legacy ?: defaultValue
        }
    } catch (e: Exception) {
        Log.e(TAG, "readFrom", e)
        readLegacyFile() ?: defaultValue
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: Preference, output: OutputStream) {
        try {
            val service = XposedServiceManager.xposedService
            if (service != null) {
                val parcelFileDescriptor: ParcelFileDescriptor? = try {
                    service.openRemoteFile(Preference.FILE_NAME)
                } catch (_: Throwable) {
                    null
                }
                if (parcelFileDescriptor != null) {
                    ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptor).use { outputStream ->
                        outputStream.channel.truncate(0)
                        IgnoreUnknownKeysJson.encodeToStream(Preference.serializer(), t, outputStream)
                        outputStream.channel.force(true)
                    }
                }
            }

            // 同步写入一份本地副本作为备份
            try {
                listOf(
                    "/data/user_de/0/io.github.soclear.oneuix/files/datastore/preference",
                    "/data/user/0/io.github.soclear.oneuix/files/datastore/preference",
                    "/data/user_de/0/io.github.mzdyl.oneuix/files/datastore/preference",
                    "/data/user/0/io.github.mzdyl.oneuix/files/datastore/preference"
                ).map { java.io.File(it) }.firstOrNull { it.exists() && it.canWrite() }?.let { f ->
                    f.writeText(IgnoreUnknownKeysJson.encodeToString(Preference.serializer(), t))
                    f.setReadable(true, false)
                    f.parentFile?.let { dir ->
                        dir.setReadable(true, false)
                        dir.setExecutable(true, false)
                        dir.parentFile?.let { parent ->
                            parent.setReadable(true, false)
                            parent.setExecutable(true, false)
                        }
                    }
                }
            } catch (_: Throwable) {}
        } catch (e: Exception) {
            Log.e(TAG, "writeTo", e)
        }
    }

    override val defaultValue: Preference = Preference()
}

val Context.dataStore by dataStore(Preference.DATASTORE_SENTINEL_NAME, PreferenceSerializer)
