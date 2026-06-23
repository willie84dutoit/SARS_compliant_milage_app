package com.mileagetracker.app.ui.setup

import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-002.1 tests for [SetupPermissionsPlanner] — pure decision logic, no Android framework
 * dependency beyond `Build.VERSION_CODES` int constants, so this runs as a plain JVM unit test
 * with no Robolectric/instrumentation needed. Consistent with this project's "hand-written fakes
 * only, no mocking framework" convention — there is nothing here that needs faking.
 */
class SetupPermissionsPlannerTest {

    private val allGranted = PermissionGrantSnapshot(
        isFineLocationGranted = true,
        isCoarseLocationGranted = true,
        isBackgroundLocationGranted = true,
        isCameraGranted = true,
        isActivityRecognitionGranted = true,
        isNotificationsGranted = true,
    )

    private val noneGranted = PermissionGrantSnapshot(
        isFineLocationGranted = false,
        isCoarseLocationGranted = false,
        isBackgroundLocationGranted = false,
        isCameraGranted = false,
        isActivityRecognitionGranted = false,
        isNotificationsGranted = false,
    )

    // --- firstRoundPermissionsToRequest ---

    @Test
    fun `firstRoundPermissionsToRequest returns empty array when everything already granted on API 33+`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.TIRAMISU)

        val result = planner.firstRoundPermissionsToRequest(allGranted)

        assertArrayEquals(emptyArray<String>(), result)
    }

    @Test
    fun `firstRoundPermissionsToRequest requests all five permissions on API 33+ when none granted`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.TIRAMISU)

        val result = planner.firstRoundPermissionsToRequest(noneGranted)

        assertArrayEquals(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ),
            result,
        )
    }

    @Test
    fun `firstRoundPermissionsToRequest omits POST_NOTIFICATIONS below API 33`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.Q)

        val result = planner.firstRoundPermissionsToRequest(noneGranted)

        assertArrayEquals(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
            ),
            result,
        )
    }

    @Test
    fun `firstRoundPermissionsToRequest only includes the permissions still missing`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.TIRAMISU)
        val partiallyGranted = noneGranted.copy(isFineLocationGranted = true, isCameraGranted = true)

        val result = planner.firstRoundPermissionsToRequest(partiallyGranted)

        assertArrayEquals(
            arrayOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ),
            result,
        )
    }

    // --- shouldRequestBackgroundLocation ---

    @Test
    fun `shouldRequestBackgroundLocation is false below API 29 regardless of grant state`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.P)

        assertFalse(planner.shouldRequestBackgroundLocation(noneGranted))
        assertFalse(planner.shouldRequestBackgroundLocation(allGranted.copy(isBackgroundLocationGranted = false)))
    }

    @Test
    fun `shouldRequestBackgroundLocation is false when background location is already granted`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.Q)

        assertFalse(planner.shouldRequestBackgroundLocation(allGranted))
    }

    @Test
    fun `shouldRequestBackgroundLocation is false when neither fine nor coarse foreground location is granted`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.Q)

        assertFalse(planner.shouldRequestBackgroundLocation(noneGranted))
    }

    @Test
    fun `shouldRequestBackgroundLocation is true when fine location is granted and background is not`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.Q)
        val fineLocationOnly = noneGranted.copy(isFineLocationGranted = true)

        assertTrue(planner.shouldRequestBackgroundLocation(fineLocationOnly))
    }

    @Test
    fun `shouldRequestBackgroundLocation is true when only coarse location is granted and background is not`() {
        val planner = SetupPermissionsPlanner(sdkVersion = Build.VERSION_CODES.Q)
        val coarseLocationOnly = noneGranted.copy(isCoarseLocationGranted = true)

        assertTrue(planner.shouldRequestBackgroundLocation(coarseLocationOnly))
    }

    // --- isLimitedModeRequired ---

    @Test
    fun `isLimitedModeRequired is false when everything is granted`() {
        val planner = SetupPermissionsPlanner()

        assertFalse(planner.isLimitedModeRequired(allGranted))
    }

    @Test
    fun `isLimitedModeRequired is true when background location is denied`() {
        val planner = SetupPermissionsPlanner()
        val backgroundLocationDenied = allGranted.copy(isBackgroundLocationGranted = false)

        assertTrue(planner.isLimitedModeRequired(backgroundLocationDenied))
    }

    @Test
    fun `isLimitedModeRequired is true when notifications are denied`() {
        val planner = SetupPermissionsPlanner()
        val notificationsDenied = allGranted.copy(isNotificationsGranted = false)

        assertTrue(planner.isLimitedModeRequired(notificationsDenied))
    }

    @Test
    fun `isLimitedModeRequired is false when only camera is denied`() {
        val planner = SetupPermissionsPlanner()
        val cameraDenied = allGranted.copy(isCameraGranted = false)

        assertFalse(planner.isLimitedModeRequired(cameraDenied))
    }

    @Test
    fun `isLimitedModeRequired is true when both background location and notifications are denied`() {
        val planner = SetupPermissionsPlanner()
        val bothDenied = allGranted.copy(isBackgroundLocationGranted = false, isNotificationsGranted = false)

        assertTrue(planner.isLimitedModeRequired(bothDenied))
    }
}
