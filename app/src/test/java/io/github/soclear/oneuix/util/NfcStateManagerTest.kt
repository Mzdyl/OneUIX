package io.github.soclear.oneuix.util

import io.github.soclear.oneuix.common.Preference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class NfcStateManagerTest {
    private val cardA = Preference.NfcCard(id = "a", name = "A", uid = "01:02:03:04")
    private val cardB = Preference.NfcCard(id = "b", name = "B", uid = "05:06:07:08")
    private val active = Preference.Nfc(
        enableSimulation = true, activeUid = cardA.uid, activeCardName = cardA.name,
        cards = listOf(cardA, cardB)
    )

    private class Fixture(initial: Preference.Nfc) {
        var state = initial
        var apply: suspend (Preference.NfcCard) -> Result<String> = { Result.success(it.uid) }
        var reset: suspend () -> Result<String> = { Result.success("reset") }
        val manager = NfcStateManager(
            read = { state },
            update = { state = it(state) },
            applyCard = { apply(it) },
            resetCard = { reset() }
        )
    }

    @Test
    fun failedResetDisableAndDeletePreserveActiveCardAndSavedCards() = runBlocking {
        val fixture = Fixture(active)
        fixture.reset = { Result.failure(IllegalStateException("Root denied")) }
        assertTrue(fixture.manager.reset().isFailure)
        assertEquals(active, fixture.state)
        assertTrue(fixture.manager.setEnabled(false).isFailure)
        assertEquals(active, fixture.state)
        assertTrue(fixture.manager.delete(cardA).isFailure)
        assertEquals(active, fixture.state)
        assertFalse(fixture.manager.busy.value)
    }

    @Test
    fun failedSwitchKeepsPreviouslySelectedCard() = runBlocking {
        val fixture = Fixture(active)
        fixture.apply = { Result.failure(IllegalStateException("Module deployment failed")) }
        assertTrue(fixture.manager.emulate(cardB).isFailure)
        assertEquals(active, fixture.state)
    }

    @Test
    fun consecutiveChangesAreSerializedAndOnlySavedAfterSuccess() = runBlocking {
        val fixture = Fixture(active)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        fixture.apply = {
            calls += it.uid
            started.complete(Unit)
            release.await()
            Result.success(it.uid)
        }
        fixture.reset = { calls += "reset"; Result.success("reset") }
        val change = async { fixture.manager.emulate(cardB) }
        started.await()
        val reset = async { fixture.manager.reset() }
        yield()
        assertEquals(listOf(cardB.uid), calls)
        assertEquals(active, fixture.state)
        assertTrue(fixture.manager.busy.value)
        release.complete(Unit)
        assertTrue(change.await().isSuccess)
        assertTrue(reset.await().isSuccess)
        assertEquals(listOf(cardB.uid, "reset"), calls)
        assertFalse(fixture.state.enableSimulation)
        assertEquals("", fixture.state.activeUid)
        assertFalse(fixture.manager.busy.value)
    }

    @Test
    fun cancellationReleasesBusyStateAndAllowsNextOperation() = runBlocking {
        val fixture = Fixture(active)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        fixture.apply = {
            started.complete(Unit)
            never.await()
            Result.success(it.uid)
        }
        val job = launch { fixture.manager.emulate(cardB) }
        started.await()
        job.cancelAndJoin()
        assertEquals(active, fixture.state)
        assertFalse(fixture.manager.busy.value)
        assertTrue(fixture.manager.reset().isSuccess)
    }

    @Test
    fun failedImmediateActivationStillSavesNewCardWithoutReplacingActiveCard() = runBlocking {
        val fixture = Fixture(active.copy(cards = listOf(cardA)))
        fixture.apply = { Result.failure(IllegalStateException("Apply failed")) }
        assertTrue(fixture.manager.add(cardB, true).isFailure)
        assertEquals(listOf(cardA, cardB), fixture.state.cards)
        assertEquals(cardA.uid, fixture.state.activeUid)
        assertTrue(fixture.state.enableSimulation)
    }

    @Test
    fun addingExistingUidKeepsStableIdAndDeletingInactiveCardDoesNotResetHardware() = runBlocking {
        val fixture = Fixture(active.copy(cards = listOf(cardA.copy(uid = "01020304"), cardB)))
        fixture.reset = { error("Must not reset an inactive card") }
        assertTrue(fixture.manager.add(cardA.copy(id = "new", name = "Renamed"), false).isSuccess)
        assertEquals(2, fixture.state.cards.size)
        assertEquals("a", fixture.state.cards.first().id)
        assertEquals("Renamed", fixture.state.cards.first().name)
        assertTrue(fixture.manager.delete(cardB).isSuccess)
        assertTrue(fixture.state.enableSimulation)
    }
}
