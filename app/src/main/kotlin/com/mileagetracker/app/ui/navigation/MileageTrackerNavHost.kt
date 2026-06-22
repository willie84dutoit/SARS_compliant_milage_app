package com.mileagetracker.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mileagetracker.app.ui.classification.TripClassificationScreen
import com.mileagetracker.app.ui.export.ExportScreen
import com.mileagetracker.app.ui.history.TripHistoryScreen
import com.mileagetracker.app.ui.home.HomeStatusScreen
import com.mileagetracker.app.ui.odometer.OdometerCaptureScreen
import com.mileagetracker.app.ui.settings.SettingsScreen
import com.mileagetracker.app.ui.setup.SetupPermissionsScreen

/**
 * Single NavHost for all 7 screens (T-001 blueprint §5). Each screen pulls its own
 * `@HiltViewModel` internally via `hiltViewModel()` — this host only wires routes and
 * navigation callbacks, no business logic.
 */
@Composable
fun MileageTrackerNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.SetupPermissions.route) {

        composable(Screen.SetupPermissions.route) {
            SetupPermissionsScreen(
                onSetupComplete = {
                    // Setup is a one-time, first-run screen — pop it off the back stack so the
                    // system Back button from Home exits the app instead of returning here.
                    navController.navigate(Screen.HomeStatus.route) {
                        popUpTo(Screen.SetupPermissions.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.HomeStatus.route) {
            HomeStatusScreen(
                onNavigateToTripHistory = { navController.navigate(Screen.TripHistory.route) },
                onNavigateToExport = { navController.navigate(Screen.Export.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onTripAwaitingClassification = { tripId ->
                    navController.navigate(Screen.TripClassification.buildRoute(tripId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.TripClassification.route) { backStackEntry ->
            val tripId = requireNotNull(backStackEntry.arguments?.getString(Screen.TripClassification.ARG_TRIP_ID))
            TripClassificationScreen(
                tripId = tripId,
                onClassificationSaved = {
                    navController.navigate(Screen.OdometerCapture.buildRoute(tripId)) {
                        // Classification is a one-shot step in the trip-completion flow, not a
                        // place the user should land on via Back — pop it so Back from Odometer
                        // Capture returns to Home, not to a stale Classification screen.
                        popUpTo(Screen.TripClassification.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.OdometerCapture.route) { backStackEntry ->
            val tripId = requireNotNull(backStackEntry.arguments?.getString(Screen.OdometerCapture.ARG_TRIP_ID))
            OdometerCaptureScreen(
                tripId = tripId,
                onCaptureComplete = {
                    // Same reasoning as above: pop the entire trip-completion sub-flow (back to
                    // and including the Home entry that triggered it) so the back stack never
                    // accumulates one Home/Classification/Odometer triple per completed trip.
                    navController.navigate(Screen.HomeStatus.route) {
                        popUpTo(Screen.HomeStatus.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.TripHistory.route) {
            TripHistoryScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Export.route) {
            ExportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
