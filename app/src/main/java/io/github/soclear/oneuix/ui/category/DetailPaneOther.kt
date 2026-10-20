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
fun DetailPaneOther(
    uiState: Preference.Other,
    onEvent: (OtherEvent) -> Unit,
    onNavigateToAppPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.screenshot),
            title = stringResource(R.string.hideScreenshotDetection_title),
            summary = stringResource(R.string.app_picker_selected_count, uiState.hideScreenshotDetectionPackages.size) +
                "\n" + stringResource(R.string.hideScreenshotDetection_summary),
            clickable = true,
            onClick = onNavigateToAppPicker,
            checked = uiState.hideScreenshotDetection,
            onCheckedChange = {
                onEvent(OtherEvent.HideScreenshotDetection(it))
                if (it && uiState.hideScreenshotDetectionPackages.isEmpty()) onNavigateToAppPicker()
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
