package com.mileagetracker.app.service.location

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.mileagetracker.app.domain.location.HaversineDistanceCalculator
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * FusedLocationProviderClient callback. Implements T-004.2/T-004.3 of the full implementation
 * plan: Haversine accumulation between consecutive *accepted* fixes, gated by the locked 8.0m
 * noise floor (strictly below the 10m provider-level distance filter, so it does real filtering
 * work on top of it).
 *
 * This class does no I/O itself — it reports accepted distance deltas and the latest fix to its
 * [listener], which the foreground service owns (the service is responsible for flushing
 * distance to Room and resetting/observing the stop timers, per blueprint §1's
 * dependency-direction rule: `service -> domain + data`, never the other way round).
 */
class TripLocationCallback(
    private val listener: Listener,
) : LocationCallback() {

    /** The last fix this callback *accepted* (i.e. moved the anchor) — null until the first fix arrives. */
    private var lastAcceptedLatitude: Double? = null
    private var lastAcceptedLongitude: Double? = null

    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)

        val latestLocation = locationResult.lastLocation ?: return
        listener.onAnyLocationCallbackReceived()

        val previousLatitude = lastAcceptedLatitude
        val previousLongitude = lastAcceptedLongitude

        if (previousLatitude == null || previousLongitude == null) {
            // First fix of the trip: anchor immediately, no distance to add yet.
            lastAcceptedLatitude = latestLocation.latitude
            lastAcceptedLongitude = latestLocation.longitude
            listener.onFirstFixAcquired(latestLocation.latitude, latestLocation.longitude)
            return
        }

        try {
            val deltaMeters = HaversineDistanceCalculator.distanceInMeters(
                startLatitude = previousLatitude,
                startLongitude = previousLongitude,
                endLatitude = latestLocation.latitude,
                endLongitude = latestLocation.longitude,
            )

            if (deltaMeters < GPS_NOISE_FLOOR_METERS) {
                // T-004.3 (locked): below the 8.0m noise floor — discard, no distance added, anchor
                // unchanged, inactivity timer not reset.
                return
            }

            lastAcceptedLatitude = latestLocation.latitude
            lastAcceptedLongitude = latestLocation.longitude
            listener.onAcceptedMovement(
                deltaMeters = deltaMeters,
                latestLatitude = latestLocation.latitude,
                latestLongitude = latestLocation.longitude,
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (haversineComputationFailure: Exception) {
            Timber.tag("MT-Location").e(haversineComputationFailure, "Failed to process location result")
            // Do not update lastAcceptedLatitude/lastAcceptedLongitude on failure; next fix will be
            // compared against the last known-good point.
        }
    }

    /** Resets the anchor so the next fix is always treated as a first fix (e.g. on a new trip). */
    fun reset() {
        lastAcceptedLatitude = null
        lastAcceptedLongitude = null
    }

    interface Listener {
        /** Fired on every delivered callback, accepted or not — feeds the 2-minute unstable-signal timer (T-004.4). */
        fun onAnyLocationCallbackReceived()

        /** Fired once, for the very first fix of a trip — used to set the trip's start lat/lng. */
        fun onFirstFixAcquired(latitude: Double, longitude: Double)

        /** Fired when a fix clears the 8m noise floor — feeds distance accumulation and resets the 3-minute inactivity timer. */
        fun onAcceptedMovement(deltaMeters: Double, latestLatitude: Double, latestLongitude: Double)
    }

    private companion object {
        /** T-004.3 locked value: strictly below the 10m provider filter, above stationary GPS drift. */
        const val GPS_NOISE_FLOOR_METERS = 8.0
    }
}
