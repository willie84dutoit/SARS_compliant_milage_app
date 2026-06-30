package com.mileagetracker.app.service.di

import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
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
 * Qualifier for the [CoroutineScope] that backs
 * [com.mileagetracker.app.service.notification.ClassificationPromptTimeoutScheduler]'s 30s timer
 * (T-039 item 9). Mirrors [ConfidenceAcquisitionScope]'s shape exactly — a distinct qualifier
 * keeps this timer's scope from colliding with any other unqualified `@Singleton CoroutineScope`
 * binding in the graph (the same M-1-adjacent issue T-039 already fixed for the confidence
 * window).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClassificationPromptTimeoutScope

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

    /**
     * Backs [com.mileagetracker.app.service.notification.ClassificationPromptTimeoutScheduler]'s
     * 30s timer (T-039 item 9). CPU-bound delay only, no I/O, so `Dispatchers.Default`;
     * `SupervisorJob()` so a failure in one trip's timeout does not take down the next one —
     * same reasoning as [ActivityRecognitionModule]'s `provideConfidenceAcquisitionCoroutineScope`.
     */
    @Provides
    @Singleton
    @ClassificationPromptTimeoutScope
    fun provideClassificationPromptTimeoutCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
