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
fun DetailPaneSPen(
    uiState: Preference.SPen,
    onEvent: (SPenEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.spen),
            title = stringResource(id = R.string.useGoogleTranslate_title),
            summary = stringResource(id = R.string.useGoogleTranslate_summary),
            checked = uiState.useGoogleTranslate,
            onCheckedChange = { onEvent(SPenEvent.UseGoogleTranslate(it)) }
        )
    }
}

sealed interface SPenEvent {
    @JvmInline
    value class UseGoogleTranslate(val value: Boolean) : SPenEvent
}

fun SettingViewModel.onSPenEvent(event: SPenEvent) {
    updateData { preference ->
        when (event) {
            is SPenEvent.UseGoogleTranslate -> preference.copy(
                sPen = preference.sPen.copy(
                    useGoogleTranslate = event.value
                )
            )
        }
    }
}