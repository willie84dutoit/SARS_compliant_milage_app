package com.mileagetracker.app.domain.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Haversine great-circle distance calculation (T-001 blueprint §1 domain-layer rule: no
 * Android imports, unit-testable on the plain JVM). Used by the service layer to accumulate
 * [com.mileagetracker.app.domain.model.Trip.distanceKm] between consecutive accepted GPS fixes
 * (T-004.2/T-004.3 of the full implementation plan).
 */
object HaversineDistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Returns the great-circle distance between two lat/lng points, in meters.
     */
    fun distanceInMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val latitudeDeltaRadians = Math.toRadians(endLatitude - startLatitude)
        val longitudeDeltaRadians = Math.toRadians(endLongitude - startLongitude)

        val haversineTerm = sin(latitudeDeltaRadians / 2.0) * sin(latitudeDeltaRadians / 2.0) +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) *
            sin(longitudeDeltaRadians / 2.0) * sin(longitudeDeltaRadians / 2.0)
        val angularDistance = 2.0 * atan2(sqrt(haversineTerm), sqrt(1.0 - haversineTerm))

        return EARTH_RADIUS_METERS * angularDistance
    }
}
