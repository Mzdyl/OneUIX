package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.SPenTranslationSource
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SelectItem

@Composable
fun DetailPaneSPen(
    uiState: Preference.Other,
    onEvent: (SPenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        SelectItem(
            icon = ImageVector.vectorResource(R.drawable.spen),
            title = stringResource(R.string.sPenTranslationSource_title),
            summary = stringResource(R.string.sPenTranslationSource_summary),
            entries = listOf(
                stringResource(R.string.choice_default),
                stringResource(R.string.translation_google),
                stringResource(R.string.translation_baidu)
            ),
            selectedIndex = uiState.sPenTranslationSource.ordinal,
            onSelectedIndexChange = { onEvent(SPenEvent.TranslationSource(SPenTranslationSource.entries[it])) }
        )
    }
}

sealed interface SPenEvent {
    @JvmInline
    value class TranslationSource(val value: SPenTranslationSource) : SPenEvent
}

fun SettingViewModel.onSPenEvent(event: SPenEvent) {
    updateData { preference ->
        when (event) {
            is SPenEvent.TranslationSource -> preference.copy(
                other = preference.other.copy(
                    sPenTranslationSource = event.value
                )
            )
        }
    }
}
