package com.mileagetracker.app.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

/** T-004.2: one degree of latitude is ~111,195 meters. */
class HaversineDistanceCalculatorTest {

    @Test
    fun `one degree of latitude separation is approximately 111195 meters`() {
        val distanceMeters = HaversineDistanceCalculator.distanceInMeters(
            startLatitude = 0.0,
            startLongitude = 0.0,
            endLatitude = 1.0,
            endLongitude = 0.0,
        )

        assertEquals(111_195.0, distanceMeters, 50.0)
    }

    @Test
    fun `identical points have zero distance`() {
        val distanceMeters = HaversineDistanceCalculator.distanceInMeters(
            startLatitude = -25.7479,
            startLongitude = 28.2293,
            endLatitude = -25.7479,
            endLongitude = 28.2293,
        )

        assertEquals(0.0, distanceMeters, 0.001)
    }
}
