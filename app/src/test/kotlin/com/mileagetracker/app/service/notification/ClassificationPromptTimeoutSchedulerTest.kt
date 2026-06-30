package com.mileagetracker.app.service.notification

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-039 item 9. Mirrors [com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindowTest]'s
 * injected-[TestScope] + `advanceTimeBy` shape exactly — virtual time only, no real device.
 *
 * Covers the two scenarios the user's locked ruling calls out:
 * (a) user responds before 30s ([cancelFor]) → no timeout event fires — normal flow.
 * (b) 30s elapses with no response → exactly one [ClassificationPromptTimeoutScheduler.PromptTimedOut]
 *     event fires for that tripId. The "trip persisted, never lost, never auto-classified" half of
 *     the ruling is a structural guarantee from [com.mileagetracker.app.service.TripTrackingForegroundService.handleStopEvent]
 *     already writing `TripStatus.PENDING_OCR` to Room *before* this scheduler is even started —
 *     this test suite only verifies the timer/event contract this class itself owns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassificationPromptTimeoutSchedulerTest {

    @Test
    fun `cancelFor before 30s elapses prevents the timeout event from firing`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val scheduler = ClassificationPromptTimeoutScheduler(coroutineScope = testScope)

        val emittedEvents = mutableListOf<ClassificationPromptTimeoutScheduler.PromptTimedOut>()
        testScope.launch { scheduler.observeTimeouts().toList(emittedEvents) }
        testScope.runCurrent()

        scheduler.startTimeoutFor("trip-1")
        testScope.advanceTimeBy(10_000) // 10s in — well before the 30s threshold
        scheduler.cancelFor("trip-1") // user responded — opened classification screen and saved
        testScope.advanceTimeBy(30_000) // advance well past where the original 30s deadline would have landed
        testScope.advanceUntilIdle()

        assertEquals(0, emittedEvents.size)
    }

    @Test
    fun `no response for the full 30s fires exactly one timeout event for that tripId`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val scheduler = ClassificationPromptTimeoutScheduler(coroutineScope = testScope)

        val emittedEvents = mutableListOf<ClassificationPromptTimeoutScheduler.PromptTimedOut>()
        testScope.launch { scheduler.observeTimeouts().toList(emittedEvents) }
        testScope.runCurrent()

        scheduler.startTimeoutFor("trip-2")
        testScope.advanceTimeBy(29_000) // 1s before the deadline
        assertEquals(0, emittedEvents.size)

        testScope.advanceTimeBy(1_000) // reaches exactly 30s
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals("trip-2", emittedEvents.first().tripId)
    }

    @Test
    fun `cancelFor a different tripId than the one currently pending is a no-op`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val scheduler = ClassificationPromptTimeoutScheduler(coroutineScope = testScope)

        val emittedEvents = mutableListOf<ClassificationPromptTimeoutScheduler.PromptTimedOut>()
        testScope.launch { scheduler.observeTimeouts().toList(emittedEvents) }
        testScope.runCurrent()

        scheduler.startTimeoutFor("trip-3")
        scheduler.cancelFor("some-other-trip-id") // must not cancel trip-3's countdown
        testScope.advanceTimeBy(30_000)
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals("trip-3", emittedEvents.first().tripId)
    }

    @Test
    fun `startTimeoutFor a second tripId cancels the first trip's pending countdown`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val scheduler = ClassificationPromptTimeoutScheduler(coroutineScope = testScope)

        val emittedEvents = mutableListOf<ClassificationPromptTimeoutScheduler.PromptTimedOut>()
        testScope.launch { scheduler.observeTimeouts().toList(emittedEvents) }
        testScope.runCurrent()

        scheduler.startTimeoutFor("trip-A")
        testScope.advanceTimeBy(10_000)
        scheduler.startTimeoutFor("trip-B") // defensive case — should not happen in practice, but must not double-fire
        testScope.advanceTimeBy(30_000)
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals("trip-B", emittedEvents.first().tripId)
    }

    @Test
    fun `cancel unconditionally stops any in-flight countdown`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val scheduler = ClassificationPromptTimeoutScheduler(coroutineScope = testScope)

        val emittedEvents = mutableListOf<ClassificationPromptTimeoutScheduler.PromptTimedOut>()
        testScope.launch { scheduler.observeTimeouts().toList(emittedEvents) }
        testScope.runCurrent()

        scheduler.startTimeoutFor("trip-teardown")
        testScope.advanceTimeBy(5_000)
        scheduler.cancel() // service teardown
        testScope.advanceTimeBy(30_000)
        testScope.advanceUntilIdle()

        assertEquals(0, emittedEvents.size)
        assertTrue(true) // explicit: no exception thrown by a teardown-then-advance sequence
    }
}
