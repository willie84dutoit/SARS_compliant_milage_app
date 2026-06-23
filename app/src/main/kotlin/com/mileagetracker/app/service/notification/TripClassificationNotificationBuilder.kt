package com.mileagetracker.app.service.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.mileagetracker.app.MainActivity
import com.mileagetracker.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds the trip-classification prompt notification, with a lock-screen-usable action (brief
 * §5.2: "must be usable from the lock screen if the device allows it").
 *
 * T-003 (final wiring): tapping the notification fires [contentIntent] targeting
 * [com.mileagetracker.app.MainActivity] with [ACTION_OPEN_TRIP_CLASSIFICATION] /
 * [EXTRA_TRIP_ID]. `MainActivity.onCreate`/`onNewIntent` extract the tripId and push it through
 * `PendingTripClassificationNavigationStore`, which `MileageTrackerNavHost` consumes to navigate
 * straight to `Screen.TripClassification`, satisfying "open the classification screen directly,
 * not just the home screen." `MainActivity` itself calls `setShowWhenLocked(true)`/
 * `setTurnScreenOn(true)` (plus the matching manifest attributes) to wake/show over the lock
 * screen on tap; this builder deliberately does NOT also set a full-screen intent
 * (`setFullScreenIntent`) — the brief's requirement is "wake the screen **when the user taps the
 * notification action**," not an unprompted heads-up popup over the lock screen the way an
 * incoming call behaves, and `USE_FULL_SCREEN_INTENT` carries its own elevated-permission/OEM-
 * revocation risk on API 33+ that this lower-risk tap-driven design avoids entirely. If a device's
 * own policy disallows showing any activity over its lock screen, Android requires unlock first
 * and then shows [com.mileagetracker.app.MainActivity] normally — satisfying brief §5.2's explicit
 * fallback ("must still open the app normally") with no extra branching needed in this class.
 */
class TripClassificationNotificationBuilder @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    fun build(tripId: String): Notification {
        val openClassificationIntent = Intent(appContext, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRIP_CLASSIFICATION
            putExtra(EXTRA_TRIP_ID, tripId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            tripId.hashCode(),
            openClassificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(appContext, TripAlertNotificationChannel.CHANNEL_ID)
            .setContentTitle("Trip detected")
            .setContentText("Tap to classify this trip as Work or Private")
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val ACTION_OPEN_TRIP_CLASSIFICATION = "com.mileagetracker.app.action.OPEN_TRIP_CLASSIFICATION"
        const val EXTRA_TRIP_ID = "com.mileagetracker.app.extra.TRIP_ID"
    }
}
