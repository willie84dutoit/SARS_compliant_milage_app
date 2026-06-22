package com.mileagetracker.app.data.di

import android.content.Context
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the two Google Play Services clients the service layer depends on. The actual
 * `ActivityTransitionRequest` construction and confidence-threshold derivation are
 * geo-sensors-specialist's call (T-002/T-004, blueprint open question 1) — this module only
 * wires the clients into Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(@ApplicationContext appContext: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(appContext)
    }

    @Provides
    @Singleton
    fun provideActivityRecognitionClient(@ApplicationContext appContext: Context): ActivityRecognitionClient {
        return ActivityRecognition.getClient(appContext)
    }
}
