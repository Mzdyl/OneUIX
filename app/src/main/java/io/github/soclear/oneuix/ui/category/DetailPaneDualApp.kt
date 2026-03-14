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
fun DetailPaneDualApp(
    uiState: Preference.DualApp,
    onEvent: (DualAppEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.apk_document),
            title = stringResource(id = R.string.makeAllUserAppsAvailable_title),
            checked = uiState.makeAllUserAppsAvailable,
            onCheckedChange = { onEvent(DualAppEvent.MakeAllUserAppsAvailable(it)) }
        )
    }
}

sealed interface DualAppEvent {
    @JvmInline
    value class MakeAllUserAppsAvailable(val value: Boolean) : DualAppEvent
}

fun SettingViewModel.onDualAppEvent(event: DualAppEvent) {
    updateData { preference ->
        when (event) {
            is DualAppEvent.MakeAllUserAppsAvailable -> preference.copy(
                dualApp = preference.dualApp.copy(
                    makeAllUserAppsAvailable = event.value
                )
            )
        }
    }
}
