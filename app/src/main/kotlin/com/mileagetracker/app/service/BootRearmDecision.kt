package com.mileagetracker.app.service

/**
 * Pure decision logic for "should [com.mileagetracker.app.service.BootCompletedReceiver] start
 * [TripTrackingForegroundService] after a `BOOT_COMPLETED` broadcast" (T-035).
 *
 * Deliberately has zero Android framework dependency (no `Context`, no `Intent`) so it is
 * unit-testable directly, consistent with this project's "hand-written fakes only, no mocking
 * framework" convention (mirrors [com.mileagetracker.app.ui.setup.SetupPermissionsPlanner]).
 *
 * Mirrors [com.mileagetracker.app.MainActivity.startTripTrackingServiceForDetection]'s exact
 * gate: fine location is the one permission [TripTrackingForegroundService.onStartCommand]
 * cannot promote itself to foreground without (API 29-33 throws `SecurityException`, API 34+
 * throws `InvalidForegroundServiceTypeException` for `foregroundServiceType="location"`). This is
 * intentionally the SAME gate as the app-launch path, not a broader multi-permission check — the
 * receiver re-arms exactly the path the app already arms on every launch, it does not invent a
 * parallel arming mechanism. If fine location is absent at boot, the service start is skipped
 * (degrade-never-block); the user re-arms everything on next app open, same as a fresh install.
 */
object BootRearmDecision {

    /**
     * @param isFineLocationGranted current OS-reported grant state for
     *   `android.permission.ACCESS_FINE_LOCATION`, as read via `ContextCompat.checkSelfPermission`
     *   at the moment `BOOT_COMPLETED` is received.
     * @return true if [TripTrackingForegroundService] should be started (no action set, same as
     *   [com.mileagetracker.app.MainActivity]'s app-launch call), false if the start must be
     *   skipped because the foreground-service promotion would fail.
     */
    fun shouldStartDetectionService(isFineLocationGranted: Boolean): Boolean {
        return isFineLocationGranted
    }
}
