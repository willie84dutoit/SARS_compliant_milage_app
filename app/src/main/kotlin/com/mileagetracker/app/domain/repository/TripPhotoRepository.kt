package com.mileagetracker.app.domain.repository

import com.mileagetracker.app.domain.model.PhotoRetentionMode

/**
 * Domain-owned contract for trip-photo persistence. Per T-001 blueprint §2's photo-retention
 * decision: a row is written ONLY when [PhotoRetentionMode.SAVED] is in effect for the trip at
 * capture time. Under [PhotoRetentionMode.TEMPORARY], the implementation must delete the
 * underlying file after OCR success + user confirmation and must never call through to a Room
 * insert — zero rows for that trip is correct behavior, not a bug.
 */
interface TripPhotoRepository {

    /**
     * Saves the photo only if [retentionMode] is SAVED; otherwise deletes [imageUri] and returns
     * without writing a row. Implementations must document this branch inline (per blueprint §2).
     */
    suspend fun savePhotoIfRetentionEnabled(tripId: String, imageUri: String, retentionMode: PhotoRetentionMode)

    suspend fun deletePhotosForTrip(tripId: String)
}
