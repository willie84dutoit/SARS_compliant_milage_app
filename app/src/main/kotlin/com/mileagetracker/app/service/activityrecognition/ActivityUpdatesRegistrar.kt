package com.mileagetracker.app.service.activityrecognition

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin seam around [ActivityRecognitionClient.requestActivityUpdates] /
 * `removeActivityUpdates`, owned by T-002.4.
 *
 * `ActivityRecognitionClient` is a concrete Play Services class (no public interface, cannot be
 * constructed or subclassed on the plain JVM), so [ConfidenceAcquisitionWindowImpl] cannot depend
 * on it directly and still stay unit-testable with a hand-written fake (this project's convention
 * — no mocking framework, see `team/blueprints/T-002-vehicle-detection-spec.md` §5). This
 * interface is the seam: [ConfidenceAcquisitionWindowImpl] depends on
 * [ActivityUpdatesRegistrar], tests substitute [FakeActivityUpdatesRegistrar]-style fakes, and
 * only the production [ActivityUpdatesRegistrarImpl] touches the real GMS client.
 */
interface ActivityUpdatesRegistrar {
    /** Registers a 5s-interval `requestActivityUpdates` subscription. Safe to call repeatedly. */
    fun register()

    /** Unregisters the subscription. Safe to call even if not currently registered. */
    fun unregister()
}

private const val CONFIDENCE_WINDOW_INTERVAL_MILLIS = 5_000L
private const val CONFIDENCE_UPDATE_REQUEST_CODE = 1002

class ActivityUpdatesRegistrarImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val activityRecognitionClient: ActivityRecognitionClient,
) : ActivityUpdatesRegistrar {

    private fun buildConfidenceUpdatePendingIntent(): PendingIntent {
        val intent = Intent(appContext, ConfidenceUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            CONFIDENCE_UPDATE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override fun register() {
        activityRecognitionClient
            .requestActivityUpdates(CONFIDENCE_WINDOW_INTERVAL_MILLIS, buildConfidenceUpdatePendingIntent())
            .addOnSuccessListener {
                Timber.tag("MT-ActivityRecognition").d("Confidence-window activity updates registered")
            }
            .addOnFailureListener { registrationFailure ->
                Timber.tag("MT-ActivityRecognition").e(registrationFailure, "Failed to register confidence-window activity updates")
            }
    }

    override fun unregister() {
        activityRecognitionClient
            .removeActivityUpdates(buildConfidenceUpdatePendingIntent())
            .addOnSuccessListener {
                Timber.tag("MT-ActivityRecognition").d("Confidence-window activity updates unregistered")
            }
            .addOnFailureListener { unregisterFailure ->
                Timber.tag("MT-ActivityRecognition").e(unregisterFailure, "Failed to unregister confidence-window activity updates")
            }
    }
}
