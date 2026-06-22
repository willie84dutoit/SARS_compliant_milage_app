package com.mileagetracker.app.data.di

import com.mileagetracker.app.data.repository.SettingsRepositoryImpl
import com.mileagetracker.app.data.repository.TripPhotoRepositoryImpl
import com.mileagetracker.app.data.repository.TripRepositoryImpl
import com.mileagetracker.app.domain.repository.SettingsRepository
import com.mileagetracker.app.domain.repository.TripPhotoRepository
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the domain repository interfaces to their Room/DataStore-backed implementations. This is
 * the literal mechanism (T-001 blueprint §3) that keeps ViewModels seeing only
 * [TripRepository]/[TripPhotoRepository]/[SettingsRepository] — never [TripRepositoryImpl],
 * never a DAO directly.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindTripPhotoRepository(impl: TripPhotoRepositoryImpl): TripPhotoRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
