package com.mileagetracker.app.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot pending-navigation seam between [com.mileagetracker.app.MainActivity] (which receives
 * `ACTION_OPEN_TRIP_CLASSIFICATION` intents — from the trip-classification notification tap, on a
 * locked or unlocked device, both at cold start via `onCreate` and while already running via
 * `onNewIntent`) and [MileageTrackerNavHost] (a `@Composable` with no Activity/Intent access of its
 * own, since its `NavHostController` is created with `rememberNavController()` inside the Compose
 * tree — T-003 design decision, see `team/TASKS.md` T-003 card).
 *
 * [MainActivity] is the only writer ([setPendingTripId]); [MileageTrackerNavHost] is the only
 * reader, and it must call [consumePendingTripId] — not just read [pendingTripId] — the moment it
 * acts on a value, so the same tripId is never re-navigated-to on the next recomposition (e.g. a
 * screen rotation, or any other state change that recomposes the `LaunchedEffect` host). This is
 * the "consumed exactly once" half of the seam; [setPendingTripId] is the "set on intent" half.
 *
 * Deliberately NOT a `Channel`/`SharedFlow` with replay=0: a plain nullable [StateFlow] plus an
 * explicit consume step is simpler to unit test with a hand-written fake-free assertion (no
 * mocking framework, per project convention) and survives configuration change correctly, because
 * Hilt `@Singleton` scope outlives the recreated `MainActivity` instance across a rotation — the
 * pending value (if any) is still there for the new Activity instance's recomposed NavHost to
 * consume, rather than being lost the way an Activity-scoped `Channel` collected once would be.
 */
@Singleton
class PendingTripClassificationNavigationStore @Inject constructor() {

    private val pendingTripIdState = MutableStateFlow<String?>(null)

    /** Observed by [MileageTrackerNavHost]; non-null exactly when a navigation is owed. */
    val pendingTripId: StateFlow<String?> = pendingTripIdState

    /** Called from [com.mileagetracker.app.MainActivity] whenever a new classification intent arrives. */
    fun setPendingTripId(tripId: String) {
        pendingTripIdState.value = tripId
    }

    /**
     * Atomically reads and clears the pending value. Returns null if nothing is pending (e.g. this
     * recomposition was not caused by a new intent). Callers must treat a non-null return as
     * "consumed" — there is no way to re-read the same value after this call.
     */
    fun consumePendingTripId(): String? {
        val tripId = pendingTripIdState.value
        pendingTripIdState.value = null
        return tripId
    }
}
