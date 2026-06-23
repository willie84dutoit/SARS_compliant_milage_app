package com.mileagetracker.app.ui.setup

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import timber.log.Timber

private const val SETUP_PERMISSIONS_LOG_TAG = "MT-UI"

/**
 * First-run setup/permissions screen (brief §7 #1, T-002.1). Requests the runtime permissions the
 * automatic + manual trip-tracking loop needs: location (fine/coarse, then background as a
 * mandatory **second step** — see below), camera, activity recognition, and (API 33+)
 * notifications. Denial does not block navigation — the app still opens to Home in limited mode
 * per brief §8/§9; [SetupPermissionsViewModel] computes exactly when that banner applies.
 *
 * Two-step location request (Android 10+ / API 29+ requirement, not a stylistic choice): the OS
 * silently fails to grant `ACCESS_BACKGROUND_LOCATION` if it is requested in the same
 * `RequestMultiplePermissions()` call as foreground location — it must be requested in its own,
 * separate call, *after* foreground location is confirmed granted. [backgroundLocationLauncher]
 * only fires once [foregroundPermissionLauncher]'s result confirms that.
 *
 * Field finding (2026-06-23, see `team/TASKS.md` T-002 card and this task's investigation in
 * `team/LOGS.md`): the original shell only called `permissionLauncher.launch(...)` from the
 * "Continue" button's `onClick` — the dialog never fires on its own just by viewing this screen,
 * by design (Android does not support showing a permission dialog automatically on composition
 * without a user gesture in a healthy UX flow), so if a field tester never tapped Continue, or
 * tapped it once, denied, and the OS then suppressed the dialog on a later relaunch per its own
 * "don't ask again after repeated denial" policy, the dialog would appear to "never fire" without
 * actually being an OS/installer-level block. See this composable's investigation note below for
 * why the restricted-settings theory does not explain it.
 */
@Composable
fun SetupPermissionsScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupPermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val setupPermissionsPlanner = remember { SetupPermissionsPlanner() }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isBackgroundLocationGranted ->
            Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i(
                "SetupPermissionsScreen: background-location second-step result granted=%s",
                isBackgroundLocationGranted,
            )
            viewModel.applyGrantSnapshot(currentGrantSnapshot(context).copy(isBackgroundLocationGranted = isBackgroundLocationGranted))
            viewModel.onSetupComplete()
            onSetupComplete()
        },
    )

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grantResults ->
            logFirstRoundResult(grantResults)
            val snapshotAfterFirstRound = currentGrantSnapshot(context)
            viewModel.applyGrantSnapshot(snapshotAfterFirstRound)

            if (setupPermissionsPlanner.shouldRequestBackgroundLocation(snapshotAfterFirstRound)) {
                Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i(
                    "SetupPermissionsScreen: foreground location granted — requesting background location as second step",
                )
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i(
                    "SetupPermissionsScreen: skipping background-location second step (already granted, or foreground location was denied)",
                )
                viewModel.onSetupComplete()
                onSetupComplete()
            }
        },
    )

    // Returning user (or a user who granted everything via phone Settings rather than this
    // screen's dialog, e.g. the field finding's manual-grant path) sees real, current state
    // instead of the ViewModel's hard-coded `false` defaults. Runs once per composition of this
    // screen, not on every recomposition.
    LaunchedEffect(Unit) {
        viewModel.applyGrantSnapshot(currentGrantSnapshot(context))
    }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Welcome to Mileage Tracker")
            Text("We need location, camera, and notification access to detect and classify trips automatically.")
            if (uiState.isLimitedModeBannerVisible) {
                Text(
                    "Without background location and notification access, automatic detection is " +
                        "disabled — you can still track trips manually with Start/Stop.",
                )
            }
            // Field finding advisory (see class doc): if a sideloaded install's dialog does not
            // appear at all after tapping Continue more than once, restricted settings is the
            // documented Android 13+ block for Accessibility/Notification-Listener/Device-Admin
            // permissions specifically — this app requests none of those, so that block does not
            // apply here. This note still helps a user who lands in that state for an unrelated
            // reason (e.g. a previous "don't ask again" denial) recover via Settings.
            Text(
                "If a permission prompt does not appear after tapping Continue, open phone " +
                    "Settings > Apps > Mileage Tracker > Permissions to grant it manually.",
            )
            Button(
                onClick = {
                    Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i("SetupPermissionsScreen: Continue tapped")
                    val permissionsToRequest = setupPermissionsPlanner.firstRoundPermissionsToRequest(currentGrantSnapshot(context))
                    if (permissionsToRequest.isEmpty()) {
                        Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i(
                            "SetupPermissionsScreen: all first-round permissions already granted — checking background location",
                        )
                        val snapshot = currentGrantSnapshot(context)
                        if (setupPermissionsPlanner.shouldRequestBackgroundLocation(snapshot)) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            viewModel.onSetupComplete()
                            onSetupComplete()
                        }
                    } else {
                        foregroundPermissionLauncher.launch(permissionsToRequest)
                    }
                },
            ) {
                Text("Continue")
            }
        }
    }
}

private fun logFirstRoundResult(grantResults: Map<String, Boolean>) {
    grantResults.forEach { (permission, isGranted) ->
        Timber.tag(SETUP_PERMISSIONS_LOG_TAG).i(
            "SetupPermissionsScreen: permission result %s granted=%s",
            permission,
            isGranted,
        )
    }
}

/**
 * Reads real, current OS grant state via `ContextCompat.checkSelfPermission` — the source of
 * truth for "what does the user actually have granted right now," independent of any single
 * launcher callback. Notifications are normalized to `true` below API 33, where no runtime
 * notification permission exists (the brief's permission matrix, §8, only lists
 * `POST_NOTIFICATIONS` for Android 13+).
 */
private fun currentGrantSnapshot(context: Context): PermissionGrantSnapshot {
    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val isNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isGranted(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

    return PermissionGrantSnapshot(
        isFineLocationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        isCoarseLocationGranted = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION),
        isBackgroundLocationGranted = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        isCameraGranted = isGranted(Manifest.permission.CAMERA),
        isActivityRecognitionGranted = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
        isNotificationsGranted = isNotificationsGranted,
    )
}
