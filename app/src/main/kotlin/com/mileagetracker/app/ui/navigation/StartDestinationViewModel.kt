package com.mileagetracker.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Resolves which screen [MileageTrackerNavHost] should use as its `startDestination` (T-002.1 bug
 * fix). Before this ViewModel existed, the `NavHost` unconditionally started at
 * [Screen.SetupPermissions] on every launch — including for a user who had already completed
 * first-run setup — because nothing ever read
 * [SettingsRepository.observeHasCompletedFirstRunSetup]. `NavHost`'s `startDestination` can only
 * be set once, at first composition, so the caller must wait for [startDestinationRoute] to
 * resolve past `null` ("still loading the flag") before composing the real `NavHost` — see
 * [MileageTrackerNavHost] for how that gate is applied.
 *
 * Reads [SettingsRepository] directly (not [com.mileagetracker.app.domain.repository.TripRepository])
 * per the same architecture boundary [SetupPermissionsViewModel][com.mileagetracker.app.ui.setup.SetupPermissionsViewModel]
 * already follows for this flag.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * `null` while the underlying DataStore read has not yet emitted its first value; otherwise
     * the resolved start route — [Screen.HomeStatus] if first-run setup was already completed,
     * [Screen.SetupPermissions] otherwise.
     */
    val startDestinationRoute: StateFlow<String?> = settingsRepository.observeHasCompletedFirstRunSetup()
        .map { hasCompletedFirstRunSetup ->
            if (hasCompletedFirstRunSetup) Screen.HomeStatus.route else Screen.SetupPermissions.route
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
