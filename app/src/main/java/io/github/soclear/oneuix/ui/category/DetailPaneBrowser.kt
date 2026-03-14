package io.github.soclear.oneuix.ui.category

import android.annotation.SuppressLint
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
        if (isCHC) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.search),
                title = stringResource(id = R.string.customizeBrowserSearchEngine_title),
                summary = stringResource(id = R.string.customizeBrowserSearchEngine_summary),
                checked = uiState.customizeBrowserSearchEngine,
                onCheckedChange = { onEvent(BrowserEvent.CustomizeBrowserSearchEngine(it)) }
            )
        }
    }
}

private val isCHC by lazy {
    try {
        @SuppressLint("PrivateApi")
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
        getMethod(null, "ro.csc.sales_code") == "CHC"
    } catch (_: Throwable) {
        false
    }
}

sealed interface BrowserEvent {
    @JvmInline
    value class CustomizeBrowserSearchEngine(val value: Boolean) : BrowserEvent
}

fun SettingViewModel.onBrowserEvent(event: BrowserEvent) {
    updateData { preference ->
        when (event) {
            is BrowserEvent.CustomizeBrowserSearchEngine -> preference.copy(
                browser = preference.browser.copy(
                    customizeBrowserSearchEngine = event.value
                )
            )
        }
    }
}
