package io.github.soclear.oneuix

import android.app.Activity
import android.os.Bundle

class RebootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.action?.let {
            Runtime.getRuntime().exec("su -c reboot $it")
        }
        finish()
    }
}
