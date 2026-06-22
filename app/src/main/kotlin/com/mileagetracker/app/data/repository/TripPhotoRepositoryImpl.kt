package com.mileagetracker.app.data.repository

import android.content.Context
import com.mileagetracker.app.data.local.TripPhotoDao
import com.mileagetracker.app.data.local.TripPhotoEntity
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Room-backed implementation of [TripPhotoRepository]. Enforces the T-001 blueprint §2
 * photo-retention decision: a [TripPhotoEntity] row is written ONLY when [PhotoRetentionMode.SAVED]
 * is in effect. Under [PhotoRetentionMode.TEMPORARY], the file at [imageUri] is deleted and no
 * row is ever created — this is correct, expected behavior for that trip, not a bug.
 */
class TripPhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tripPhotoDao: TripPhotoDao,
) : TripPhotoRepository {

    override suspend fun savePhotoIfRetentionEnabled(
        tripId: String,
        imageUri: String,
        retentionMode: PhotoRetentionMode,
    ) {
        when (retentionMode) {
            PhotoRetentionMode.SAVED -> {
                tripPhotoDao.insertTripPhoto(
                    TripPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        tripId = tripId,
                        imageUri = imageUri,
                        capturedAt = System.currentTimeMillis(),
                    ),
                )
            }
            PhotoRetentionMode.TEMPORARY -> {
                // Per blueprint §2: temporary retention means the photo was already used for
                // OCR and must be deleted immediately after success + user confirmation. No
                // TripPhotoEntity row is created for this capture — zero rows is correct.
                deleteFileQuietlyIfExists(imageUri)
            }
        }
    }

    override suspend fun deletePhotosForTrip(tripId: String) {
        val photos = tripPhotoDao.getPhotosForTrip(tripId)
        photos.forEach { photo -> deleteFileQuietlyIfExists(photo.imageUri) }
        tripPhotoDao.deletePhotosForTrip(tripId)
    }

    private fun deleteFileQuietlyIfExists(imageUri: String) {
        val candidateFile = File(imageUri)
        if (candidateFile.exists()) {
            val deleted = candidateFile.delete()
            check(deleted) { "Failed to delete temporary odometer photo at $imageUri" }
        }
    }
}
