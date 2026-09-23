package io.github.soclear.oneuix

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

object XposedServiceManager {

    @Volatile
    var xposedService: XposedService? = null
        private set

    val isModuleActive: Boolean
        get() = xposedService != null

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                Thread {
                    try {
                        val legacy = io.github.soclear.oneuix.ui.PreferenceSerializer.readLegacyFile()
                        if (legacy != null) {
                            val pfd = service.openRemoteFile(io.github.soclear.oneuix.common.Preference.FILE_NAME)
                            if (pfd != null) {
                                android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                                    if (out.channel.size() == 0L) {
                                        out.channel.truncate(0)
                                        val jsonString = io.github.soclear.oneuix.common.IgnoreUnknownKeysJson.encodeToString(
                                            io.github.soclear.oneuix.common.Preference.serializer(),
                                            legacy
                                        )
                                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                                        out.channel.force(true)
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }.start()
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService == service) {
                    xposedService = null
                }
            }
        })
    }
}
