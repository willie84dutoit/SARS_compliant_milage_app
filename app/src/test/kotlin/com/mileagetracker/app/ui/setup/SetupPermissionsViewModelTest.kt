package com.mileagetracker.app.ui.setup

import com.mileagetracker.app.domain.repository.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T-002.1 tests for [SetupPermissionsViewModel] — exercises [SetupPermissionsViewModel.applyGrantSnapshot]
 * (the seam the screen calls after every `ContextCompat.checkSelfPermission` read or
 * `ActivityResult` callback) and [SetupPermissionsViewModel.onSetupComplete] against a hand-written
 * [FakeSettingsRepository], consistent with this project's no-mocking-framework convention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupPermissionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has every granted flag false and the limited-mode banner hidden`() {
        val viewModel = SetupPermissionsViewModel(FakeSettingsRepository())

        val state = viewModel.uiState.value

        assertFalse(state.isFineLocationGranted)
        assertFalse(state.isBackgroundLocationGranted)
        assertFalse(state.isCameraGranted)
        assertFalse(state.isNotificationsGranted)
        assertFalse(state.isLimitedModeBannerVisible)
    }

    @Test
    fun `applyGrantSnapshot copies every flag from the snapshot into uiState`() {
        val viewModel = SetupPermissionsViewModel(FakeSettingsRepository())
        val snapshot = PermissionGrantSnapshot(
            isFineLocationGranted = true,
            isCoarseLocationGranted = true,
            isBackgroundLocationGranted = true,
            isCameraGranted = true,
            isActivityRecognitionGranted = true,
            isNotificationsGranted = true,
        )

        viewModel.applyGrantSnapshot(snapshot)

        val state = viewModel.uiState.value
        assertTrue(state.isFineLocationGranted)
        assertTrue(state.isCoarseLocationGranted)
        assertTrue(state.isBackgroundLocationGranted)
        assertTrue(state.isCameraGranted)
        assertTrue(state.isActivityRecognitionGranted)
        assertTrue(state.isNotificationsGranted)
    }

    @Test
    fun `applyGrantSnapshot shows the limited-mode banner when background location is denied`() {
        val viewModel = SetupPermissionsViewModel(FakeSettingsRepository())
        val snapshot = PermissionGrantSnapshot(
            isFineLocationGranted = true,
            isCoarseLocationGranted = true,
            isBackgroundLocationGranted = false,
            isCameraGranted = true,
            isActivityRecognitionGranted = true,
            isNotificationsGranted = true,
        )

        viewModel.applyGrantSnapshot(snapshot)

        assertTrue(viewModel.uiState.value.isLimitedModeBannerVisible)
    }

    @Test
    fun `applyGrantSnapshot hides the limited-mode banner when only camera is denied`() {
        val viewModel = SetupPermissionsViewModel(FakeSettingsRepository())
        val snapshot = PermissionGrantSnapshot(
            isFineLocationGranted = true,
            isCoarseLocationGranted = true,
            isBackgroundLocationGranted = true,
            isCameraGranted = false,
            isActivityRecognitionGranted = true,
            isNotificationsGranted = true,
        )

        viewModel.applyGrantSnapshot(snapshot)

        assertFalse(viewModel.uiState.value.isLimitedModeBannerVisible)
    }

    @Test
    fun `onSetupComplete persists hasCompletedFirstRunSetup as true`() = runTest {
        val fakeSettingsRepository = FakeSettingsRepository()
        val viewModel = SetupPermissionsViewModel(fakeSettingsRepository)

        viewModel.onSetupComplete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeSettingsRepository.hasCompletedFirstRunSetupFlow.value)
    }
}
