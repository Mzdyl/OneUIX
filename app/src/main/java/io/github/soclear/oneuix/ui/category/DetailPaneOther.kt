package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.AppPickerDialog
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneOther(
    uiState: Preference.Other,
    onEvent: (OtherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.screenshot),
            title = stringResource(R.string.hideScreenshotDetection_title),
            summary = stringResource(R.string.app_picker_selected_count, uiState.hideScreenshotDetectionPackages.size) +
                "\n" + stringResource(R.string.hideScreenshotDetection_summary),
            clickable = true,
            onClick = { showAppPicker = true },
            checked = uiState.hideScreenshotDetection,
            onCheckedChange = {
                showAppPicker = it && uiState.hideScreenshotDetectionPackages.isEmpty()
                onEvent(OtherEvent.HideScreenshotDetection(it))
            }
        )
    }
    if (showAppPicker) {
        AppPickerDialog(
            selectedPackages = uiState.hideScreenshotDetectionPackages,
            onDismissRequest = { showAppPicker = false },
            onConfirm = {
                onEvent(OtherEvent.HideScreenshotDetectionPackages(it))
                showAppPicker = false
            }
        )
    }
}

sealed interface OtherEvent {
    @JvmInline
    value class HideScreenshotDetection(val value: Boolean) : OtherEvent

    @JvmInline
    value class HideScreenshotDetectionPackages(val value: Set<String>) : OtherEvent
}

fun SettingViewModel.onOtherEvent(event: OtherEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is OtherEvent.HideScreenshotDetection -> preference.other.copy(hideScreenshotDetection = event.value)
            is OtherEvent.HideScreenshotDetectionPackages -> preference.other.copy(hideScreenshotDetectionPackages = event.value)
        })
    }
}
