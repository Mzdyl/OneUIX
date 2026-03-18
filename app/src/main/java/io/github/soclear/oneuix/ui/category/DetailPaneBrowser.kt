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
fun DetailPaneBrowser(
    uiState: Preference.Browser,
    onEvent: (BrowserEvent) -> Unit,
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
            onCheckedChange = { onEvent(BrowserEvent.ShowMorePlaybackSpeeds(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.language_us),
            title = stringResource(id = R.string.spoofBrowserCountryCodeToUS_title),
            summary = stringResource(id = R.string.spoofBrowserCountryCodeToUS_summary),
            checked = uiState.spoofBrowserCountryCodeToUS,
            onCheckedChange = { onEvent(BrowserEvent.SpoofBrowserCountryCodeToUS(it)) }
        )
    }
}

sealed interface BrowserEvent {
    @JvmInline
    value class ShowMorePlaybackSpeeds(val value: Boolean) : BrowserEvent

    @JvmInline
    value class SpoofBrowserCountryCodeToUS(val value: Boolean) : BrowserEvent
}

fun SettingViewModel.onBrowserEvent(event: BrowserEvent) {
    updateData { preference ->
        when (event) {
            is BrowserEvent.ShowMorePlaybackSpeeds -> preference.copy(
                browser = preference.browser.copy(
                    showMorePlaybackSpeeds = event.value
                )
            )

            is BrowserEvent.SpoofBrowserCountryCodeToUS -> preference.copy(
                browser = preference.browser.copy(
                    spoofBrowserCountryCodeToUS = event.value
                )
            )
        }
    }
}