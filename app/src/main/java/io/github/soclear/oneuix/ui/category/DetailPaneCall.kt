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
import io.github.soclear.oneuix.ui.component.SelectItem
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneCall(
    uiState: Preference.Call,
    onEvent: (CallEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.call_recording),
            title = stringResource(id = R.string.supportVoiceCallRecording_title),
            checked = uiState.supportVoiceCallRecording,
            onCheckedChange = { onEvent(CallEvent.SupportVoiceCallRecording(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.call_recording),
            title = stringResource(id = R.string.preferRecordingButton_title),
            checked = uiState.preferRecordingButton,
            onCheckedChange = { onEvent(CallEvent.PreferRecordingButton(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.person_pin_circle),
            title = stringResource(id = R.string.showGeocodedLocationInRecentCall_title),
            checked = uiState.showGeocodedLocationInRecentCall,
            onCheckedChange = { onEvent(CallEvent.ShowGeocodedLocationInRecentCall(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.call_log),
            title = stringResource(id = R.string.isOpStyleCHN_title),
            checked = uiState.isOpStyleCHN,
            onCheckedChange = { onEvent(CallEvent.IsOpStyleCHN(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.wifi_link_speed),
            title = stringResource(id = R.string.bypassSameWifiRestriction_title),
            summary = stringResource(id = R.string.bypassSameWifiRestriction_summary),
            checked = uiState.bypassSameWifiRestriction,
            onCheckedChange = { onEvent(CallEvent.BypassSameWifiRestriction(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.net_speed),
            title = stringResource(id = R.string.unlockCmcMobileNetwork_title),
            summary = stringResource(id = R.string.unlockCmcMobileNetwork_summary),
            checked = uiState.unlockCmcMobileNetwork,
            onCheckedChange = { onEvent(CallEvent.UnlockCmcMobileNetwork(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.sim_card),
            title = stringResource(id = R.string.bypassChinaSimRestriction_title),
            summary = stringResource(id = R.string.bypassChinaSimRestriction_summary),
            checked = uiState.bypassChinaSimRestriction,
            onCheckedChange = { onEvent(CallEvent.BypassChinaSimRestriction(it)) }
        )
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.phone_forwarded),
            title = stringResource(id = R.string.mdecDeviceType_title),
            summary = stringResource(id = R.string.mdecDeviceType_summary),
            entries = listOf(
                stringResource(id = R.string.mdecDeviceType_default),
                stringResource(id = R.string.mdecDeviceType_phone),
                stringResource(id = R.string.mdecDeviceType_tablet),
            ),
            selectedIndex = uiState.mdecDeviceType,
            onSelectedIndexChange = { onEvent(CallEvent.SetMdecDeviceType(it)) }
        )
    }
}


sealed interface CallEvent {
    @JvmInline
    value class SupportVoiceCallRecording(val value: Boolean) : CallEvent

    @JvmInline
    value class PreferRecordingButton(val value: Boolean) : CallEvent

    @JvmInline
    value class ShowGeocodedLocationInRecentCall(val value: Boolean) : CallEvent

    @JvmInline
    value class IsOpStyleCHN(val value: Boolean) : CallEvent

    @JvmInline
    value class BypassSameWifiRestriction(val value: Boolean) : CallEvent

    @JvmInline
    value class UnlockCmcMobileNetwork(val value: Boolean) : CallEvent

    @JvmInline
    value class BypassChinaSimRestriction(val value: Boolean) : CallEvent

    @JvmInline
    value class SetMdecDeviceType(val value: Int) : CallEvent
}

fun SettingViewModel.onCallEvent(event: CallEvent) {
    updateData {
        when (event) {
            is CallEvent.SupportVoiceCallRecording -> {
                it.copy(call = it.call.copy(supportVoiceCallRecording = event.value))
            }

            is CallEvent.PreferRecordingButton -> {
                it.copy(call = it.call.copy(preferRecordingButton = event.value))
            }

            is CallEvent.ShowGeocodedLocationInRecentCall -> {
                it.copy(call = it.call.copy(showGeocodedLocationInRecentCall = event.value))
            }

            is CallEvent.IsOpStyleCHN -> {
                it.copy(call = it.call.copy(isOpStyleCHN = event.value))
            }

            is CallEvent.BypassSameWifiRestriction -> {
                it.copy(call = it.call.copy(bypassSameWifiRestriction = event.value))
            }

            is CallEvent.UnlockCmcMobileNetwork -> {
                it.copy(call = it.call.copy(unlockCmcMobileNetwork = event.value))
            }

            is CallEvent.BypassChinaSimRestriction -> {
                it.copy(call = it.call.copy(bypassChinaSimRestriction = event.value))
            }

            is CallEvent.SetMdecDeviceType -> {
                it.copy(call = it.call.copy(mdecDeviceType = event.value))
            }
        }
    }
}
