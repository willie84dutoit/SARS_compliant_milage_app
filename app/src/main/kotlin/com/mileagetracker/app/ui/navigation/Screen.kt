package com.mileagetracker.app.ui.navigation

/**
 * Sealed route definitions for the 7 screens (brief §7, T-001 blueprint §5). [route] values are
 * the literal navigation-compose route strings; argument-carrying routes (e.g. classification by
 * tripId) build their template here so call sites never hand-construct path segments.
 */
sealed class Screen(val route: String) {
    data object SetupPermissions : Screen("setup_permissions")
    data object HomeStatus : Screen("home_status")
    data object TripHistory : Screen("trip_history")
    data object Export : Screen("export")
    data object Settings : Screen("settings")

    data object TripClassification : Screen("trip_classification/{tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun buildRoute(tripId: String) = "trip_classification/$tripId"
    }

    data object OdometerCapture : Screen("odometer_capture/{tripId}") {
        const val ARG_TRIP_ID = "tripId"
        fun buildRoute(tripId: String) = "odometer_capture/$tripId"
    }
}
