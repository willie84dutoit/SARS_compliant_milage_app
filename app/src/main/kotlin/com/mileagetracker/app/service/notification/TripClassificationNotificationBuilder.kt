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
 * §5.2: "must be usable from the lock screen if the device allows it"). T-001 scaffolding only —
 * the exact action-button wiring (open classification screen directly vs. wake screen first) is
 * finished alongside T-002/T-003's notification-triggered navigation.
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
