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
fun DetailPaneSmartSuggestions(
    uiState: Preference.Other,
    onEvent: (SmartSuggestionsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apps),
            title = stringResource(id = R.string.smartSuggestionsUnlock_title),
            summary = stringResource(id = R.string.smartSuggestionsUnlock_summary),
            checked = uiState.unlockSmartSuggestions,
            onCheckedChange = { onEvent(SmartSuggestionsEvent.Unlock(it)) }
        )
        if (uiState.unlockSmartSuggestions) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.language_us),
                title = stringResource(id = R.string.smartSuggestionsChatTranslation_title),
                summary = stringResource(id = R.string.smartSuggestionsChatTranslation_summary),
                checked = uiState.smartSuggestionsChatTranslation,
                onCheckedChange = { onEvent(SmartSuggestionsEvent.ChatTranslation(it)) }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.globe),
                title = stringResource(id = R.string.smartSuggestionsGlobalCards_title),
                summary = stringResource(id = R.string.smartSuggestionsGlobalCards_summary),
                checked = uiState.smartSuggestionsGlobalCards,
                onCheckedChange = { onEvent(SmartSuggestionsEvent.GlobalCards(it)) }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.health_metrics),
                title = stringResource(id = R.string.smartSuggestionsHealthCards_title),
                summary = stringResource(id = R.string.smartSuggestionsHealthCards_summary),
                checked = uiState.smartSuggestionsHealthCards,
                onCheckedChange = { onEvent(SmartSuggestionsEvent.HealthCards(it)) }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.brightness_auto),
                title = stringResource(id = R.string.smartSuggestionsNowNudge_title),
                summary = stringResource(id = R.string.smartSuggestionsNowNudge_summary),
                checked = uiState.smartSuggestionsNowNudge,
                onCheckedChange = { onEvent(SmartSuggestionsEvent.NowNudge(it)) }
            )
        }
    }
}

sealed interface SmartSuggestionsEvent {
    @JvmInline
    value class Unlock(val value: Boolean) : SmartSuggestionsEvent

    @JvmInline
    value class ChatTranslation(val value: Boolean) : SmartSuggestionsEvent

    @JvmInline
    value class GlobalCards(val value: Boolean) : SmartSuggestionsEvent

    @JvmInline
    value class HealthCards(val value: Boolean) : SmartSuggestionsEvent

    @JvmInline
    value class NowNudge(val value: Boolean) : SmartSuggestionsEvent
}

fun SettingViewModel.onSmartSuggestionsEvent(event: SmartSuggestionsEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is SmartSuggestionsEvent.Unlock ->
                preference.other.copy(unlockSmartSuggestions = event.value)
            is SmartSuggestionsEvent.ChatTranslation ->
                preference.other.copy(smartSuggestionsChatTranslation = event.value)
            is SmartSuggestionsEvent.GlobalCards ->
                preference.other.copy(smartSuggestionsGlobalCards = event.value)
            is SmartSuggestionsEvent.HealthCards ->
                preference.other.copy(smartSuggestionsHealthCards = event.value)
            is SmartSuggestionsEvent.NowNudge ->
                preference.other.copy(smartSuggestionsNowNudge = event.value)
        })
    }
}
