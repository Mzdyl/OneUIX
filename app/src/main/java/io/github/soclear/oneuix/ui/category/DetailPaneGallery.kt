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
fun DetailPaneGallery(
    uiState: Preference.Gallery,
    onEvent: (GalleryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportAllGallerySettings_title),
            summary = stringResource(id = R.string.supportAllGallerySettings_summary),
            checked = uiState.supportAllGallerySettings,
            onCheckedChange = { onEvent(GalleryEvent.SupportAllGallerySettings(it)) }
        )
    }
}

sealed interface GalleryEvent {
    @JvmInline
    value class SupportAllGallerySettings(val value: Boolean) : GalleryEvent
}

fun SettingViewModel.onGalleryEvent(event: GalleryEvent) {
    updateData { preference ->
        when (event) {
            is GalleryEvent.SupportAllGallerySettings -> preference.copy(
                gallery = preference.gallery.copy(
                    supportAllGallerySettings = event.value
                )
            )
        }
    }
}
