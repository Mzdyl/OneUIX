package io.github.soclear.oneuix.ui.category

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem
import io.github.soclear.oneuix.util.NfcController
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

data class ScannedNfcCard(
    val uid: String,
    val sak: String = "04",
    val atqa: String = "00",
)

object NfcScanChannel {
    val scannedCard = MutableSharedFlow<ScannedNfcCard>(replay = 1, extraBufferCapacity = 1)

    fun onTagScanned(uid: String, sak: String = "04", atqa: String = "00") {
        scannedCard.tryEmit(ScannedNfcCard(uid, sak, atqa))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clear() {
        scannedCard.resetReplayCache()
    }
}

@Composable
fun DetailPaneNfc(
    uiState: Preference.Nfc,
    onEvent: (NfcEvent) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    var showScanDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var cardToRename by remember { mutableStateOf<Preference.NfcCard?>(null) }
    var cardToDelete by remember { mutableStateOf<Preference.NfcCard?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SwitchItem(
            title = stringResource(R.string.nfc_bypass_prompt_title),
            summary = stringResource(R.string.nfc_bypass_prompt_summary),
            icon = ImageVector.vectorResource(R.drawable.shield_off),
            checked = uiState.bypassPrompt,
            onCheckedChange = { onEvent(NfcEvent.ToggleBypassPrompt(it)) },
        )

        SwitchItem(
            title = stringResource(R.string.nfc_simulation_title),
            summary = stringResource(if (busy) R.string.operation_in_progress else R.string.nfc_simulation_summary),
            icon = ImageVector.vectorResource(R.drawable.ic_nfc),
            checked = uiState.enableSimulation,
            enabled = !busy,
            onCheckedChange = { onEvent(NfcEvent.ToggleSimulation(it)) },
        )

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.nfc_active_card),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.enableSimulation && uiState.activeUid.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.nfc_emulating),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.enableSimulation && uiState.activeUid.isNotEmpty()) {
                    Text(
                        text = uiState.activeCardName.ifBlank { "已激活卡片" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.nfc_active_uid, uiState.activeUid)}  (SAK: ${uiState.activeSak})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { onEvent(NfcEvent.ResetSimulation) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.nfc_btn_reset_simulation))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.nfc_no_active_card),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showScanDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_nfc),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.nfc_btn_scan))
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.nfc_btn_add_manual))
            }
        }

        Text(
            text = stringResource(R.string.nfc_saved_cards),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (uiState.cards.isEmpty()) {
            Text(
                text = stringResource(R.string.nfc_empty_cards),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            uiState.cards.forEach { card ->
                val isActive = uiState.enableSimulation && uiState.activeUid == card.uid
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = card.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${card.uid}  •  SAK: ${card.sak}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.nfc_emulating),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(enabled = !busy, onClick = { cardToRename = card }) {
                                Text(text = stringResource(R.string.nfc_dialog_rename_title))
                            }
                            TextButton(enabled = !busy, onClick = { cardToDelete = card }) {
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            if (isActive) {
                                OutlinedButton(onClick = { onEvent(NfcEvent.ResetSimulation) }) {
                                    Text(text = stringResource(R.string.nfc_btn_deactivate))
                                }
                            } else {
                                Button(enabled = !busy, onClick = { onEvent(NfcEvent.EmulateCard(card)) }) {
                                    Text(text = stringResource(R.string.nfc_emulate_this))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        ScanCardDialog(
            onDismiss = {
                NfcScanChannel.clear()
                showScanDialog = false
            },
            onSave = { name, uid, sak, atqa, emulateImmediately ->
                onEvent(NfcEvent.AddCard(name, uid, sak, atqa, emulateImmediately))
                NfcScanChannel.clear()
                showScanDialog = false
            }
        )
    }

    if (showAddDialog) {
        ManualAddCardDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, uid, sak, atqa, emulateImmediately ->
                onEvent(NfcEvent.AddCard(name, uid, sak, atqa, emulateImmediately))
                showAddDialog = false
            }
        )
    }

    cardToRename?.let { card ->
        var newName by remember { mutableStateOf(card.name) }
        AlertDialog(
            onDismissRequest = { cardToRename = null },
            title = { Text(text = stringResource(R.string.nfc_dialog_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.nfc_card_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onEvent(NfcEvent.RenameCard(card, newName.trim()))
                        }
                        cardToRename = null
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToRename = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text(text = stringResource(R.string.nfc_dialog_delete_title)) },
            text = { Text(text = stringResource(R.string.nfc_dialog_delete_confirm, card.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onEvent(NfcEvent.DeleteCard(card))
                        cardToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ScanCardDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, uid: String, sak: String, atqa: String, emulateImmediately: Boolean) -> Unit
) {
    var scannedUid by remember { mutableStateOf("") }
    var scannedSak by remember { mutableStateOf("04") }
    var scannedAtqa by remember { mutableStateOf("00") }
    var cardName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        NfcScanChannel.clear()
        NfcScanChannel.scannedCard.collect { card ->
            scannedUid = card.uid
            scannedSak = card.sak
            scannedAtqa = card.atqa
            if (cardName.isBlank()) {
                val cardType = when (card.sak) {
                    "08" -> "M1"
                    "00" -> "UL"
                    "20" -> "CPU"
                    else -> "门禁"
                }
                cardName = "${cardType}卡_${card.uid.replace(":", "").takeLast(4)}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_nfc),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.nfc_dialog_scan_title))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (scannedUid.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.nfc_dialog_scan_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.nfc_dialog_scanned_success),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UID: $scannedUid",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "SAK: $scannedSak   ATQA: $scannedAtqa",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            val cardTypeDesc = when (scannedSak.uppercase()) {
                                "08" -> "Mifare Classic 1K (标准IC门禁卡)"
                                "00" -> "Mifare Ultralight"
                                "20" -> "CPU卡 / 复合卡 (DESFire)"
                                "18" -> "Mifare Classic 4K"
                                "28" -> "Mifare Classic 1K (仿真)"
                                else -> "标准 NFC-A 卡片"
                            }
                            Text(
                                text = "卡片类型: $cardTypeDesc",
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = cardName,
                        onValueChange = { cardName = it },
                        label = { Text(stringResource(R.string.nfc_card_name)) },
                        placeholder = { Text(stringResource(R.string.nfc_card_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = scannedUid,
                        onValueChange = { scannedUid = it },
                        label = { Text(stringResource(R.string.nfc_card_uid)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (scannedUid.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSave(cardName, scannedUid, scannedSak, scannedAtqa, false) }) {
                        Text(stringResource(R.string.nfc_btn_save_only))
                    }
                    Button(onClick = { onSave(cardName, scannedUid, scannedSak, scannedAtqa, true) }) {
                        Text(stringResource(R.string.nfc_btn_save_and_emulate))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ManualAddCardDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, uid: String, sak: String, atqa: String, emulateImmediately: Boolean) -> Unit
) {
    var cardName by remember { mutableStateOf("") }
    var cardUid by remember { mutableStateOf("") }
    var cardSak by remember { mutableStateOf("04") }
    var cardAtqa by remember { mutableStateOf("00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.nfc_btn_add_manual)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text(stringResource(R.string.nfc_card_name)) },
                    placeholder = { Text(stringResource(R.string.nfc_card_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cardUid,
                    onValueChange = { cardUid = it },
                    label = { Text(stringResource(R.string.nfc_card_uid)) },
                    placeholder = { Text(stringResource(R.string.nfc_card_uid_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cardSak,
                        onValueChange = { cardSak = it },
                        label = { Text("SAK") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cardAtqa,
                        onValueChange = { cardAtqa = it },
                        label = { Text("ATQA") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (cardUid.isNotBlank()) onSave(cardName, cardUid.trim(), cardSak.trim(), cardAtqa.trim(), false)
                    }
                ) {
                    Text(stringResource(R.string.nfc_btn_save_only))
                }
                Button(
                    onClick = {
                        if (cardUid.isNotBlank()) onSave(cardName, cardUid.trim(), cardSak.trim(), cardAtqa.trim(), true)
                    }
                ) {
                    Text(stringResource(R.string.nfc_btn_save_and_emulate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

sealed interface NfcEvent {
    data class ToggleBypassPrompt(val value: Boolean) : NfcEvent
    data class ToggleSimulation(val value: Boolean) : NfcEvent
    data class EmulateCard(val card: Preference.NfcCard) : NfcEvent
    data class AddCard(
        val name: String,
        val uid: String,
        val sak: String = "04",
        val atqa: String = "00",
        val emulateImmediately: Boolean
    ) : NfcEvent
    data class DeleteCard(val card: Preference.NfcCard) : NfcEvent
    data class RenameCard(val card: Preference.NfcCard, val newName: String) : NfcEvent
    data object ResetSimulation : NfcEvent
}

fun SettingViewModel.onNfcEvent(event: NfcEvent) {
    viewModelScope.launch {
        val manager = nfcStateManager
        val result = when (event) {
            is NfcEvent.ToggleBypassPrompt -> manager.setBypassPrompt(event.value)
            is NfcEvent.ToggleSimulation -> manager.setEnabled(event.value)
            is NfcEvent.EmulateCard -> manager.emulate(event.card)
            is NfcEvent.ResetSimulation -> manager.reset()
            is NfcEvent.DeleteCard -> manager.delete(event.card)
            is NfcEvent.RenameCard -> manager.rename(event.card, event.newName)
            is NfcEvent.AddCard -> {
                val clean = NfcController.cleanUid(event.uid)
                if (!NfcController.validateUid(clean)) {
                    Toast.makeText(application, R.string.nfc_toast_invalid_uid, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val formatted = NfcController.formatUid(clean)
                val card = Preference.NfcCard(
                    id = UUID.randomUUID().toString(),
                    name = event.name.ifBlank { formatted },
                    uid = formatted,
                    sak = event.sak.trim().uppercase().ifEmpty { "04" },
                    atqa = event.atqa.trim().uppercase().ifEmpty { "00" }
                )
                manager.add(card, event.emulateImmediately)
            }
        }
        if (result.isFailure) {
            val message = if (result.exceptionOrNull() is NoSuchElementException) {
                R.string.nfc_empty_cards
            } else R.string.operation_failed
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        } else {
            val reset = event is NfcEvent.ResetSimulation ||
                event is NfcEvent.ToggleSimulation && !event.value
            val uid = when (event) {
                is NfcEvent.EmulateCard -> event.card.uid
                is NfcEvent.AddCard -> if (event.emulateImmediately) {
                    NfcController.formatUid(NfcController.cleanUid(event.uid))
                } else null
                else -> null
            }
            if (reset) {
                Toast.makeText(application, R.string.nfc_toast_reset_success, Toast.LENGTH_SHORT).show()
            } else if (uid != null) {
                Toast.makeText(application, application.getString(R.string.nfc_toast_sim_success, uid), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
