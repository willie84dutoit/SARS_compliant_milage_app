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
import com.mileagetracker.app.ui.settings.SettingsScreen
import com.mileagetracker.app.ui.setup.SetupPermissionsScreen

/**
 * Single NavHost for all 6 screens (T-001 blueprint §5). Each screen pulls its own
 * `@HiltViewModel` internally via `hiltViewModel()` — this host only wires routes and
 * navigation callbacks, no business logic.
 *
 * T-003: [pendingTripClassificationNavigationStore] is the nav seam for the trip-classification
 * notification. `null` default keeps existing callers and Compose previews working unchanged.
 *
 * T-002.1 fix: `NavHost.startDestination` is gated until [StartDestinationViewModel] has read
 * the first-run flag — avoids re-showing SetupPermissions on every launch.
 *
 * C-2 removal: the former C-2 back-stack seam (homeStatusViewModelOrNull obtained here to call
 * onClassificationSavedNavigatingToOdometer) is removed. Classification now atomically saves
 * classification + odometer in one Save; onClassificationSaved navigates directly to HomeStatus.
 *
 * T-022: the autoRoutedToClassificationTripId gate in HomeStatusViewModel is kept. Cancel on
 * TripClassificationScreen still uses popBackStack() (not navigate(HomeStatus)) so the gate on
 * the existing Home back-stack entry remains set, preventing re-fire.
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
            // Consume immediately before navigating — "exactly once" contract.
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
            val tripId = requireNotNull(
                backStackEntry.arguments?.getString(Screen.TripClassification.ARG_TRIP_ID),
            )
            TripClassificationScreen(
                tripId = tripId,
                onClassificationSaved = {
                    // Trip is now COMPLETED (or PENDING_BUSINESS_REASON) at this point — the
                    // ViewModel's Save atomically wrote classification + odometer + signed the trip
                    // before calling this callback. Navigate to HomeStatus and clear the entire
                    // trip-completion sub-flow from the back stack.
                    navController.navigate(Screen.HomeStatus.route) {
                        popUpTo(Screen.HomeStatus.route) { inclusive = true }
                    }
                },
                // T-024/T-022: popBackStack() returns to the existing Home entry whose
                // autoRoutedToClassificationTripId gate is already set. An explicit navigate
                // would push a new Home entry with a fresh ViewModel (null gate) and re-trigger
                // the auto-redirect trap.
                onCancelClassification = { navController.popBackStack() },
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
