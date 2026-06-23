package com.mileagetracker.app.service.di

import com.mileagetracker.app.service.activityrecognition.BluetoothDiagnosticsSnapshot
import com.mileagetracker.app.service.activityrecognition.BluetoothDiagnosticsSnapshotImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * T-020 binding for the debug-only Bluetooth diagnostics seam.
 *
 * The interface and this `@Binds` declaration live in `src/main` so the rest of the app (and
 * Hilt's single merged component) compile against one contract regardless of build type. The
 * concrete `BluetoothDiagnosticsSnapshotImpl` class is NOT in `src/main` — it exists once in
 * `app/src/debug/.../BluetoothDiagnosticsSnapshotImpl.kt` (real ACL-state tracking) and once in
 * `app/src/release/.../BluetoothDiagnosticsSnapshotImpl.kt` (no-op), same fully-qualified class
 * name, so exactly one of the two is on the compile classpath per build type and this module
 * resolves correctly without any `BuildConfig.DEBUG` branching at runtime.
 *
 * `@Singleton`, matching [com.mileagetracker.app.service.activityrecognition.ActivityRecognitionModule]'s
 * scope for the sibling ActivityRecognition bindings this is consumed alongside
 * (`ConfidenceAcquisitionWindowImpl` is itself `@Singleton`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothDiagnosticsModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothDiagnosticsSnapshot(
        impl: BluetoothDiagnosticsSnapshotImpl,
    ): BluetoothDiagnosticsSnapshot
}
