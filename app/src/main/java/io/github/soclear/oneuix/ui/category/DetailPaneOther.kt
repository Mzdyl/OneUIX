package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.SettingViewModel

@Composable
fun DetailPaneOther(
    uiState: Preference.Other,
    onEvent: (OtherEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Other 类别保留给模块自身设置或未来功能
    }
}

sealed interface OtherEvent

fun SettingViewModel.onOtherEvent(event: OtherEvent) {
    // 暂无事件处理
}