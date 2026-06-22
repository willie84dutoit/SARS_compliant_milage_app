package com.mileagetracker.app.ui.setup

import android.Manifest
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * First-run setup/permissions screen shell (brief §7 #1). Requests the runtime permissions the
 * manual start/stop trip-tracking loop needs (T-002.1 of the full implementation plan): location,
 * activity recognition, camera, and (API 33+) notifications. Denial does not block navigation —
 * the app still opens to Home in limited mode per brief §9, it just cannot track GPS until granted.
 */
@Composable
fun SetupPermissionsScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupPermissionsViewModel = hiltViewModel(),
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            viewModel.onSetupComplete()
            onSetupComplete()
        },
    )

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
            Button(onClick = { permissionLauncher.launch(requiredPermissions()) }) {
                Text("Continue")
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return permissions.toTypedArray()
}
