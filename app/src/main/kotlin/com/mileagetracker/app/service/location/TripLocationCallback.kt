package com.mileagetracker.app.service.location

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.mileagetracker.app.domain.location.GpsAnchorTracker
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * FusedLocationProviderClient callback. Implements T-004.2/T-004.3 of the full implementation
 * plan: Haversine accumulation between consecutive *accepted* fixes, gated by the locked 8.0m
 * noise floor (strictly below the 10m provider-level distance filter, so it does real filtering
 * work on top of it).
 *
 * The anchor/noise-floor/exception-safety *decision* lives in [gpsAnchorTracker] (a pure,
 * plain-JVM-testable class with no Android imports) — this class is reduced to the thin Android
 * boundary: pulling lat/lng `Double`s out of the platform [android.location.Location] object,
 * handing them to [gpsAnchorTracker], and translating its [GpsAnchorTracker.Outcome] back into
 * [Listener] callbacks. See [gpsAnchorTracker]'s class doc for why this extraction was necessary
 * (no `isReturnDefaultValues`, no Robolectric in this project — `Location` cannot be exercised in
 * `app/src/test`).
 *
 * This class does no I/O itself — it reports accepted distance deltas and the latest fix to its
 * [listener], which the foreground service owns (the service is responsible for flushing
 * distance to Room and resetting/observing the stop timers, per blueprint §1's
 * dependency-direction rule: `service -> domain + data`, never the other way round).
 */
class TripLocationCallback(
    private val listener: Listener,
    private val gpsAnchorTracker: GpsAnchorTracker = GpsAnchorTracker(),
) : LocationCallback() {

    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)

        val latestLocation = locationResult.lastLocation ?: return
        listener.onAnyLocationCallbackReceived()

        try {
            when (
                val outcome = gpsAnchorTracker.evaluateFix(
                    latitude = latestLocation.latitude,
                    longitude = latestLocation.longitude,
                )
            ) {
                is GpsAnchorTracker.Outcome.FirstFix -> {
                    listener.onFirstFixAcquired(outcome.latitude, outcome.longitude)
                }

                is GpsAnchorTracker.Outcome.AcceptedMovement -> {
                    listener.onAcceptedMovement(
                        deltaMeters = outcome.deltaMeters,
                        latestLatitude = outcome.latestLatitude,
                        latestLongitude = outcome.latestLongitude,
                    )
                }

                is GpsAnchorTracker.Outcome.Discarded -> {
                    // T-004.3 (locked): below the 8.0m noise floor — discard, no distance added,
                    // anchor unchanged, inactivity timer not reset.
                }

                is GpsAnchorTracker.Outcome.ComputationFailed -> {
                    Timber.tag("MT-Location").e(outcome.cause, "Failed to process location result")
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        }
    }

    /** Resets the anchor so the next fix is always treated as a first fix (e.g. on a new trip). */
    fun reset() {
        gpsAnchorTracker.reset()
    }

    interface Listener {
        /** Fired on every delivered callback, accepted or not — feeds the 2-minute unstable-signal timer (T-004.4). */
        fun onAnyLocationCallbackReceived()

        /** Fired once, for the very first fix of a trip — used to set the trip's start lat/lng. */
        fun onFirstFixAcquired(latitude: Double, longitude: Double)

        /** Fired when a fix clears the 8m noise floor — feeds distance accumulation and resets the 3-minute inactivity timer. */
        fun onAcceptedMovement(deltaMeters: Double, latestLatitude: Double, latestLongitude: Double)
    }
}
