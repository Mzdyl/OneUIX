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
fun DetailPaneQuickShare(
    uiState: Preference.Other,
    onEvent: (QuickShareEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.share),
            title = stringResource(id = R.string.enableGoogleQuickShare_title),
            summary = stringResource(id = R.string.enableGoogleQuickShare_summary),
            checked = uiState.enableGoogleQuickShare,
            onCheckedChange = { onEvent(QuickShareEvent.EnableGoogleQuickShare(it)) }
        )
    }
}

sealed interface QuickShareEvent {
    @JvmInline
    value class EnableGoogleQuickShare(val value: Boolean) : QuickShareEvent
}

fun SettingViewModel.onQuickShareEvent(event: QuickShareEvent) {
    updateData { preference ->
        when (event) {
            is QuickShareEvent.EnableGoogleQuickShare -> preference.copy(
                other = preference.other.copy(
                    enableGoogleQuickShare = event.value
                )
            )
        }
    }
}
