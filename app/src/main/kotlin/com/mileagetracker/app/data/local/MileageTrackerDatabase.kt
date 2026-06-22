package com.mileagetracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database, version 1. T-008's signing columns ([TripEntity.signatureBase64],
 * [TripEntity.signingKeyId]) are part of this initial schema directly — per the T-008 decision,
 * no Migration(1,2) is needed because this is a brand-new table, not a retrofit onto an existing
 * one. `exportSchema = true` per T-001 build order step 3, so the generated schema JSON under
 * `app/schemas/` can be checked into version control for future migration diffing.
 */
@Database(
    entities = [TripEntity::class, TripPhotoEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MileageTrackerDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripPhotoDao(): TripPhotoDao
}
