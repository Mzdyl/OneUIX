package io.github.soclear.oneuix

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import io.github.soclear.oneuix.ui.SettingScreen
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.category.NfcScanChannel
import io.github.soclear.oneuix.ui.theme.OneUIXTheme

class MainActivity : ComponentActivity() {
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var nfcPendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        nfcPendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setSettingScreen()
        if (!XposedServiceManager.isModuleActive) {
            Toast.makeText(this, R.string.module_disabled_tip, Toast.LENGTH_LONG).show()
        }
        handleNfcIntent(getIntent())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        disableNfcReader()
    }

    override fun onResume() {
        super.onResume()
        enableNfcReader()
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            @Suppress("DEPRECATION")
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                processTag(tag)
            } else {
                val rawId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
                if (rawId != null && rawId.isNotEmpty()) {
                    processRawId(rawId)
                }
            }
        }
    }

    private fun processTag(tag: Tag) {
        val id = tag.id ?: return
        if (id.isEmpty()) return
        val uid = id.joinToString(":") { "%02X".format(it) }
        var sak = "04"
        var atqa = "00"
        try {
            val nfcA = NfcA.get(tag)
            if (nfcA != null) {
                sak = "%02X".format(nfcA.sak.toInt() and 0xFF)
                if (nfcA.atqa.isNotEmpty()) {
                    atqa = nfcA.atqa.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
                }
            }
        } catch (_: Throwable) {}
        vibrateFeedback()
        NfcScanChannel.onTagScanned(uid, sak, atqa)
    }

    private fun processRawId(id: ByteArray) {
        if (id.isEmpty()) return
        val uid = id.joinToString(":") { "%02X".format(it) }
        vibrateFeedback()
        NfcScanChannel.onTagScanned(uid, "04", "00")
    }

    private fun enableNfcReader() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        try {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            adapter.enableReaderMode(
                this,
                { tag ->
                    processTag(tag)
                },
                flags,
                null
            )
        } catch (_: Throwable) {}
        try {
            nfcPendingIntent?.let {
                adapter.enableForegroundDispatch(this, it, null, null)
            }
        } catch (_: Throwable) {}
    }

    private fun disableNfcReader() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Throwable) {}
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Throwable) {}
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {}
    }


    private fun setSettingScreen() {
        val viewModel: SettingViewModel by viewModels {
            SettingViewModelFactory(this.application)
        }

        setContent {
            OneUIXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private class SettingViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
