package com.mileagetracker.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `trip_photos` table (T-001 blueprint §2). A row exists ONLY when the
 * trip's photo-retention setting was SAVED at capture time — under TEMPORARY retention, the
 * file is deleted after OCR confirmation and no row is ever inserted for that capture. Zero
 * rows for a given trip is correct, expected behavior, not a bug — see
 * [com.mileagetracker.app.data.repository.TripPhotoRepositoryImpl] for the enforcement point.
 */
@Entity(
    tableName = "trip_photos",
    indices = [Index(value = ["trip_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TripPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "trip_id")
    val tripId: String,

    @ColumnInfo(name = "image_uri")
    val imageUri: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,
)
