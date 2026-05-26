package io.github.soclear.oneuix.data

import io.github.soclear.oneuix.BuildConfig

object RebootRequest {
    const val ACTION = "${BuildConfig.APPLICATION_ID}.action.REBOOT"
    const val EXTRA_REASON = "reason"
    const val RECOVERY = "recovery"
    const val DOWNLOAD = "download"
}
