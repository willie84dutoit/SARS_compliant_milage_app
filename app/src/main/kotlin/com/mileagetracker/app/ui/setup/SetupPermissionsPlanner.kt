package com.mileagetracker.app.ui.setup

import android.os.Build

/**
 * Current OS-reported grant state for every permission [SetupPermissionsScreen] cares about, as
 * read via `ContextCompat.checkSelfPermission` or an `ActivityResult` callback's
 * `Map<String, Boolean>`. Plain booleans only — no `Context`/`Activity` reference — so this and
 * [SetupPermissionsPlanner] are testable with zero Android framework dependency, consistent with
 * this project's "hand-written fakes only, no mocking framework" convention (there is nothing here
 * that needs faking).
 */
data class PermissionGrantSnapshot(
    val isFineLocationGranted: Boolean,
    val isCoarseLocationGranted: Boolean,
    val isBackgroundLocationGranted: Boolean,
    val isCameraGranted: Boolean,
    val isActivityRecognitionGranted: Boolean,
    val isNotificationsGranted: Boolean,
)

/**
 * Pure decision logic for "what permissions still need requesting, in what order, given the
 * current grant state" (T-002.1). Deliberately has no Android framework dependency beyond
 * `Build.VERSION_CODES` constants, so it is unit-testable directly.
 *
 * Android 10+ (API 29+) requires `ACCESS_BACKGROUND_LOCATION` to be requested in a **separate**
 * call, after foreground location (fine or coarse) has already been granted — bundling it into the
 * same `RequestMultiplePermissions()` call as fine/coarse location silently fails to grant it on
 * API 29+ (the OS shows the dialog but the background grant is dropped). [secondStepPermissions]
 * encodes that ordering rule; callers must not fire it until the first-round result confirms
 * foreground location was granted.
 */
class SetupPermissionsPlanner(
    private val sdkVersion: Int = Build.VERSION.SDK_INT,
) {

    /**
     * The first-round permissions to request together: location (fine + coarse), camera, activity
     * recognition, and — API 33+ only — notifications. Returns only the ones not already granted,
     * so a returning user who already granted everything gets an empty array and the launcher call
     * can be skipped entirely.
     */
    fun firstRoundPermissionsToRequest(currentGrantState: PermissionGrantSnapshot): Array<String> {
        val permissionsStillNeeded = mutableListOf<String>()
        if (!currentGrantState.isFineLocationGranted) {
            permissionsStillNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!currentGrantState.isCoarseLocationGranted) {
            permissionsStillNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!currentGrantState.isCameraGranted) {
            permissionsStillNeeded.add(android.Manifest.permission.CAMERA)
        }
        if (!currentGrantState.isActivityRecognitionGranted) {
            permissionsStillNeeded.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (sdkVersion >= Build.VERSION_CODES.TIRAMISU && !currentGrantState.isNotificationsGranted) {
            permissionsStillNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissionsStillNeeded.toTypedArray()
    }

    /**
     * Whether the second-step `ACCESS_BACKGROUND_LOCATION` request should fire, given the grant
     * state immediately after the first round resolved. Only true when background location is not
     * already granted AND at least one of fine/coarse foreground location is granted — requesting
     * background location before foreground location is granted is a no-op on API 29+ (the OS
     * denies it outright), so this never tells a caller to fire it in that case. Below API 29,
     * `ACCESS_BACKGROUND_LOCATION` does not exist as a distinct runtime permission (foreground grant
     * implies background access), so this always returns false there.
     */
    fun shouldRequestBackgroundLocation(currentGrantState: PermissionGrantSnapshot): Boolean {
        if (sdkVersion < Build.VERSION_CODES.Q) return false
        if (currentGrantState.isBackgroundLocationGranted) return false
        return currentGrantState.isFineLocationGranted || currentGrantState.isCoarseLocationGranted
    }

    /**
     * Brief §8's locked limited-mode rule: "If the user disables background location or
     * notification access, the app must degrade gracefully and fall back to manual start/stop
     * confirmation." Camera denial alone does not trigger limited mode — it only blocks odometer
     * OCR capture (a separate, narrower fallback already handled by T-005's manual-entry path) —
     * and is deliberately excluded here.
     *
     * Below API 33, [PermissionGrantSnapshot.isNotificationsGranted] is expected to already be
     * normalized to `true` by the caller (no runtime notification permission exists pre-Tiramisu),
     * so this still gives the correct answer without an explicit SDK check here.
     */
    fun isLimitedModeRequired(currentGrantState: PermissionGrantSnapshot): Boolean {
        return !currentGrantState.isBackgroundLocationGranted || !currentGrantState.isNotificationsGranted
    }
}
