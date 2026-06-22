package com.mileagetracker.app.data.di

import android.content.Context
import androidx.room.Room
import com.mileagetracker.app.data.local.MileageTrackerDatabase
import com.mileagetracker.app.data.local.TripDao
import com.mileagetracker.app.data.local.TripPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATABASE_NAME = "mileage_tracker.db"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMileageTrackerDatabase(@ApplicationContext appContext: Context): MileageTrackerDatabase {
        return Room.databaseBuilder(appContext, MileageTrackerDatabase::class.java, DATABASE_NAME).build()
    }

    @Provides
    @Singleton
    fun provideTripDao(database: MileageTrackerDatabase): TripDao = database.tripDao()

    @Provides
    @Singleton
    fun provideTripPhotoDao(database: MileageTrackerDatabase): TripPhotoDao = database.tripPhotoDao()
}
