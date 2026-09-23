package io.github.soclear.oneuix.hook.util

import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.decodePreference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.io.File

object PreferenceProvider {
    @OptIn(ExperimentalSerializationApi::class)
    context(xposedModule: XposedModule)
    fun loadPreference(): Preference? {
        val remotePref = try {
            val parcelFileDescriptor: ParcelFileDescriptor? = try {
                xposedModule.openRemoteFile(Preference.FILE_NAME)
            } catch (_: java.io.FileNotFoundException) {
                null
            }
            if (parcelFileDescriptor != null) {
                ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { inputStream ->
                    if (inputStream.channel.size() > 0L) {
                        IgnoreUnknownKeysJson.decodeFromStream<Preference>(inputStream)
                    } else null
                }
            } else null
        } catch (t: Throwable) {
            xlog(t)
            null
        }

        if (remotePref != null) {
            return remotePref
        }

        return try {
            val legacyFile = listOf(
                "/data/user_de/0/io.github.soclear.oneuix/files/datastore/preference",
                "/data/user/0/io.github.soclear.oneuix/files/datastore/preference",
                "/data/user_de/0/io.github.mzdyl.oneuix/files/datastore/preference",
                "/data/user/0/io.github.mzdyl.oneuix/files/datastore/preference"
            ).map { File(it) }.firstOrNull { it.exists() && it.canRead() }
            legacyFile?.readText()?.let(::decodePreference)
        } catch (_: Throwable) {
            null
        }
    }
}
