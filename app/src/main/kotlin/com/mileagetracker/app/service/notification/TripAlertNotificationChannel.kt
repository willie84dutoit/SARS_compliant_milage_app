package com.mileagetracker.app.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.mileagetracker.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the locked `mileage_tracker_trip_alerts` channel at HIGH importance (brief §5.2).
 * Channel creation is idempotent — calling [createChannel] more than once (e.g. across process
 * restarts) is a documented no-op per Android's NotificationManager contract, not something this
 * class needs to guard against itself.
 */
@Singleton
class TripAlertNotificationChannel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    companion object {
        const val CHANNEL_ID = "mileage_tracker_trip_alerts"
    }

    fun createChannel() {
        val notificationManager = ContextCompat.getSystemService(appContext, NotificationManager::class.java)
            ?: error("NotificationManager unavailable — cannot create $CHANNEL_ID channel")

        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notification_channel_trip_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = appContext.getString(R.string.notification_channel_trip_alerts_description)
        }

        notificationManager.createNotificationChannel(channel)
    }
}
