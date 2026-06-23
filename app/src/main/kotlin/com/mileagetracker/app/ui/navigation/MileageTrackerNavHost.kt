package com.mileagetracker.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
 *
 * T-003: [pendingTripClassificationNavigationStore] is the nav seam for the trip-classification
 * notification — see that class's doc and [com.mileagetracker.app.MainActivity]'s doc for the full
 * picture. `null` is the default so every existing/future caller that doesn't pass one (e.g. a
 * Compose preview) keeps working unchanged; `MainActivity` is the only real caller that supplies a
 * non-null store today.
 *
 * T-002.1 bug fix: `NavHost.startDestination` can only be set once, at first composition, and is
 * fixed for the lifetime of that `NavHost` instance — it cannot be changed by recomposition. Before
 * this fix, it was hard-coded to [Screen.SetupPermissions], so the first-run welcome/permissions
 * screen reappeared on *every* launch, even though [SetupPermissionsScreen] already persists
 * `hasCompletedFirstRunSetup` via [StartDestinationViewModel]'s same
 * [com.mileagetracker.app.domain.repository.SettingsRepository] and its own doc comment calls
 * itself "a one-time, first-run screen." [startDestinationRouteOrNull] gates actually composing the
 * `NavHost` until that flag's first value has been read — a brief loading spinner the very first
 * frame, not a visible flash of the wrong screen.
 */
@Composable
fun MileageTrackerNavHost(
    navController: NavHostController = rememberNavController(),
    pendingTripClassificationNavigationStore: PendingTripClassificationNavigationStore? = null,
    startDestinationViewModel: StartDestinationViewModel = hiltViewModel(),
) {
    val startDestinationRouteOrNull = startDestinationViewModel.startDestinationRoute.collectAsState().value

    if (startDestinationRouteOrNull == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (pendingTripClassificationNavigationStore != null) {
        val pendingTripId = pendingTripClassificationNavigationStore.pendingTripId.collectAsState().value
        LaunchedEffect(pendingTripId) {
            if (pendingTripId == null) return@LaunchedEffect
            // Consume immediately, before navigating: if navigate() were to somehow trigger a
            // recomposition that re-reads a not-yet-cleared pendingTripId (e.g. a future screen
            // added to the back stack target observes the same store), the value is already gone,
            // so this LaunchedEffect can never act on the same tripId twice — the "exactly once"
            // contract the store's class doc requires of its reader.
            pendingTripClassificationNavigationStore.consumePendingTripId()
            navController.navigate(Screen.TripClassification.buildRoute(pendingTripId)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestinationRouteOrNull) {

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
