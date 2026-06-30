package com.mileagetracker.app.service.di

import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Singleton Hilt binding for [TripLifecycleStateMachine] (T-001 blueprint §3, updated T-030/P0.3).
 *
 * Moved from @ServiceScoped / ServiceComponent to @Singleton / SingletonComponent so that
 * [com.mileagetracker.app.ui.classification.TripClassificationViewModel] (a @HiltViewModel, which
 * lives in ActivityRetainedComponent — a child of SingletonComponent) can receive the same binding
 * via constructor injection rather than constructing its own unscoped instance.
 *
 * Sharing a single instance is safe because [TripLifecycleStateMachine] is explicitly documented
 * as a pure, stateless transition-logic class — it holds no mutable fields and performs no I/O.
 * The foreground service continues to receive it via injection; the previous
 * "fresh instance per service lifecycle" behaviour had no observable difference in practice (the
 * class doc confirms this), and the gain in testability and graph consistency outweighs the
 * cosmetic scope narrowing.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideTripLifecycleStateMachine(): TripLifecycleStateMachine {
        return TripLifecycleStateMachine()
    }
}
