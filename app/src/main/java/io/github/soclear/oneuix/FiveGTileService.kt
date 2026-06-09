package io.github.soclear.oneuix

import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
        val hasRead =
            checkSelfPermission(READ_PRIVILEGED_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasModify = checkSelfPermission(MODIFY_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasRead && hasModify) {
            true
        } else {
            grantPermissions()
        }
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
    }

    private fun TelephonyManager.allowedNetworkTypes(): Long {
        return this.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
    }

    private fun setTileState(state: Int) {
        qsTile.state = state
        qsTile.updateTile()
    }

    companion object {
        const val READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE"
        const val MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE"
        private fun Long.has5G(): Boolean {
            return (this and TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0L
        }

        private fun grantPermissions(): Boolean {
            val grant = "su -c pm grant"
            val id = BuildConfig.APPLICATION_ID
            val command =
                "$grant $id $READ_PRIVILEGED_PHONE_STATE && $grant $id $MODIFY_PHONE_STATE"
            return try {
                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()
                exitCode == 0
            } catch (_: Throwable) {
                false
            }
        }
    }
}
