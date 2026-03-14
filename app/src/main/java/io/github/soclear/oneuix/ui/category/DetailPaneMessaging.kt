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
fun DetailPaneMessaging(
    uiState: Preference.Messaging,
    onEvent: (MessagingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.sms),
            title = stringResource(id = R.string.supportBlockMessage_title),
            checked = uiState.supportBlockMessage,
            onCheckedChange = { onEvent(MessagingEvent.SupportBlockMessage(it)) }
        )
    }
}

sealed interface MessagingEvent {
    @JvmInline
    value class SupportBlockMessage(val value: Boolean) : MessagingEvent
}

fun SettingViewModel.onMessagingEvent(event: MessagingEvent) {
    updateData { preference ->
        when (event) {
            is MessagingEvent.SupportBlockMessage -> preference.copy(
                messaging = preference.messaging.copy(
                    supportBlockMessage = event.value
                )
            )
        }
    }
}
