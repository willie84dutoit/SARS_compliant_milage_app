package com.mileagetracker.app

import android.app.Application
import com.mileagetracker.app.data.logging.FileLoggingTree
import com.mileagetracker.app.service.notification.TripAlertNotificationChannel
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Application entry point. Per T-001 build order step 5/6: notification channel creation is a
 * one-time `onCreate` call, done before any service that might post to it.
 *
 * T-018: [FileLoggingTree] is installed here, once, before anything else runs — every
 * `Timber.tag(...).log(...)` call anywhere in the app (service, OCR, export, repository) writes
 * to the on-device log file from the very first line of `onCreate` onward, so a field tester's
 * crash report always has a log file to attach, not just whatever happened to be in logcat.
 */
@HiltAndroidApp
class MileageTrackerApplication : Application() {

    @Inject
    lateinit var tripAlertNotificationChannel: TripAlertNotificationChannel

    override fun onCreate() {
        super.onCreate()
        Timber.plant(FileLoggingTree(logsDirectory = File(filesDir, "logs")))
        tripAlertNotificationChannel.createChannel()
    }
}
