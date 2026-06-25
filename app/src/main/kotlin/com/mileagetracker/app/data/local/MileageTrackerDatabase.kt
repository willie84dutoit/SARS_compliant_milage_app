package com.mileagetracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database, version 3.
 *
 * v1: initial schema with [TripEntity.signatureBase64] and [TripEntity.signingKeyId] as the only
 * T-008 columns (added in-schema at table creation — no migration needed for those two, per the
 * original blueprint note).
 *
 * v1 → v2 ([MIGRATION_1_2]): adds [TripEntity.tripSequenceNumber] with DEFAULT 0 (existing rows
 * were never signed so 0 is the correct sentinel value for them). This is the one additive
 * migration required by the T-008 decision ([2026-06-18 17:10] DECISION in team/LOGS.md).
 *
 * v2 → v3 ([MIGRATION_2_3]): adds [TripEntity.isManualStart] (INTEGER NOT NULL DEFAULT 0) for the
 * H-1/H-2 origin-label fix. DEFAULT 0 (false = auto-detected) is acceptable for existing rows —
 * those were inserted in debug sessions and the "detected" label is correct for them.
 *
 * `exportSchema = true` per T-001 build order step 3, so the generated schema JSON under
 * `app/schemas/` can be checked into version control for future migration diffing.
 */
@Database(
    entities = [TripEntity::class, TripPhotoEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MileageTrackerDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripPhotoDao(): TripPhotoDao

    companion object {
        /**
         * Adds the `trip_sequence_number` column (INTEGER NOT NULL DEFAULT 0). The DEFAULT 0
         * ensures SQLite backfills existing rows without requiring a table rebuild, and 0 is the
         * correct sentinel for any row that predates T-008 signing.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN trip_sequence_number INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds the `is_manual_start` column (INTEGER NOT NULL DEFAULT 0) for H-1/H-2 origin-label
         * fix. SQLite stores booleans as INTEGER (0/1); Room's Boolean type converter maps 0→false
         * and 1→true. DEFAULT 0 (false = auto-detected) is safe for existing rows.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE trips ADD COLUMN is_manual_start INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
