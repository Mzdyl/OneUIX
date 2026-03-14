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
fun DetailPaneNotes(
    uiState: Preference.Notes,
    onEvent: (NotesEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.notes),
            title = stringResource(id = R.string.supportAllNotesFeatures_title),
            summary = stringResource(id = R.string.supportAllNotesFeatures_summary),
            checked = uiState.supportAllNotesFeatures,
            onCheckedChange = { onEvent(NotesEvent.SupportAllNotesFeatures(it)) }
        )
    }
}

sealed interface NotesEvent {
    @JvmInline
    value class SupportAllNotesFeatures(val value: Boolean) : NotesEvent
}

fun SettingViewModel.onNotesEvent(event: NotesEvent) {
    updateData { preference ->
        when (event) {
            is NotesEvent.SupportAllNotesFeatures -> preference.copy(
                notes = preference.notes.copy(
                    supportAllNotesFeatures = event.value
                )
            )
        }
    }
}
