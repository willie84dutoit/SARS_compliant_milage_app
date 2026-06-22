package com.mileagetracker.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Room entity for the `trips` table. Field shape is locked by the T-001 blueprint §2 — this is
 * a transcription of that table, not a redesign. Two notes carried over from the blueprint:
 *
 * - [endTimestamp] is written equal to [startTimestamp] as a sentinel while status == ACTIVE
 *   (brief's type for endTimestamp is non-null Long; this avoids contradicting that while still
 *   being internally consistent — the repository, not the UI, treats endTimestamp == startTimestamp
 *   as "trip still open" wherever duration is computed).
 * - [signatureBase64] / [signingKeyId] are the two nullable columns added per the T-008 decision
 *   ([2026-06-18 17:10] DECISION in team/LOGS.md) — built into the v1 schema directly since this
 *   is the initial table (no Migration(1,2) needed for a brand-new table).
 */
@Entity(tableName = "trips", indices = [Index(value = ["status"])])
data class TripEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "classification")
    val classification: TripClassification,

    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long,

    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long,

    @ColumnInfo(name = "start_odometer_km")
    val startOdometerKm: Double,

    @ColumnInfo(name = "end_odometer_km")
    val endOdometerKm: Double,

    @ColumnInfo(name = "verified_odometer_km")
    val verifiedOdometerKm: Double?,

    @ColumnInfo(name = "distance_km")
    val distanceKm: Double,

    @ColumnInfo(name = "business_reason")
    val businessReason: String?,

    @ColumnInfo(name = "start_latitude")
    val startLatitude: Double?,

    @ColumnInfo(name = "start_longitude")
    val startLongitude: Double?,

    @ColumnInfo(name = "end_latitude")
    val endLatitude: Double?,

    @ColumnInfo(name = "end_longitude")
    val endLongitude: Double?,

    @ColumnInfo(name = "status")
    val status: TripStatus,

    @ColumnInfo(name = "photo_retention")
    val photoRetention: PhotoRetentionMode,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    // --- T-008 trip-signing columns (DECISION [2026-06-18 17:10], team/LOGS.md) ---
    @ColumnInfo(name = "signature_base64")
    val signatureBase64: String?,

    @ColumnInfo(name = "signing_key_id")
    val signingKeyId: String?,
)
