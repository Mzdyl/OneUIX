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
fun DetailPaneThemeCenter(
    uiState: Preference.ThemeCenter,
    onEvent: (ThemeCenterEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.format_paint),
            title = stringResource(id = R.string.setThemeTrialNeverExpired_title),
            checked = uiState.setThemeTrialNeverExpired,
            onCheckedChange = { onEvent(ThemeCenterEvent.SetThemeTrialNeverExpired(it)) }
        )
    }
}

sealed interface ThemeCenterEvent {
    @JvmInline
    value class SetThemeTrialNeverExpired(val value: Boolean) : ThemeCenterEvent
}

fun SettingViewModel.onThemeCenterEvent(event: ThemeCenterEvent) {
    updateData { preference ->
        when (event) {
            is ThemeCenterEvent.SetThemeTrialNeverExpired -> preference.copy(
                themeCenter = preference.themeCenter.copy(
                    setThemeTrialNeverExpired = event.value
                )
            )
        }
    }
}
