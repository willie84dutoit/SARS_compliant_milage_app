package com.mileagetracker.app.ui.classification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mileagetracker.app.domain.classification.ClassificationRules
import com.mileagetracker.app.domain.model.Trip
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.repository.TripRepository
import com.mileagetracker.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T-001 blueprint §5, row 3: trip being classified, selected classification, business-reason
 * field state, validation error if Work + blank reason. The validation branching itself
 * (`ClassificationRules.validateBusinessReason`) is the one piece of logic kept with
 * android-engineer per the blueprint §6 delegation split — it embeds the same compliance
 * judgment as the state machine's `pending_ocr` resolution.
 */
data class TripClassificationUiState(
    val trip: Trip? = null,
    val selectedClassification: TripClassification? = null,
    val businessReasonText: String = "",
    val validationErrorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class TripClassificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String = requireNotNull(savedStateHandle[Screen.TripClassification.ARG_TRIP_ID])

    private val _uiState = MutableStateFlow(TripClassificationUiState())
    val uiState: StateFlow<TripClassificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = tripRepository.getTripById(tripId)
            _uiState.value = _uiState.value.copy(
                trip = trip,
                selectedClassification = trip?.classification,
                businessReasonText = trip?.businessReason.orEmpty(),
            )
        }
    }

    fun onClassificationSelected(classification: TripClassification) {
        _uiState.value = _uiState.value.copy(selectedClassification = classification, validationErrorMessage = null)
    }

    fun onBusinessReasonChanged(newText: String) {
        _uiState.value = _uiState.value.copy(businessReasonText = newText, validationErrorMessage = null)
    }

    /** Returns true if the trip was saved and the caller should navigate onward. */
    fun onSaveClassification(onSaved: () -> Unit) {
        val currentState = _uiState.value
        val classification = currentState.selectedClassification ?: return

        if (classification == TripClassification.WORK) {
            val validationResult = ClassificationRules.validateBusinessReason(currentState.businessReasonText)
            if (validationResult is ClassificationRules.ValidationResult.Invalid) {
                _uiState.value = currentState.copy(validationErrorMessage = validationResult.reason)
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isSaving = true)
            val businessReasonToStore = if (classification == TripClassification.WORK) {
                currentState.businessReasonText
            } else {
                null
            }
            tripRepository.updateClassification(tripId, classification, businessReasonToStore)
            _uiState.value = _uiState.value.copy(isSaving = false)
            onSaved()
        }
    }
}
