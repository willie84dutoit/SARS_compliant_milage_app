package com.mileagetracker.app.data.di

import com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOdometerOcrClient(impl: MlKitOdometerOcrClient): OdometerOcrClient
}
