package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain-owned contract for trip persistence. ViewModels depend on this interface only — never
 * on [com.mileagetracker.app.data.local.TripDao] or the Room database directly (T-001 blueprint
 * §3/§5 hard rule). [com.mileagetracker.app.data.repository.TripRepositoryImpl] is the sole
 * production implementation, bound via Hilt `@Binds` in `RepositoryModule`.
 */
interface TripRepository {

    suspend fun getTripById(tripId: String): Trip?

    /** Blueprint §2 recovery mechanism: the one call that must run before any new trip insert. */
    suspend fun getInProgressTrip(): Trip?

    fun observeInProgressTrip(): Flow<Trip?>

    fun observeTripHistory(): Flow<List<Trip>>

    suspend fun getCompletedTripsForExport(): List<Trip>

    fun observePendingBusinessReasonTrips(): Flow<List<Trip>>

    /** The single insert point per blueprint §2 — callers must have confirmed [getInProgressTrip] returned null first. */
    suspend fun insertNewActiveTrip(trip: Trip)

    /**
     * T-033: guarded — returns [TripWriteResult.RejectedSignedRow] if the trip is already signed,
     * [TripWriteResult.TripNotFound] if the tripId does not exist, [TripWriteResult.Success] otherwise.
     */
    suspend fun updateClassification(tripId: String, classification: TripClassification, businessReason: String?): TripWriteResult

    /**
     * T-033: guarded — returns [TripWriteResult.RejectedSignedRow] if the trip is already signed,
     * [TripWriteResult.TripNotFound] if the tripId does not exist, [TripWriteResult.Success] otherwise.
     */
    suspend fun updateBusinessReason(tripId: String, businessReason: String): TripWriteResult

    /**
     * T-033: guarded — returns [TripWriteResult.RejectedSignedRow] if the trip is already signed,
     * [TripWriteResult.TripNotFound] if the tripId does not exist, [TripWriteResult.Success] otherwise.
     */
    suspend fun updateVerifiedOdometer(tripId: String, verifiedOdometerKm: Double): TripWriteResult

    suspend fun markTripCompleted(tripId: String, signatureBase64: String, signingKeyId: String)

    /** T-008: writes the three signing fields without touching status — called before [markTripCompleted]. */
    suspend fun updateSigningFields(
        tripId: String,
        signatureBase64: String,
        signingKeyId: String,
        tripSequenceNumber: Int,
    )

    /**
     * T-008 / T-034: count of trips that already have an assigned tripSequenceNumber (> 0),
     * excluding [excludeTripId] (the trip being signed right now). Used to derive the next
     * monotonic sequence number: assignedSequenceNumber = countAssignedSequenceNumbers(id) + 1.
     * Excludes the current trip because a PENDING_BUSINESS_REASON WORK trip has tripSequenceNumber
     * == 0 (not yet assigned) and must not count itself.
     */
    suspend fun countAssignedSequenceNumbers(excludeTripId: String): Int

    /** T-008 cold-start self-heal: returns the highest-sequence signed trip, or null if none exist yet. */
    suspend fun getMostRecentlySignedTrip(): Trip?

    // --- T-004 live-tracking writes (called from TripTrackingForegroundService only) ---------

    /** Sets the trip's start lat/lng once, on the first GPS fix of the trip. No-op if already set. */
    suspend fun updateStartLocationIfUnset(tripId: String, latitude: Double, longitude: Double)

    /** Overwrites the trip's end lat/lng with the most recent accepted fix. */
    suspend fun updateEndLocation(tripId: String, latitude: Double, longitude: Double)

    /** T-004.5: flushes the in-memory accumulated distance to Room. */
    suspend fun updateDistanceKm(tripId: String, distanceKm: Double)

    /** Drives the trip across a [TripStatus] transition outside the classification/odometer flows. */
    suspend fun updateStatus(tripId: String, status: TripStatus)

    /** Sets the trip's real end timestamp, replacing the "still open" sentinel (blueprint §2). */
    suspend fun updateEndTimestamp(tripId: String, endTimestampEpochMillis: Long)
}
