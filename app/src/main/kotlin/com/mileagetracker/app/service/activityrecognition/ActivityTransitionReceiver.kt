package com.mileagetracker.app.service.activityrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mileagetracker.app.domain.repository.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives ActivityTransitionResult broadcasts. T-001 scaffolding only — the actual transition
 * parsing and hand-off into [com.mileagetracker.app.domain.statemachine.TripLifecycleStateMachine]
 * is wired in T-002, coordinated with geo-sensors-specialist (blueprint open question 1).
 *
 * Injects [TripRepository] directly (not a DAO) so any transition-driven trip creation still
 * respects the no-duplicate-trip recovery check (blueprint §2) — this receiver must call
 * `tripRepository.getInProgressTrip()` before triggering a new trip, the same discipline the
 * foreground service's `onStartCommand` follows.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var tripRepository: TripRepository

    override fun onReceive(context: Context, intent: Intent) {
        // T-002 TODO (geo-sensors-specialist + android-engineer): parse ActivityTransitionResult
        // from intent, feed the confidence value into TripLifecycleStateMachine.onStartEvent,
        // and gate any new-trip creation on tripRepository.getInProgressTrip() == null first.
    }
}
