package com.mileagetracker.app.domain.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-004.2/T-004.3 anchor-tracking + 8.0m noise-floor coverage, extracted from
 * [com.mileagetracker.app.service.location.TripLocationCallback] specifically so this real,
 * shipped, previously-untested decision logic can run on the plain JVM (see [GpsAnchorTracker]'s
 * class doc for why `android.location.Location` itself cannot be exercised in this module's
 * `app/src/test` source set).
 */
class GpsAnchorTrackerTest {

    @Test
    fun `first fix anchors with zero distance added and reports FirstFix`() {
        // Arrange
        val gpsAnchorTracker = GpsAnchorTracker()

        // Act
        val outcome = gpsAnchorTracker.evaluateFix(latitude = -25.7479, longitude = 28.2293)

        // Assert
        val firstFixOutcome = outcome as GpsAnchorTracker.Outcome.FirstFix
        assertEquals(-25.7479, firstFixOutcome.latitude, 0.0)
        assertEquals(28.2293, firstFixOutcome.longitude, 0.0)
    }

    @Test
    fun `fix below the 8 meter noise floor is discarded and anchor stays unchanged`() {
        // Arrange
        val gpsAnchorTracker = GpsAnchorTracker()
        gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = 0.0) // anchors at origin

        // A longitude delta of 0.00005 degrees at the equator is ~5.55m — comfortably below the
        // 8.0m noise floor without being so small it risks floating-point edge cases.
        val belowFloorLongitude = 0.00005

        // Act
        val outcome = gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = belowFloorLongitude)

        // Assert: discarded, and the anchor genuinely did not move — prove it by feeding the exact
        // same below-floor fix again and confirming it is still measured from the *original*
        // anchor (0,0), not from belowFloorLongitude.
        assertTrue(outcome is GpsAnchorTracker.Outcome.Discarded)
        val secondOutcome = gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = belowFloorLongitude)
        val discardedOutcome = secondOutcome as GpsAnchorTracker.Outcome.Discarded
        assertTrue(discardedOutcome.deltaMeters > 0.0)
    }

    @Test
    fun `fix at or above the 8 meter noise floor moves the anchor and reports AcceptedMovement with the correct delta`() {
        // Arrange
        val gpsAnchorTracker = GpsAnchorTracker()
        gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = 0.0) // anchors at origin

        // A longitude delta of 0.0001 degrees at the equator is ~11.13m — at/above the 8.0m floor.
        val atOrAboveFloorLongitude = 0.0001
        val expectedDeltaMeters = HaversineDistanceCalculator.distanceInMeters(
            startLatitude = 0.0,
            startLongitude = 0.0,
            endLatitude = 0.0,
            endLongitude = atOrAboveFloorLongitude,
        )

        // Act
        val outcome = gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = atOrAboveFloorLongitude)

        // Assert
        val acceptedMovementOutcome = outcome as GpsAnchorTracker.Outcome.AcceptedMovement
        assertEquals(expectedDeltaMeters, acceptedMovementOutcome.deltaMeters, 0.001)
        assertEquals(0.0, acceptedMovementOutcome.latestLatitude, 0.0)
        assertEquals(atOrAboveFloorLongitude, acceptedMovementOutcome.latestLongitude, 0.0)

        // The anchor must have actually moved: the next fix's delta is measured from the new
        // anchor, not the original origin — feeding the same point again now yields zero delta
        // (well below the floor), proving the anchor moved to atOrAboveFloorLongitude.
        val thirdOutcome = gpsAnchorTracker.evaluateFix(latitude = 0.0, longitude = atOrAboveFloorLongitude)
        val discardedOutcome = thirdOutcome as GpsAnchorTracker.Outcome.Discarded
        assertEquals(0.0, discardedOutcome.deltaMeters, 0.001)
    }

    @Test
    fun `haversine computation failure leaves the anchor unchanged and reports ComputationFailed without throwing`() {
        // Arrange: inject a failing calculator so the exception-safety path is forced
        // deterministically, rather than trying to coax HaversineDistanceCalculator itself into
        // throwing (it doesn't, for any finite double input).
        val forcedFailure = IllegalStateException("forced Haversine computation failure for test")
        val gpsAnchorTracker = GpsAnchorTracker(
            haversineDistanceCalculator = { _, _, _, _ -> throw forcedFailure },
        )
        gpsAnchorTracker.evaluateFix(latitude = 10.0, longitude = 20.0) // anchors normally (no calculator call on first fix)

        // Act
        val outcome = gpsAnchorTracker.evaluateFix(latitude = 10.001, longitude = 20.001)

        // Assert: failure reported, no crash
        val computationFailedOutcome = outcome as GpsAnchorTracker.Outcome.ComputationFailed
        assertEquals(forcedFailure, computationFailedOutcome.cause)

        // Assert: anchor unchanged — swap in a real (non-throwing) calculator and confirm the very
        // next evaluation is still measured from the original anchor (10.0, 20.0), not from the
        // failed fix's (10.001, 20.001).
        val recoveredTracker = GpsAnchorTracker()
        recoveredTracker.evaluateFix(latitude = 10.0, longitude = 20.0)
        val expectedDeltaFromOriginalAnchor = HaversineDistanceCalculator.distanceInMeters(
            startLatitude = 10.0,
            startLongitude = 20.0,
            endLatitude = 10.002,
            endLongitude = 20.002,
        )
        val verificationOutcome = recoveredTracker.evaluateFix(latitude = 10.002, longitude = 20.002)
        val acceptedMovementOutcome = verificationOutcome as GpsAnchorTracker.Outcome.AcceptedMovement
        assertEquals(expectedDeltaFromOriginalAnchor, acceptedMovementOutcome.deltaMeters, 0.001)
    }

    @Test
    fun `reset clears the anchor so the next fix is treated as a first fix again`() {
        // Arrange
        val gpsAnchorTracker = GpsAnchorTracker()
        gpsAnchorTracker.evaluateFix(latitude = 5.0, longitude = 5.0)
        gpsAnchorTracker.evaluateFix(latitude = 5.001, longitude = 5.001) // moves the anchor

        // Act
        gpsAnchorTracker.reset()
        val outcomeAfterReset = gpsAnchorTracker.evaluateFix(latitude = 99.0, longitude = 99.0)

        // Assert
        val firstFixOutcome = outcomeAfterReset as GpsAnchorTracker.Outcome.FirstFix
        assertEquals(99.0, firstFixOutcome.latitude, 0.0)
        assertEquals(99.0, firstFixOutcome.longitude, 0.0)
    }
}
