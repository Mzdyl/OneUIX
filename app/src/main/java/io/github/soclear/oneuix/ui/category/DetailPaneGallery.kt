package io.github.soclear.oneuix.ui.category

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
    uiState: Preference.Other,
    onEvent: (GalleryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportAllGallerySettings_title),
            summary = stringResource(id = R.string.supportAllGallerySettings_summary),
            checked = uiState.supportAllGallerySettings,
            onCheckedChange = { onEvent(GalleryEvent.SupportAllGallerySettings(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportSharedAlbumsInHide_title),
            summary = stringResource(id = R.string.supportSharedAlbumsInHide_summary),
            checked = uiState.supportSharedAlbumsInHide,
            onCheckedChange = { onEvent(GalleryEvent.SupportSharedAlbumsInHide(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.hideVideoEditorStudio_title),
            checked = uiState.hideVideoEditorStudio,
            onCheckedChange = { onEvent(GalleryEvent.HideVideoEditorStudio(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportGalleryGoogleSync_title),
            summary = stringResource(id = R.string.supportGalleryGoogleSync_summary),
            checked = uiState.supportGalleryGoogleSync,
            onCheckedChange = { onEvent(GalleryEvent.SupportGalleryGoogleSync(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.supportGalleryCloudSyncToolbar_title),
            summary = stringResource(id = R.string.supportGalleryCloudSyncToolbar_summary),
            checked = uiState.supportGalleryCloudSyncToolbar,
            onCheckedChange = { onEvent(GalleryEvent.SupportGalleryCloudSyncToolbar(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryPoi_title),
            summary = stringResource(id = R.string.enableGalleryPoi_summary),
            checked = uiState.enableGalleryPoi,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryPoi(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryFullAddress_title),
            summary = stringResource(id = R.string.enableGalleryFullAddress_summary),
            checked = uiState.enableGalleryFullAddress,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryFullAddress(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.unlockGalleryDeveloperLabs_title),
            summary = stringResource(id = R.string.unlockGalleryDeveloperLabs_summary),
            checked = uiState.unlockGalleryDeveloperLabs,
            onCheckedChange = { onEvent(GalleryEvent.UnlockGalleryDeveloperLabs(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryUndoDelete_title),
            summary = stringResource(id = R.string.enableGalleryUndoDelete_summary),
            checked = uiState.enableGalleryUndoDelete,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryUndoDelete(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryDualPhotoPreview_title),
            summary = stringResource(id = R.string.enableGalleryDualPhotoPreview_summary),
            checked = uiState.enableGalleryDualPhotoPreview,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryDualPhotoPreview(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryQuickSeek_title),
            summary = stringResource(id = R.string.enableGalleryQuickSeek_summary),
            checked = uiState.enableGalleryQuickSeek,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryQuickSeek(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryHdrThumbnail_title),
            summary = stringResource(id = R.string.enableGalleryHdrThumbnail_summary),
            checked = uiState.enableGalleryHdrThumbnail,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryHdrThumbnail(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryPhotoStripHighQuality_title),
            summary = stringResource(id = R.string.enableGalleryPhotoStripHighQuality_summary),
            checked = uiState.enableGalleryPhotoStripHighQuality,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryPhotoStripHighQuality(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.photo_library),
            title = stringResource(id = R.string.enableGalleryUnmuteAlways_title),
            summary = stringResource(id = R.string.enableGalleryUnmuteAlways_summary),
            checked = uiState.enableGalleryUnmuteAlways,
            onCheckedChange = { onEvent(GalleryEvent.EnableGalleryUnmuteAlways(it)) }
        )
    }
}

sealed interface GalleryEvent {
    @JvmInline
    value class SupportAllGallerySettings(val value: Boolean) : GalleryEvent

    @JvmInline
    value class SupportSharedAlbumsInHide(val value: Boolean) : GalleryEvent

    @JvmInline
    value class HideVideoEditorStudio(val value: Boolean) : GalleryEvent

    @JvmInline
    value class SupportGalleryGoogleSync(val value: Boolean) : GalleryEvent

    @JvmInline
    value class SupportGalleryCloudSyncToolbar(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryPoi(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryFullAddress(val value: Boolean) : GalleryEvent

    @JvmInline
    value class UnlockGalleryDeveloperLabs(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryUndoDelete(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryDualPhotoPreview(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryQuickSeek(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryHdrThumbnail(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryPhotoStripHighQuality(val value: Boolean) : GalleryEvent

    @JvmInline
    value class EnableGalleryUnmuteAlways(val value: Boolean) : GalleryEvent
}

fun SettingViewModel.onGalleryEvent(event: GalleryEvent) {
    updateData { preference ->
        when (event) {
            is GalleryEvent.SupportAllGallerySettings -> preference.copy(
                other = preference.other.copy(
                    supportAllGallerySettings = event.value
                )
            )

            is GalleryEvent.SupportSharedAlbumsInHide -> preference.copy(
                other = preference.other.copy(
                    supportSharedAlbumsInHide = event.value
                )
            )

            is GalleryEvent.HideVideoEditorStudio -> preference.copy(
                other = preference.other.copy(
                    hideVideoEditorStudio = event.value
                )
            )

            is GalleryEvent.SupportGalleryGoogleSync -> preference.copy(
                other = preference.other.copy(
                    supportGalleryGoogleSync = event.value
                )
            )

            is GalleryEvent.SupportGalleryCloudSyncToolbar -> preference.copy(
                other = preference.other.copy(
                    supportGalleryCloudSyncToolbar = event.value
                )
            )

            is GalleryEvent.EnableGalleryPoi -> preference.copy(
                other = preference.other.copy(
                    enableGalleryPoi = event.value
                )
            )

            is GalleryEvent.EnableGalleryFullAddress -> preference.copy(
                other = preference.other.copy(
                    enableGalleryFullAddress = event.value
                )
            )

            is GalleryEvent.UnlockGalleryDeveloperLabs -> preference.copy(
                other = preference.other.copy(
                    unlockGalleryDeveloperLabs = event.value
                )
            )

            is GalleryEvent.EnableGalleryUndoDelete -> preference.copy(
                other = preference.other.copy(
                    enableGalleryUndoDelete = event.value
                )
            )

            is GalleryEvent.EnableGalleryDualPhotoPreview -> preference.copy(
                other = preference.other.copy(
                    enableGalleryDualPhotoPreview = event.value
                )
            )

            is GalleryEvent.EnableGalleryQuickSeek -> preference.copy(
                other = preference.other.copy(
                    enableGalleryQuickSeek = event.value
                )
            )

            is GalleryEvent.EnableGalleryHdrThumbnail -> preference.copy(
                other = preference.other.copy(
                    enableGalleryHdrThumbnail = event.value
                )
            )

            is GalleryEvent.EnableGalleryPhotoStripHighQuality -> preference.copy(
                other = preference.other.copy(
                    enableGalleryPhotoStripHighQuality = event.value
                )
            )

            is GalleryEvent.EnableGalleryUnmuteAlways -> preference.copy(
                other = preference.other.copy(
                    enableGalleryUnmuteAlways = event.value
                )
            )
        }
    }
}
