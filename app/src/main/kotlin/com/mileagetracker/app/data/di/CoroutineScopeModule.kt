package com.mileagetracker.app.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the application-wide [CoroutineScope] provided by [CoroutineScopeModule].
 * Used to launch coroutines that should outlive any individual component (ViewModel, Activity)
 * and survive for the entire process lifetime — specifically the T-008 cold-start self-heal
 * in [com.mileagetracker.app.MileageTrackerApplication].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    /**
     * A [SupervisorJob]-backed [CoroutineScope] running on [Dispatchers.IO], bound to the
     * SingletonComponent lifetime (i.e. the application process). Individual child-job failures
     * do not cancel the scope — the supervisor isolates them.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
