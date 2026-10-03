package io.github.soclear.oneuix.util

import io.github.soclear.oneuix.common.Preference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NfcStateManager(
    private val read: suspend () -> Preference.Nfc,
    private val update: suspend ((Preference.Nfc) -> Preference.Nfc) -> Unit,
    private val applyCard: suspend (Preference.NfcCard) -> Result<String>,
    private val resetCard: suspend () -> Result<String>,
) {
    private val mutex = Mutex()
    private val busyState = MutableStateFlow(false)
    val busy = busyState.asStateFlow()

    private suspend fun execute(action: suspend () -> Unit): Result<Unit> = mutex.withLock {
        busyState.value = true
        try {
            action()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            busyState.value = false
        }
    }

    private suspend fun activate(card: Preference.NfcCard) {
        val appliedUid = applyCard(card).getOrThrow()
        update {
            it.copy(
                enableSimulation = true,
                activeUid = appliedUid,
                activeCardName = card.name,
                activeSak = card.sak,
                activeAtqa = card.atqa
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = execute {
        if (enabled) {
            val state = read()
            val card = if (state.activeUid.isNotEmpty()) {
                Preference.NfcCard(
                    name = state.activeCardName, uid = state.activeUid,
                    sak = state.activeSak, atqa = state.activeAtqa
                )
            } else state.cards.firstOrNull() ?: throw NoSuchElementException("No saved NFC card")
            activate(card)
        } else {
            resetCard().getOrThrow()
            update { it.copy(enableSimulation = false) }
        }
    }

    suspend fun emulate(card: Preference.NfcCard): Result<Unit> = execute { activate(card) }

    suspend fun reset(): Result<Unit> = execute {
        resetCard().getOrThrow()
        update { it.withoutActiveCard() }
    }

    suspend fun add(card: Preference.NfcCard, emulateImmediately: Boolean): Result<Unit> = execute {
        update { state ->
            val index = state.cards.indexOfFirst { NfcController.cleanUid(it.uid) == NfcController.cleanUid(card.uid) }
            val cards = state.cards.toMutableList()
            if (index >= 0) cards[index] = card.copy(id = cards[index].id) else cards.add(card)
            state.copy(cards = cards)
        }
        if (emulateImmediately) activate(card)
    }

    suspend fun delete(card: Preference.NfcCard): Result<Unit> = execute {
        val active = NfcController.cleanUid(read().activeUid) == NfcController.cleanUid(card.uid)
        if (active) resetCard().getOrThrow()
        update { state ->
            val next = if (active) state.withoutActiveCard() else state
            next.copy(cards = next.cards.filterNot { it.id == card.id })
        }
    }

    suspend fun rename(card: Preference.NfcCard, name: String): Result<Unit> = execute {
        update { state ->
            state.copy(
                activeCardName = if (NfcController.cleanUid(state.activeUid) == NfcController.cleanUid(card.uid)) name else state.activeCardName,
                cards = state.cards.map { if (it.id == card.id) it.copy(name = name) else it }
            )
        }
    }

    suspend fun setBypassPrompt(enabled: Boolean): Result<Unit> = execute {
        update { it.copy(bypassPrompt = enabled) }
    }

    private fun Preference.Nfc.withoutActiveCard() = copy(
        enableSimulation = false, activeUid = "", activeCardName = "", activeSak = "04", activeAtqa = "00"
    )
}
