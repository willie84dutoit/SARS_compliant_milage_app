package com.mileagetracker.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.mileagetracker.app.domain.model.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * Exact method signatures per T-001 blueprint §2. The recovery query
 * ([getInProgressTrip]/[observeInProgressTrip]) is the anti-duplication mechanism's read side —
 * see the blueprint's "Recovery requirement" section for the full call-site discipline this
 * depends on (checked before every onStartCommand, single insert point elsewhere in the code).
 */
@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    // Recovery requirement: exactly one of these must exist after restart, or none.
    // "active" and "pending_*" are mutually exclusive in practice (see state machine §4),
    // but the query covers all three so recovery logic has one call site, not three.
    @Query(
        "SELECT * FROM trips WHERE status IN ('active', 'pending_business_reason', 'pending_ocr') " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun getInProgressTrip(): TripEntity?

    // Same query as a Flow, for the Home screen to reactively show "trip in progress" banner.
    @Query(
        "SELECT * FROM trips WHERE status IN ('active', 'pending_business_reason', 'pending_ocr') " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    fun observeInProgressTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = 'completed' ORDER BY start_timestamp DESC")
    fun observeTripHistory(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE status = 'completed' ORDER BY start_timestamp DESC")
    suspend fun getCompletedTripsForExport(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE status = 'pending_business_reason'")
    fun observePendingBusinessReasonTrips(): Flow<List<TripEntity>>

    @Query("UPDATE trips SET status = :newStatus, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateTripStatus(tripId: String, newStatus: TripStatus, updatedAt: Long)

    @Query("UPDATE trips SET business_reason = :businessReason, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateBusinessReason(tripId: String, businessReason: String, updatedAt: Long)

    // --- T-004 live-tracking writes (foreground service only) -------------------------------

    @Query(
        "UPDATE trips SET start_latitude = :latitude, start_longitude = :longitude, updated_at = :updatedAt " +
            "WHERE id = :tripId AND start_latitude IS NULL",
    )
    suspend fun updateStartLocationIfUnset(tripId: String, latitude: Double, longitude: Double, updatedAt: Long)

    @Query(
        "UPDATE trips SET end_latitude = :latitude, end_longitude = :longitude, updated_at = :updatedAt " +
            "WHERE id = :tripId",
    )
    suspend fun updateEndLocation(tripId: String, latitude: Double, longitude: Double, updatedAt: Long)

    @Query("UPDATE trips SET distance_km = :distanceKm, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateDistanceKm(tripId: String, distanceKm: Double, updatedAt: Long)

    @Query("UPDATE trips SET end_timestamp = :endTimestamp, updated_at = :updatedAt WHERE id = :tripId")
    suspend fun updateEndTimestamp(tripId: String, endTimestamp: Long, updatedAt: Long)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    // --- T-008 signing queries -------------------------------------------------------------------

    /**
     * Counts all trips that have passed the stop-event (completed or pending_business_reason).
     * Used to assign [TripEntity.tripSequenceNumber] at signing time — the new trip's sequence
     * number is this count + 1, capturing finalization order.
     */
    @Query(
        "SELECT COUNT(*) FROM trips WHERE status IN ('completed', 'pending_business_reason')",
    )
    suspend fun countFinalizedTrips(): Int

    /**
     * Returns the most recently signed trip (non-null signatureBase64, ordered by
     * tripSequenceNumber descending). Used by the cold-start tail-hash self-heal to recompute
     * the DataStore cache from Room (the durability anchor per the T-008 decision).
     */
    @Query(
        "SELECT * FROM trips WHERE signature_base64 IS NOT NULL " +
            "ORDER BY trip_sequence_number DESC LIMIT 1",
    )
    suspend fun getMostRecentlySignedTrip(): TripEntity?

    /**
     * Writes the signing columns and tripSequenceNumber to an existing trip row in a single UPDATE,
     * called from [TripSigningOrchestrator] immediately before [markTripCompleted] writes the final
     * status. Separating the two updates (sign first, then flip status) keeps the Room write the
     * durability anchor: a crash between the two leaves the trip unsigned-but-still-pending, which
     * the self-heal can recover from on next cold start.
     */
    @Query(
        "UPDATE trips SET signature_base64 = :signatureBase64, signing_key_id = :signingKeyId, " +
            "trip_sequence_number = :tripSequenceNumber, updated_at = :updatedAt WHERE id = :tripId",
    )
    suspend fun updateSigningFields(
        tripId: String,
        signatureBase64: String,
        signingKeyId: String,
        tripSequenceNumber: Int,
        updatedAt: Long,
    )
}
