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
fun DetailPanePhotoRetouching(
    uiState: Preference.PhotoRetouching,
    onEvent: (PhotoRetouchingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.format_paint),
            title = stringResource(id = R.string.enableSketch_title),
            summary = stringResource(id = R.string.enableSketch_summary),
            checked = uiState.enableSketch,
            onCheckedChange = { onEvent(PhotoRetouchingEvent.EnableSketch(it)) }
        )

        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.branding_watermark),
            title = stringResource(id = R.string.noAIWatermark_title),
            checked = uiState.noAIWatermark,
            onCheckedChange = { onEvent(PhotoRetouchingEvent.NoAIWatermark(it)) }
        )
    }
}

sealed interface PhotoRetouchingEvent {
    @JvmInline
    value class EnableSketch(val value: Boolean) : PhotoRetouchingEvent
    @JvmInline
    value class NoAIWatermark(val value: Boolean) : PhotoRetouchingEvent
}

fun SettingViewModel.onPhotoRetouchingEvent(event: PhotoRetouchingEvent) {
    updateData { preference ->
        when (event) {
            is PhotoRetouchingEvent.EnableSketch -> preference.copy(
                photoRetouching = preference.photoRetouching.copy(
                    enableSketch = event.value
                )
            )
            is PhotoRetouchingEvent.NoAIWatermark -> preference.copy(
                photoRetouching = preference.photoRetouching.copy(
                    noAIWatermark = event.value
                )
            )
        }
    }
}
