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
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneBixby(
    uiState: Preference.Bixby,
    onEvent: (BixbyEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            title = stringResource(id = R.string.bixby_enable_offline_title),
            summary = stringResource(id = R.string.bixby_enable_offline_summary),
            icon = ImageVector.vectorResource(id = R.drawable.wifi_link_speed),
            checked = uiState.enableOffline,
            onCheckedChange = { onEvent(BixbyEvent.EnableOffline(it)) },
        )
        SwitchItem(
            title = stringResource(id = R.string.bixby_enable_custom_wakeup_title),
            summary = stringResource(id = R.string.bixby_enable_custom_wakeup_summary),
            icon = ImageVector.vectorResource(id = R.drawable.phone_forwarded),
            checked = uiState.enableCustomWakeup,
            onCheckedChange = { onEvent(BixbyEvent.EnableCustomWakeup(it)) },
        )
    }
}

sealed interface BixbyEvent {
    @JvmInline value class EnableOffline(val value: Boolean) : BixbyEvent
    @JvmInline value class EnableCustomWakeup(val value: Boolean) : BixbyEvent
}

fun io.github.soclear.oneuix.ui.SettingViewModel.onBixbyEvent(event: BixbyEvent) {
    updateData { preference ->
        when (event) {
            is BixbyEvent.EnableOffline -> preference.copy(bixby = preference.bixby.copy(enableOffline = event.value))
            is BixbyEvent.EnableCustomWakeup -> preference.copy(bixby = preference.bixby.copy(enableCustomWakeup = event.value))
        }
    }
}