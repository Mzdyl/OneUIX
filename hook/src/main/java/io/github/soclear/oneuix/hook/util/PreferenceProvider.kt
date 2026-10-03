package io.github.soclear.oneuix.hook.util

import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.decodePreference
import java.io.File

object PreferenceProvider {
    context(xposedModule: XposedModule)
    fun loadPreference(): Preference? {
        val remotePref = try {
            val parcelFileDescriptor: ParcelFileDescriptor? = try {
                xposedModule.openRemoteFile(Preference.FILE_NAME)
            } catch (_: java.io.FileNotFoundException) {
                null
            }
            if (parcelFileDescriptor != null) {
                val size = parcelFileDescriptor.statSize
                ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { inputStream ->
                    if (size != 0L) {
                        decodePreference(inputStream.readBytes().decodeToString())
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
