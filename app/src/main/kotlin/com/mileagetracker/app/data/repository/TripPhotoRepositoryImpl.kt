package com.mileagetracker.app.data.repository

import com.mileagetracker.app.data.local.TripPhotoDao
import com.mileagetracker.app.data.local.TripPhotoEntity
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

private const val TRIP_PHOTO_REPOSITORY_LOG_TAG = "MT-Repository"

/**
 * Room-backed implementation of [TripPhotoRepository]. Enforces the T-001 blueprint §2
 * photo-retention decision: a [TripPhotoEntity] row is written ONLY when [PhotoRetentionMode.SAVED]
 * is in effect. Under [PhotoRetentionMode.TEMPORARY], the file at [imageUri] is deleted and no
 * row is ever created — this is correct, expected behavior for that trip, not a bug.
 */
class TripPhotoRepositoryImpl @Inject constructor(
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

    /**
     * Best-effort delete: never throws. A temp odometer photo that fails to delete must not block
     * trip save/completion (degrade-never-block principle) — log and retry once, then give up and
     * log an error, leaving the orphaned file for a later cleanup pass rather than failing the
     * caller's transaction.
     */
    private fun deleteFileQuietlyIfExists(imageUri: String) {
        val candidateFile = File(imageUri)
        if (!candidateFile.exists()) {
            return
        }
        if (candidateFile.delete()) {
            return
        }
        Timber.tag(TRIP_PHOTO_REPOSITORY_LOG_TAG).w(
            "deleteFileQuietlyIfExists: first delete attempt failed for temporary odometer photo " +
                "at %s — retrying once",
            imageUri,
        )
        if (candidateFile.delete()) {
            return
        }
        Timber.tag(TRIP_PHOTO_REPOSITORY_LOG_TAG).e(
            "deleteFileQuietlyIfExists: failed to delete temporary odometer photo at %s after retry " +
                "— leaving orphaned file, save proceeds",
            imageUri,
        )
    }
}
