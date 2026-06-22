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
}
