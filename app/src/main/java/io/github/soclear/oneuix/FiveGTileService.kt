package io.github.soclear.oneuix

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

class FiveGTileService : TileService() {
    private var permissionsGranted = false
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate() {
        permissionsGranted = ensurePermissions()
        if (!permissionsGranted) {
            return
        }
        subscriptionManager = getSystemService(SubscriptionManager::class.java)
        telephonyManager = getSystemService(TelephonyManager::class.java)
    }

    private fun ensurePermissions(): Boolean {
        val granted = getRequiredPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        return granted || grantPermissions()
    }

    private fun updateTileState() {
        setTileState(if (dataTelephonyManager().has5G()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
    }

    override fun onClick() {
        if (!permissionsGranted) {
            setTileState(Tile.STATE_UNAVAILABLE)
            return
        }
        val enable5G = !dataTelephonyManager().has5G()
        setTileState(Tile.STATE_UNAVAILABLE)
        activeTelephonyManagers().forEach { it.set5GEnabled(enable5G) }
        updateTileState()
    }

    override fun onStartListening() {
        if (!permissionsGranted) {
            setTileState(Tile.STATE_UNAVAILABLE)
            return
        }
        updateTileState()
    }

    @SuppressLint("MissingPermission")
    private fun activeTelephonyManagers(): List<TelephonyManager> {
        return subscriptionManager.activeSubscriptionInfoList
            .orEmpty()
            .map { it.subscriptionId }
            .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            .ifEmpty {
                listOf(SubscriptionManager.getActiveDataSubscriptionId())
                    .filter { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            }
            .map { telephonyManager.createForSubscriptionId(it) }
    }

    private fun dataTelephonyManager(): TelephonyManager {
        return telephonyManager.createForSubscriptionId(SubscriptionManager.getActiveDataSubscriptionId())
    }

    private fun TelephonyManager.has5G(): Boolean {
        return allowedNetworkTypes().has5G()
    }

    private fun TelephonyManager.set5GEnabled(enabled: Boolean) {
        val currentAllowedNetworkTypes = allowedNetworkTypes()
        val newAllowedNetworkTypes = if (enabled) {
            currentAllowedNetworkTypes or TelephonyManager.NETWORK_TYPE_BITMASK_NR
        } else {
            currentAllowedNetworkTypes and TelephonyManager.NETWORK_TYPE_BITMASK_NR.inv()
        }
        if (newAllowedNetworkTypes != currentAllowedNetworkTypes) {
            setAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                newAllowedNetworkTypes
            )
        }
        persistPreferredNetworkMode(subscriptionId, enabled)
    }

    // On boot, Samsung's PreferredNetworkUpdater reads Settings.Global "preferred_network_mode<subId>"
    // and overwrites REASON_USER with it, so the toggle only survives a reboot if we update that key
    // too. The stored value is a 0..33 RILConstants network-mode enum; we flip its NR component via a
    // fixed LTE<->NR pairing (no hidden RadioAccessFamily API) so it adapts to whatever RAT combo the
    // current enum already encodes.
    private fun persistPreferredNetworkMode(subId: Int, enable5G: Boolean) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
        val key = "preferred_network_mode$subId"
        val current = Settings.Global.getInt(contentResolver, key, -1)
        if (current < 0) return
        val target = if (enable5G) {
            LTE_TO_NR_MODE[current] ?: current
        } else {
            NR_TO_LTE_MODE[current] ?: current
        }
        if (target != current) {
            Settings.Global.putInt(contentResolver, key, target)
        }
    }

    private fun TelephonyManager.allowedNetworkTypes(): Long {
        return this.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
    }

    private fun setTileState(state: Int) {
        qsTile.state = state
        qsTile.updateTile()
    }

    companion object {
        // RILConstants network-mode enums: each LTE combo paired with its NR-enabled counterpart.
        // Covers every LTE RAT combination, so flipping NR never drops other RATs (device-independent).
        // Verified against RadioAccessFamily.getNetworkTypeFromRaf: each NR mode's RAF equals its LTE
        // mode's RAF | RAF_NR (0x80000), so this pairing is exactly the firmware's own NR<->LTE mapping.
        private val LTE_TO_NR_MODE = mapOf(
            8 to 25,   // LTE_CDMA_EVDO -> NR_LTE_CDMA_EVDO
            9 to 26,   // LTE_GSM_WCDMA -> NR_LTE_GSM_WCDMA
            10 to 27,  // LTE_CDMA_EVDO_GSM_WCDMA -> NR_LTE_CDMA_EVDO_GSM_WCDMA
            11 to 24,  // LTE_ONLY -> NR_LTE
            12 to 28,  // LTE_WCDMA -> NR_LTE_WCDMA
            15 to 29,  // LTE_TDSCDMA -> NR_LTE_TDSCDMA
            17 to 30,  // LTE_TDSCDMA_GSM -> NR_LTE_TDSCDMA_GSM
            19 to 31,  // LTE_TDSCDMA_WCDMA -> NR_LTE_TDSCDMA_WCDMA
            20 to 32,  // LTE_TDSCDMA_GSM_WCDMA -> NR_LTE_TDSCDMA_GSM_WCDMA
            22 to 33,  // LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA -> NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
        )
        private val NR_TO_LTE_MODE = LTE_TO_NR_MODE.entries.associate { (lte, nr) -> nr to lte }

        private fun getRequiredPermissions() = arrayOf(
            "android.permission.READ_PRIVILEGED_PHONE_STATE",
            Manifest.permission.MODIFY_PHONE_STATE,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        )

        private fun Long.has5G(): Boolean {
            return (this and TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0L
        }

        private fun grantPermissions(): Boolean {
            val script = getRequiredPermissions().joinToString(" && ") { permission ->
                "pm grant ${BuildConfig.APPLICATION_ID} $permission"
            }
            return try {
                ProcessBuilder("su", "-c", script).start().waitFor() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }
}
