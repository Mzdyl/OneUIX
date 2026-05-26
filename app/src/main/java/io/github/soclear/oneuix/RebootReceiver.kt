package io.github.soclear.oneuix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.soclear.oneuix.data.RebootRequest

class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RebootRequest.ACTION) return
        val reason = intent.getStringExtra(RebootRequest.EXTRA_REASON)
        Runtime.getRuntime().exec("su -c reboot $reason")
    }
}
