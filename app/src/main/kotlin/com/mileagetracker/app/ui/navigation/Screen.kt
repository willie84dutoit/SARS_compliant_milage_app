package com.mileagetracker.app.ui.navigation

/**
 * Sealed route definitions for the 6 screens (brief §7, T-001 blueprint §5). [route] values are
 * the literal navigation-compose route strings; argument-carrying routes build their template here
 * so call sites never hand-construct path segments.
 *
 * OdometerCapture was removed: classification and odometer are now the single
 * TripClassificationScreen — one Save completes both steps.
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
}
