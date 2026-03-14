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
fun DetailPaneWeather(
    uiState: Preference.Weather,
    onEvent: (WeatherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.partly_cloudy_day),
            title = stringResource(id = R.string.setWeatherProviderCN_title),
            checked = uiState.setWeatherProviderCN,
            onCheckedChange = { onEvent(WeatherEvent.SetWeatherProviderCN(it)) }
        )
    }
}

sealed interface WeatherEvent {
    @JvmInline
    value class SetWeatherProviderCN(val value: Boolean) : WeatherEvent
}

fun SettingViewModel.onWeatherEvent(event: WeatherEvent) {
    updateData { preference ->
        when (event) {
            is WeatherEvent.SetWeatherProviderCN -> preference.copy(
                weather = preference.weather.copy(
                    setWeatherProviderCN = event.value
                )
            )
        }
    }
}
