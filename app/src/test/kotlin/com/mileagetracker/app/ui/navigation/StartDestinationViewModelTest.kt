package com.mileagetracker.app.ui.navigation

import com.mileagetracker.app.domain.repository.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * T-002.1 bug-fix regression coverage: [MileageTrackerNavHost] used to hard-code
 * [Screen.SetupPermissions] as its `startDestination` regardless of
 * [com.mileagetracker.app.domain.repository.SettingsRepository.observeHasCompletedFirstRunSetup] —
 * these tests pin down [StartDestinationViewModel]'s resolution logic directly against a
 * hand-written [FakeSettingsRepository] (no mocking framework, per project convention).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartDestinationViewModelTest {

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
    fun `resolves to SetupPermissions route when first-run setup has not been completed`() {
        val fakeSettingsRepository = FakeSettingsRepository(initialHasCompletedFirstRunSetup = false)
        val viewModel = StartDestinationViewModel(fakeSettingsRepository)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Screen.SetupPermissions.route, viewModel.startDestinationRoute.value)
    }

    @Test
    fun `resolves to HomeStatus route when first-run setup has already been completed`() {
        val fakeSettingsRepository = FakeSettingsRepository(initialHasCompletedFirstRunSetup = true)
        val viewModel = StartDestinationViewModel(fakeSettingsRepository)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Screen.HomeStatus.route, viewModel.startDestinationRoute.value)
    }
}
