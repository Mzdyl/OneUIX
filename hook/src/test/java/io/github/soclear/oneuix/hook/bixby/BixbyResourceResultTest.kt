package io.github.soclear.oneuix.hook.bixby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BixbyResourceResultTest {
    private class Presenter
    private class SharedCallback(val target: Any, val caseId: Int)
    private class AmbiguousCallback(val first: Any, val second: Any)

    @Test
    fun reconcilesOnlyStaleFailuresWithReadyResources() {
        assertEquals(1, reconcileResourceResult(-1, 100))
        for (state in listOf(null, 0, 1, 3, 4, 101)) {
            assertEquals(-1, reconcileResourceResult(-1, state))
        }
        for (result in listOf(-2, 0, 1, 2)) {
            assertEquals(result, reconcileResourceResult(result, 100))
        }
    }

    @Test
    fun sharedLambdaMustCaptureTheWakeupPresenter() {
        val presenter = Presenter()
        assertSame(presenter, capturedPresenter(SharedCallback(presenter, 17), Presenter::class.java))
        assertNull(capturedPresenter(SharedCallback("another feature", 17), Presenter::class.java))
        assertNull(capturedPresenter(AmbiguousCallback(presenter, Presenter()), Presenter::class.java))
    }
}
