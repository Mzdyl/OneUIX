package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem

private const val WATCH_PAIRING_MODE_CN = 1

@Composable
fun DetailPaneWatchManager(
    uiState: Preference.Other,
    onEvent: (WatchManagerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.watch_pairing),
            title = stringResource(id = R.string.watchPairing_connectionMode_title),
            summary = stringResource(id = R.string.watchPairing_connectionMode_summary),
            entries = listOf(
                stringResource(id = R.string.watchPairing_mode_none),
                stringResource(id = R.string.watchPairing_mode_wearos_cn),
                stringResource(id = R.string.watchPairing_mode_wearos_global)
            ),
            selectedIndex = uiState.watchPairingConnectionMode,
            onSelectedIndexChange = { onEvent(WatchManagerEvent.WatchPairingConnectionMode(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.bypassWatchPairingRegionCheck_title),
            summary = stringResource(id = R.string.bypassWatchPairingRegionCheck_summary),
            checked = uiState.bypassWatchPairingRegionCheck,
            onCheckedChange = { onEvent(WatchManagerEvent.BypassWatchPairingRegionCheck(it)) }
        )
        if (uiState.watchPairingConnectionMode == WATCH_PAIRING_MODE_CN) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.google_play),
                title = stringResource(id = R.string.supplementChinaWearOsGms_title),
                summary = stringResource(id = R.string.supplementChinaWearOsGms_summary),
                checked = uiState.supplementChinaWearOsGms,
                onCheckedChange = { onEvent(WatchManagerEvent.SupplementChinaWearOsGms(it)) }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ic_nfc),
            title = stringResource(id = R.string.bypassSamsungPayWearableCnCheck_title),
            summary = stringResource(id = R.string.bypassSamsungPayWearableCnCheck_summary),
            checked = uiState.bypassSamsungPayWearableCnCheck,
            onCheckedChange = { onEvent(WatchManagerEvent.BypassSamsungPayWearableCnCheck(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.bluetooth_connected),
            title = stringResource(id = R.string.preventWatchPluginUnpair_title),
            summary = stringResource(id = R.string.preventWatchPluginUnpair_summary),
            checked = uiState.preventWatchPluginUnpair,
            onCheckedChange = { onEvent(WatchManagerEvent.PreventWatchPluginUnpair(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.globe),
            title = stringResource(id = R.string.unlockWatchPluginGlobalFeatures_title),
            summary = stringResource(id = R.string.unlockWatchPluginGlobalFeatures_summary),
            checked = uiState.unlockWatchPluginGlobalFeatures,
            onCheckedChange = { onEvent(WatchManagerEvent.UnlockWatchPluginGlobalFeatures(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.nest_clock_farsight_digital),
            title = stringResource(id = R.string.forceShowAllWatchfaceComplications_title),
            summary = stringResource(id = R.string.forceShowAllWatchfaceComplications_summary),
            checked = uiState.forceShowAllWatchfaceComplications,
            onCheckedChange = { onEvent(WatchManagerEvent.ForceShowAllWatchfaceComplications(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.logo_dev),
            title = stringResource(id = R.string.enableWatchPluginHiddenMenuShortcut_title),
            summary = stringResource(id = R.string.enableWatchPluginHiddenMenuShortcut_summary),
            checked = uiState.enableWatchPluginHiddenMenuShortcut,
            onCheckedChange = { onEvent(WatchManagerEvent.EnableWatchPluginHiddenMenuShortcut(it)) }
        )
    }
}

sealed interface WatchManagerEvent {
    @JvmInline
    value class BypassWatchPairingRegionCheck(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class WatchPairingConnectionMode(val value: Int) : WatchManagerEvent

    @JvmInline
    value class SupplementChinaWearOsGms(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class BypassSamsungPayWearableCnCheck(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class PreventWatchPluginUnpair(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class UnlockWatchPluginGlobalFeatures(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class ForceShowAllWatchfaceComplications(val value: Boolean) : WatchManagerEvent

    @JvmInline
    value class EnableWatchPluginHiddenMenuShortcut(val value: Boolean) : WatchManagerEvent
}

fun SettingViewModel.onWatchManagerEvent(event: WatchManagerEvent) {
    updateData { preference ->
        when (event) {
            is WatchManagerEvent.BypassWatchPairingRegionCheck -> preference.copy(
                other = preference.other.copy(
                    bypassWatchPairingRegionCheck = event.value
                )
            )

            is WatchManagerEvent.WatchPairingConnectionMode -> preference.copy(
                other = preference.other.copy(
                    watchPairingConnectionMode = event.value,
                    supplementChinaWearOsGms = if (event.value == WATCH_PAIRING_MODE_CN) {
                        preference.other.supplementChinaWearOsGms
                    } else {
                        false
                    }
                )
            )

            is WatchManagerEvent.SupplementChinaWearOsGms -> preference.copy(
                other = preference.other.copy(
                    supplementChinaWearOsGms = event.value
                )
            )

            is WatchManagerEvent.BypassSamsungPayWearableCnCheck -> preference.copy(
                other = preference.other.copy(
                    bypassSamsungPayWearableCnCheck = event.value
                )
            )

            is WatchManagerEvent.PreventWatchPluginUnpair -> preference.copy(
                other = preference.other.copy(
                    preventWatchPluginUnpair = event.value
                )
            )

            is WatchManagerEvent.UnlockWatchPluginGlobalFeatures -> preference.copy(
                other = preference.other.copy(
                    unlockWatchPluginGlobalFeatures = event.value
                )
            )

            is WatchManagerEvent.ForceShowAllWatchfaceComplications -> preference.copy(
                other = preference.other.copy(
                    forceShowAllWatchfaceComplications = event.value
                )
            )

            is WatchManagerEvent.EnableWatchPluginHiddenMenuShortcut -> preference.copy(
                other = preference.other.copy(
                    enableWatchPluginHiddenMenuShortcut = event.value
                )
            )
        }
    }
}
