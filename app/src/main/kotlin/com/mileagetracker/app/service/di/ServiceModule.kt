package com.mileagetracker.app.service.di

import com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.android.components.ServiceComponent

/**
 * Service-scoped Hilt bindings (T-001 blueprint §3). [TripLifecycleStateMachine] is provided
 * fresh per service lifecycle rather than as an app-wide `@Singleton` — the foreground service is
 * the only consumer, and scoping it to [ServiceComponent] (rather than `SingletonComponent`)
 * keeps the binding's lifetime visibly tied to the one place it's used, per the blueprint's
 * Hilt-graph table.
 *
 * [TripLifecycleStateMachine] itself does no I/O and holds no mutable state (it is a pure
 * transition-logic class, intentionally, so it stays unit-testable on the plain JVM — see its
 * class doc). "Fresh instance per service lifecycle" therefore has no observable behavioral
 * difference from a singleton today, but matches the blueprint's documented scope exactly and
 * avoids a service-lifetime object silently becoming an undocumented app-wide singleton later if
 * the class ever does gain internal state.
 */
@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
    fun provideTripLifecycleStateMachine(): TripLifecycleStateMachine {
        return TripLifecycleStateMachine()
    }
}
