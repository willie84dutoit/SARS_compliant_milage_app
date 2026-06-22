package com.mileagetracker.app.domain.statemachine

import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TripLifecycleStateMachineTest {

    private val stateMachine = TripLifecycleStateMachine()

    @Test
    fun `confident vehicle entry moves to PromptPending`() {
        val nextPhase = stateMachine.onStartEvent(
            currentPhase = TripLifecycleStateMachine.TransientPhase.NoTrip,
            event = TripStartEvent.ConfidentVehicleEntry(confidencePercent = 85, detectedAtEpochMillis = 0L),
        )
        assert(nextPhase is TripLifecycleStateMachine.TransientPhase.PromptPending)
    }

    @Test
    fun `every stop event lands on PENDING_OCR first, never directly on COMPLETED`() {
        val status = stateMachine.onStopEvent(TripStopEvent.ConfirmedInactivity(inactiveSinceEpochMillis = 0L))
        assertEquals(TripStatus.PENDING_OCR, status)
    }

    @Test
    fun `resolving pending_ocr for a private trip completes immediately`() {
        val status = stateMachine.resolvePendingOcrAfterOdometerConfirmed(
            classification = TripClassification.PRIVATE,
            businessReason = null,
        )
        assertEquals(TripStatus.COMPLETED, status)
    }

    @Test
    fun `resolving pending_ocr for a work trip with blank reason goes to pending_business_reason`() {
        val status = stateMachine.resolvePendingOcrAfterOdometerConfirmed(
            classification = TripClassification.WORK,
            businessReason = "",
        )
        assertEquals(TripStatus.PENDING_BUSINESS_REASON, status)
    }

    @Test
    fun `resolving pending_ocr for a work trip with a reason completes`() {
        val status = stateMachine.resolvePendingOcrAfterOdometerConfirmed(
            classification = TripClassification.WORK,
            businessReason = "Client visit",
        )
        assertEquals(TripStatus.COMPLETED, status)
    }
}
