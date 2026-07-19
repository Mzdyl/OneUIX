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
fun DetailPaneSamsungHealth(
    uiState: Preference.SamsungHealth,
    onEvent: (SamsungHealthEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_open),
            title = stringResource(id = R.string.samsungHealthBypassAccountCountry_title),
            summary = stringResource(id = R.string.samsungHealthBypassAccountCountry_summary),
            checked = uiState.bypassAccountCountryCheck,
            onCheckedChange = { onEvent(SamsungHealthEvent.BypassAccountCountryCheck(it)) }
        )
        SelectItem(
            icon = ImageVector.vectorResource(id = R.drawable.globe),
            title = stringResource(id = R.string.samsungHealthServerRegion_title),
            summary = stringResource(id = R.string.samsungHealthServerRegion_summary),
            entries = listOf(
                stringResource(id = R.string.samsungHealthServerRegion_default),
                stringResource(id = R.string.samsungHealthServerRegion_china),
                stringResource(id = R.string.samsungHealthServerRegion_global)
            ),
            selectedIndex = uiState.serverRegion,
            onSelectedIndexChange = { onEvent(SamsungHealthEvent.ServerRegion(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
            title = stringResource(id = R.string.samsungHealthUnlockCountryFeatures_title),
            summary = stringResource(id = R.string.samsungHealthUnlockCountryFeatures_summary),
            checked = uiState.unlockCountryFeatures,
            onCheckedChange = { onEvent(SamsungHealthEvent.UnlockCountryFeatures(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.bluetooth_connected),
            title = stringResource(id = R.string.samsungHealthUnlockAccessories_title),
            summary = stringResource(id = R.string.samsungHealthUnlockAccessories_summary),
            checked = uiState.unlockAccessoryProfiles,
            onCheckedChange = { onEvent(SamsungHealthEvent.UnlockAccessoryProfiles(it)) }
        )
    }
}

sealed interface SamsungHealthEvent {
    @JvmInline
    value class BypassAccountCountryCheck(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class ServerRegion(val value: Int) : SamsungHealthEvent

    @JvmInline
    value class UnlockCountryFeatures(val value: Boolean) : SamsungHealthEvent

    @JvmInline
    value class UnlockAccessoryProfiles(val value: Boolean) : SamsungHealthEvent
}

fun SettingViewModel.onSamsungHealthEvent(event: SamsungHealthEvent) {
    updateData { preference ->
        when (event) {
            is SamsungHealthEvent.BypassAccountCountryCheck -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    bypassAccountCountryCheck = event.value
                )
            )

            is SamsungHealthEvent.ServerRegion -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    serverRegion = event.value.coerceIn(0, 2)
                )
            )

            is SamsungHealthEvent.UnlockCountryFeatures -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    unlockCountryFeatures = event.value
                )
            )

            is SamsungHealthEvent.UnlockAccessoryProfiles -> preference.copy(
                samsungHealth = preference.samsungHealth.copy(
                    unlockAccessoryProfiles = event.value
                )
            )
        }
    }
}
