package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem
import io.github.soclear.oneuix.ui.component.SelectItem

@Composable
fun DetailPaneWatchPairing(
    uiState: Preference.WatchPairing,
    onEvent: (WatchPairingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 连接模式选择
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.bluetooth_connected),
            title = stringResource(id = R.string.watchPairing_connectionMode_title),
            summary = stringResource(id = R.string.watchPairing_connectionMode_summary),
            entries = listOf(
                stringResource(id = R.string.watchPairing_mode_auto),
                stringResource(id = R.string.watchPairing_mode_wearos_cn),
                stringResource(id = R.string.watchPairing_mode_wearos_global)
            ),
            selectedIndex = uiState.connectionMode,
            onSelectedIndexChange = { onEvent(WatchPairingEvent.SetConnectionMode(it)) }
        )

        // 绕过区域限制
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.watchPairing_bypassRegionCheck_title),
            summary = stringResource(id = R.string.watchPairing_bypassRegionCheck_summary),
            checked = uiState.bypassRegionCheck,
            onCheckedChange = { onEvent(WatchPairingEvent.SetBypassRegionCheck(it)) }
        )

        // 禁用 CSC 检查
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.shield_off),
            title = stringResource(id = R.string.watchPairing_disableCscCheck_title),
            summary = stringResource(id = R.string.watchPairing_disableCscCheck_summary),
            checked = uiState.disableCscCheck,
            onCheckedChange = { onEvent(WatchPairingEvent.SetDisableCscCheck(it)) }
        )

        // 强制安装国行 GMS
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.google_play),
            title = stringResource(id = R.string.watchPairing_forceChinaGmsCore_title),
            summary = stringResource(id = R.string.watchPairing_forceChinaGmsCore_summary),
            checked = uiState.forceChinaGmsCore,
            onCheckedChange = { onEvent(WatchPairingEvent.SetForceChinaGmsCore(it)) }
        )
    }
}

/**
 * 手表配对事件定义
 */
sealed interface WatchPairingEvent {
    @JvmInline
    value class SetBypassRegionCheck(val value: Boolean) : WatchPairingEvent

    @JvmInline
    value class SetConnectionMode(val value: Int) : WatchPairingEvent

    @JvmInline
    value class SetForceChinaGmsCore(val value: Boolean) : WatchPairingEvent

    @JvmInline
    value class SetDisableCscCheck(val value: Boolean) : WatchPairingEvent
}

/**
 * ViewModel 事件处理
 */
fun SettingViewModel.onWatchPairingEvent(event: WatchPairingEvent) {
    updateData { preference ->
        when (event) {
            is WatchPairingEvent.SetBypassRegionCheck -> preference.copy(
                watchPairing = preference.watchPairing.copy(
                    bypassRegionCheck = event.value
                )
            )
            is WatchPairingEvent.SetConnectionMode -> preference.copy(
                watchPairing = preference.watchPairing.copy(
                    connectionMode = event.value
                )
            )
            is WatchPairingEvent.SetForceChinaGmsCore -> preference.copy(
                watchPairing = preference.watchPairing.copy(
                    forceChinaGmsCore = event.value
                )
            )
            is WatchPairingEvent.SetDisableCscCheck -> preference.copy(
                watchPairing = preference.watchPairing.copy(
                    disableCscCheck = event.value
                )
            )
        }
    }
}
