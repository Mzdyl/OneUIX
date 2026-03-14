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
fun DetailPaneVideo(
    uiState: Preference.Video,
    onEvent: (VideoEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.fast_forward),
            title = stringResource(id = R.string.showMorePlaybackSpeeds_title),
            summary = stringResource(id = R.string.showMorePlaybackSpeeds_summary),
            checked = uiState.showMorePlaybackSpeeds,
            onCheckedChange = { onEvent(VideoEvent.ShowMorePlaybackSpeeds(it)) }
        )
    }
}

sealed interface VideoEvent {
    @JvmInline
    value class ShowMorePlaybackSpeeds(val value: Boolean) : VideoEvent
}

fun SettingViewModel.onVideoEvent(event: VideoEvent) {
    updateData { preference ->
        when (event) {
            is VideoEvent.ShowMorePlaybackSpeeds -> preference.copy(
                video = preference.video.copy(
                    showMorePlaybackSpeeds = event.value
                )
            )
        }
    }
}
