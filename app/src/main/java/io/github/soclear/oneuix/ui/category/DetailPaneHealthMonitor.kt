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

@Composable
fun DetailPaneHealthMonitor(
    uiState: Preference.HealthMonitor,
    onEvent: (HealthMonitorEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.person_pin_circle),
            title = stringResource(id = R.string.bypassHealthMonitorCountryCheck_title),
            summary = stringResource(id = R.string.bypassHealthMonitorCountryCheck_summary),
            checked = uiState.bypassHealthMonitorCountryCheck,
            onCheckedChange = { onEvent(HealthMonitorEvent.BypassHealthMonitorCountryCheck(it)) }
        )
    }
}

sealed interface HealthMonitorEvent {
    @JvmInline
    value class BypassHealthMonitorCountryCheck(val value: Boolean) : HealthMonitorEvent
}

fun SettingViewModel.onHealthMonitorEvent(event: HealthMonitorEvent) {
    updateData { preference ->
        when (event) {
            is HealthMonitorEvent.BypassHealthMonitorCountryCheck -> preference.copy(
                healthMonitor = preference.healthMonitor.copy(
                    bypassHealthMonitorCountryCheck = event.value
                )
            )
        }
    }
}
