package com.mileagetracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Exact method signatures per T-001 blueprint §2. */
@Dao
interface TripPhotoDao {

    @Insert
    suspend fun insertTripPhoto(photo: TripPhotoEntity)

    @Query("SELECT * FROM trip_photos WHERE trip_id = :tripId")
    suspend fun getPhotosForTrip(tripId: String): List<TripPhotoEntity>

    @Query("DELETE FROM trip_photos WHERE trip_id = :tripId")
    suspend fun deletePhotosForTrip(tripId: String)
}
