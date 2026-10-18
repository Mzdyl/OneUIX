package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneBixby(
    uiState: Preference.Bixby,
    onEvent: (BixbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.wifi_link_speed),
            title = stringResource(R.string.unlockBixbyOfflineProcessing_title),
            summary = stringResource(R.string.unlockBixbyOfflineProcessing_summary),
            checked = uiState.injectModel,
            onCheckedChange = { onEvent(BixbyEvent.InjectModel(it)) },
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.phone_forwarded),
            title = stringResource(R.string.supportBixbyCustomWakeup_title),
            summary = stringResource(R.string.supportBixbyCustomWakeup_summary),
            checked = uiState.labsMgr,
            onCheckedChange = { onEvent(BixbyEvent.LabsMgr(it)) },
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.lock_open),
            title = stringResource(R.string.bypassBixbyWakeWordRestrictions_title),
            summary = stringResource(R.string.bypassBixbyWakeWordRestrictions_summary),
            checked = uiState.wwvBypass,
            onCheckedChange = { onEvent(BixbyEvent.WwvBypass(it)) },
        )
    }
}

sealed interface BixbyEvent {
    @JvmInline
    value class InjectModel(val value: Boolean) : BixbyEvent

    @JvmInline
    value class LabsMgr(val value: Boolean) : BixbyEvent

    @JvmInline
    value class WwvBypass(val value: Boolean) : BixbyEvent
}

fun SettingViewModel.onBixbyEvent(event: BixbyEvent) {
    updateData { p ->
        when (event) {
            is BixbyEvent.InjectModel -> p.copy(bixby = p.bixby.copy(injectModel = event.value))
            is BixbyEvent.LabsMgr -> p.copy(bixby = p.bixby.copy(labsMgr = event.value))
            is BixbyEvent.WwvBypass -> p.copy(bixby = p.bixby.copy(wwvBypass = event.value))
        }
    }
}
