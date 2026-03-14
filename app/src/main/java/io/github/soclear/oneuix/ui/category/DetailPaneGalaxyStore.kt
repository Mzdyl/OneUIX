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
import io.github.soclear.oneuix.ui.component.DropdownItem
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneGalaxyStore(
    uiState: Preference.GalaxyStore,
    onEvent: (GalaxyStoreEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ad_off),
            title = stringResource(id = R.string.blockGalaxyStoreAds_title),
            summary = stringResource(id = R.string.blockGalaxyStoreAds_summary),
            checked = uiState.blockGalaxyStoreAds,
            onCheckedChange = { onEvent(GalaxyStoreEvent.BlockGalaxyStoreAds(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.globe),
            title = stringResource(id = R.string.galaxyStoreChangeRegion_title),
            summary = stringResource(id = R.string.galaxyStoreChangeRegion_summary),
            checked = uiState.changeRegion,
            onCheckedChange = { onEvent(GalaxyStoreEvent.ChangeRegion(it)) }
        )
        if (uiState.changeRegion) {
            DropdownItem(
                icon = ImageVector.vectorResource(id = R.drawable.map),
                title = stringResource(id = R.string.galaxyStoreRegion_title),
                summary = stringResource(id = R.string.galaxyStoreRegion_summary),
                options = listOf(
                    "US" to stringResource(id = R.string.region_us),
                    "CN" to stringResource(id = R.string.region_cn),
                    "HK" to stringResource(id = R.string.region_hk),
                    "TW" to stringResource(id = R.string.region_tw),
                    "JP" to stringResource(id = R.string.region_jp),
                    "KR" to stringResource(id = R.string.region_kr),
                    "UK" to stringResource(id = R.string.region_uk),
                    "DE" to stringResource(id = R.string.region_de),
                ),
                selectedOption = uiState.regionCode,
                onOptionSelected = { onEvent(GalaxyStoreEvent.RegionCode(it)) }
            )
        }
    }
}

sealed interface GalaxyStoreEvent {
    @JvmInline
    value class BlockGalaxyStoreAds(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class ChangeRegion(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class RegionCode(val value: String) : GalaxyStoreEvent
}

fun SettingViewModel.onGalaxyStoreEvent(event: GalaxyStoreEvent) {
    updateData { preference ->
        when (event) {
            is GalaxyStoreEvent.BlockGalaxyStoreAds -> preference.copy(
                galaxyStore = preference.galaxyStore.copy(
                    blockGalaxyStoreAds = event.value
                )
            )

            is GalaxyStoreEvent.ChangeRegion -> preference.copy(
                galaxyStore = preference.galaxyStore.copy(
                    changeRegion = event.value
                )
            )

            is GalaxyStoreEvent.RegionCode -> preference.copy(
                galaxyStore = preference.galaxyStore.copy(
                    regionCode = event.value
                )
            )
        }
    }
}
