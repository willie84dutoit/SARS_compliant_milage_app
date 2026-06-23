package com.mileagetracker.app.service.activityrecognition

import com.mileagetracker.app.domain.statemachine.TripStartEvent
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
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * T-002.4 tests, per `team/blueprints/T-002-vehicle-detection-spec.md` §5. Uses a hand-written
 * [FakeActivityUpdatesRegistrar] (no mocking framework, per this project's testing convention) in
 * place of the spec's literal `FakeActivityRecognitionClient` pseudocode — `ActivityRecognitionClient`
 * itself is a concrete Play Services class that cannot be faked on the plain JVM, so
 * [ConfidenceAcquisitionWindowImpl] depends on the [ActivityUpdatesRegistrar] seam instead (see
 * that file's doc comment). The seam still lets every behavior the spec's 4 test cases describe be
 * verified, including the idempotent-registration guard via [FakeActivityUpdatesRegistrar.registerCallCount].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfidenceAcquisitionWindowTest {

    @Test
    fun `readings 50 then 75 within the window fire confident entry at 75 and cancel the timer`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val fakeRegistrar = FakeActivityUpdatesRegistrar()
        val window = ConfidenceAcquisitionWindowImpl(
            activityUpdatesRegistrar = fakeRegistrar,
            coroutineScope = testScope,
            bluetoothDiagnosticsSnapshot = FakeBluetoothDiagnosticsSnapshot(),
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }
        testScope.runCurrent() // let the collector above actually start subscribing before any emit

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(50)
        testScope.advanceTimeBy(5_000) // first 5s poll tick
        window.onConfidenceReading(75)
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        val confidentEntry = emittedEvents.first() as TripStartEvent.ConfidentVehicleEntry
        assertEquals(75, confidentEntry.confidencePercent)
        assertFalse(window.isWindowActive()) // timer cancelled, subscription unregistered
        assertEquals(1, fakeRegistrar.unregisterCallCount)
    }

    @Test
    fun `confidence readings all below 70 for the full 30s fire retry-exhausted at exactly 30s`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val fakeRegistrar = FakeActivityUpdatesRegistrar()
        val window = ConfidenceAcquisitionWindowImpl(
            activityUpdatesRegistrar = fakeRegistrar,
            coroutineScope = testScope,
            bluetoothDiagnosticsSnapshot = FakeBluetoothDiagnosticsSnapshot(),
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }
        testScope.runCurrent() // let the collector above actually start subscribing before any emit

        window.startWindow(enteredAtEpochMillis = 0L)
        repeat(5) { tickIndex ->
            testScope.advanceTimeBy(5_000)
            window.onConfidenceReading(40 + tickIndex) // 40, 41, 42, 43, 44 — all below 70
        }

        // Confirm nothing has fired yet at 25s (5 ticks elapsed).
        assertEquals(0, emittedEvents.size)

        testScope.advanceTimeBy(5_000) // reaches exactly 30s
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        val retryExhausted = emittedEvents.first() as TripStartEvent.LowConfidenceRetryExhausted
        assertEquals(44, retryExhausted.lastObservedConfidencePercent) // running max, not latest
        assertFalse(window.isWindowActive())
        assertEquals(1, fakeRegistrar.unregisterCallCount)
    }

    @Test
    fun `a lower reading after a high reading does not erase the tracked maximum`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val fakeRegistrar = FakeActivityUpdatesRegistrar()
        val window = ConfidenceAcquisitionWindowImpl(
            activityUpdatesRegistrar = fakeRegistrar,
            coroutineScope = testScope,
            bluetoothDiagnosticsSnapshot = FakeBluetoothDiagnosticsSnapshot(),
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }
        testScope.runCurrent() // let the collector above actually start subscribing before any emit

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(85) // high reading first — should fire immediately
        window.onConfidenceReading(20) // lower reading after firing must not be observed as a new max
        testScope.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals(85, (emittedEvents.first() as TripStartEvent.ConfidentVehicleEntry).confidencePercent)
    }

    @Test
    fun `calling startWindow while a window is already active is a no-op`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val fakeRegistrar = FakeActivityUpdatesRegistrar()
        val window = ConfidenceAcquisitionWindowImpl(
            activityUpdatesRegistrar = fakeRegistrar,
            coroutineScope = testScope,
            bluetoothDiagnosticsSnapshot = FakeBluetoothDiagnosticsSnapshot(),
        )

        val emittedEvents = mutableListOf<TripStartEvent>()
        testScope.launch { window.observeResults().toList(emittedEvents) }
        testScope.runCurrent() // let the collector above actually start subscribing before any emit

        window.startWindow(enteredAtEpochMillis = 0L)
        window.onConfidenceReading(30)
        window.startWindow(enteredAtEpochMillis = 1_000L) // should be a no-op — must not re-register
        window.onConfidenceReading(72) // running max (30 -> 72) must still be tracked in the same window
        testScope.advanceUntilIdle()

        assertEquals(1, fakeRegistrar.registerCallCount) // no duplicate 5s subscription leaked
        assertEquals(1, emittedEvents.size)
        assertEquals(72, (emittedEvents.first() as TripStartEvent.ConfidentVehicleEntry).confidencePercent)
    }
}
