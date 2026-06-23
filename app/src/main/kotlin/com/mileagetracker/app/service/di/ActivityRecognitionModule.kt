package com.mileagetracker.app.service.di

import com.mileagetracker.app.service.activityrecognition.ActivityUpdatesRegistrar
import com.mileagetracker.app.service.activityrecognition.ActivityUpdatesRegistrarImpl
import com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindow
import com.mileagetracker.app.service.activityrecognition.ConfidenceAcquisitionWindowImpl
import com.mileagetracker.app.service.activityrecognition.VehicleEntryConfidenceGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * T-002.3/T-002.4 bindings for the ActivityRecognition detection pipeline.
 *
 * `SingletonComponent` (not `ServiceComponent`) because [VehicleEntryConfidenceGateway] and
 * [ConfidenceAcquisitionWindow] are consumed by
 * [com.mileagetracker.app.service.activityrecognition.ActivityTransitionReceiver] and
 * [com.mileagetracker.app.service.activityrecognition.ConfidenceUpdateReceiver] — both
 * `@AndroidEntryPoint` `BroadcastReceiver`s that can run independently of the foreground
 * service's lifecycle (e.g. delivered while the service process is being recreated) — they must
 * not depend on a service-scoped graph being active. See
 * `team/blueprints/T-002-vehicle-detection-spec.md` §6 for the deviation note explaining why this
 * is `@Singleton` rather than the spec's originally suggested `@ServiceScoped`.
 *
 * [ConfidenceAcquisitionWindowImpl] is bound as both [ConfidenceAcquisitionWindow] (consumed by
 * [com.mileagetracker.app.service.activityrecognition.ConfidenceUpdateReceiver] and, in a future
 * chunk, by `TripTrackingForegroundService`) and [VehicleEntryConfidenceGateway] (consumed by
 * `ActivityTransitionReceiver`) — both bindings resolve to the same singleton instance so the
 * window state is shared correctly between the two receivers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityRecognitionModule {

    @Binds
    @Singleton
    abstract fun bindVehicleEntryConfidenceGateway(
        impl: ConfidenceAcquisitionWindowImpl,
    ): VehicleEntryConfidenceGateway

    @Binds
    @Singleton
    abstract fun bindConfidenceAcquisitionWindow(
        impl: ConfidenceAcquisitionWindowImpl,
    ): ConfidenceAcquisitionWindow

    @Binds
    @Singleton
    abstract fun bindActivityUpdatesRegistrar(
        impl: ActivityUpdatesRegistrarImpl,
    ): ActivityUpdatesRegistrar

    companion object {
        /**
         * Backs [ConfidenceAcquisitionWindowImpl]'s 30s timer (blueprint §4 point 4). CPU-bound,
         * no I/O, so `Dispatchers.Default`; `SupervisorJob()` so a failure in one window's timer
         * doesn't take down future windows. App-scoped, not service-scoped — see the class doc
         * above for why this binding lives in `SingletonComponent`.
         */
        @Provides
        @Singleton
        fun provideConfidenceAcquisitionCoroutineScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
