package io.github.soclear.oneuix.ui.category

import android.os.Build
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
fun DetailPaneLauncher(
    uiState: Preference.Launcher,
    onEvent: (LauncherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.memory),
                title = stringResource(id = R.string.showMemoryUsageInRecents_title),
                checked = uiState.showMemoryUsageInRecents,
                onCheckedChange = { onEvent(LauncherEvent.ShowMemoryUsageInRecents(it)) }
            )
        }
    }
}

sealed interface LauncherEvent {
    @JvmInline
    value class ShowMemoryUsageInRecents(val value: Boolean) : LauncherEvent
}

fun SettingViewModel.onLauncherEvent(event: LauncherEvent) {
    updateData { preference ->
        when (event) {
            is LauncherEvent.ShowMemoryUsageInRecents -> preference.copy(
                launcher = preference.launcher.copy(
                    showMemoryUsageInRecents = event.value
                )
            )
        }
    }
}
