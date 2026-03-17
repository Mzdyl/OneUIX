package io.github.soclear.oneuix.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneAod(
    uiState: Preference.Aod,
    onEvent: (AodEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var timeoutSeconds by remember { mutableIntStateOf(uiState.autoModeTimeoutSeconds) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.mobile_screensaver),
            title = stringResource(id = R.string.customizeAutoModeTimeout_title),
            summary = stringResource(id = R.string.customizeAutoModeTimeout_summary),
            checked = uiState.customizeAutoModeTimeout,
            onCheckedChange = { onEvent(AodEvent.CustomizeAutoModeTimeout(it)) }
        )

        AnimatedVisibility(uiState.customizeAutoModeTimeout) {
            Column {
                ListItem(
                    headlineContent = { 
                        Text(stringResource(id = R.string.autoModeTimeoutSeconds_title)) 
                    },
                    supportingContent = { 
                        Text(stringResource(id = R.string.autoModeTimeoutSeconds_summary, timeoutSeconds))
                    },
                    leadingContent = {
                        ImageVector.vectorResource(id = R.drawable.mobile_screensaver)?.let { 
                            androidx.compose.material3.Icon(it, null) 
                        }
                    }
                )
                Slider(
                    value = timeoutSeconds.toFloat(),
                    onValueChange = { timeoutSeconds = it.toInt() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 10f..600f,
                    steps = 58,
                    onValueChangeFinished = { 
                        onEvent(AodEvent.AutoModeTimeoutSeconds(timeoutSeconds)) 
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

sealed interface AodEvent {
    @JvmInline
    value class CustomizeAutoModeTimeout(val value: Boolean) : AodEvent

    @JvmInline
    value class AutoModeTimeoutSeconds(val value: Int) : AodEvent
}

fun SettingViewModel.onAodEvent(event: AodEvent) {
    updateData { preference ->
        when (event) {
            is AodEvent.CustomizeAutoModeTimeout -> preference.copy(
                aod = preference.aod.copy(
                    customizeAutoModeTimeout = event.value
                )
            )
            is AodEvent.AutoModeTimeoutSeconds -> preference.copy(
                aod = preference.aod.copy(
                    autoModeTimeoutSeconds = event.value
                )
            )
        }
    }
}