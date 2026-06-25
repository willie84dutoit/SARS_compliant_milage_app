package com.mileagetracker.app.data.di

import com.mileagetracker.app.data.export.CsvFileWriter
import com.mileagetracker.app.data.export.CsvWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CsvWriterModule {

    @Binds
    abstract fun bindCsvWriter(csvFileWriter: CsvFileWriter): CsvWriter
}
