package com.mileagetracker.app.data.local

import androidx.room.TypeConverter
import com.mileagetracker.app.domain.model.PhotoRetentionMode
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.model.TripStatus

/**
 * Room TypeConverters for the three enums in [TripEntity]. Stored as the lowercase string form
 * matching the brief's literal values (`work`/`private`, `active`/`completed`/
 * `pending_business_reason`/`pending_ocr`, `temporary`/`saved`) so the underlying SQLite values
 * are human-readable and match the brief's data-model section verbatim.
 */
class Converters {

    @TypeConverter
    fun classificationToString(value: TripClassification): String = when (value) {
        TripClassification.WORK -> "work"
        TripClassification.PRIVATE -> "private"
    }

    @TypeConverter
    fun stringToClassification(value: String): TripClassification = when (value) {
        "work" -> TripClassification.WORK
        "private" -> TripClassification.PRIVATE
        else -> throw IllegalArgumentException("Unknown TripClassification value: $value")
    }

    @TypeConverter
    fun statusToString(value: TripStatus): String = when (value) {
        TripStatus.ACTIVE -> "active"
        TripStatus.COMPLETED -> "completed"
        TripStatus.PENDING_BUSINESS_REASON -> "pending_business_reason"
        TripStatus.PENDING_OCR -> "pending_ocr"
    }

    @TypeConverter
    fun stringToStatus(value: String): TripStatus = when (value) {
        "active" -> TripStatus.ACTIVE
        "completed" -> TripStatus.COMPLETED
        "pending_business_reason" -> TripStatus.PENDING_BUSINESS_REASON
        "pending_ocr" -> TripStatus.PENDING_OCR
        else -> throw IllegalArgumentException("Unknown TripStatus value: $value")
    }

    @TypeConverter
    fun photoRetentionToString(value: PhotoRetentionMode): String = when (value) {
        PhotoRetentionMode.TEMPORARY -> "temporary"
        PhotoRetentionMode.SAVED -> "saved"
    }

    @TypeConverter
    fun stringToPhotoRetention(value: String): PhotoRetentionMode = when (value) {
        "temporary" -> PhotoRetentionMode.TEMPORARY
        "saved" -> PhotoRetentionMode.SAVED
        else -> throw IllegalArgumentException("Unknown PhotoRetentionMode value: $value")
    }
}
