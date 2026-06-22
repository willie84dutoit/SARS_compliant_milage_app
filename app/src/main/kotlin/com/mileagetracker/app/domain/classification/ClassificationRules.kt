package com.mileagetracker.app.domain.classification

import com.mileagetracker.app.domain.model.TripClassification

/**
 * The single gate for "is this trip allowed to complete" with respect to the business-reason
 * requirement (brief §5.2/§5.9). The UI/ViewModel layer must call this — never inline a blank
 * check itself — so the rule has exactly one definition across Trip Classification, Trip
 * History (pending-reason banner), and CSV export (T-001 blueprint §5's hard rule).
 */
object ClassificationRules {

    /**
     * Returns true only when [classification] is PRIVATE, or WORK with a non-blank
     * [businessReason]. A reason consisting only of whitespace is treated as blank.
     */
    fun isBusinessReasonSatisfied(classification: TripClassification, businessReason: String?): Boolean {
        return when (classification) {
            TripClassification.PRIVATE -> true
            TripClassification.WORK -> !businessReason.isNullOrBlank()
        }
    }

    /**
     * Validates a business-reason submission for a WORK trip. Returns the trimmed reason on
     * success, or null if it is blank. The trip's stored value must be the exact, untrimmed
     * user input per brief §5.2 ("stored exactly as entered") for logbook compliance — trimming
     * here is only for the blank-check decision, not for what gets persisted. Callers must pass
     * the original, untrimmed string to the repository on success.
     */
    fun validateBusinessReason(businessReason: String?): ValidationResult {
        return if (businessReason.isNullOrBlank()) {
            ValidationResult.Invalid(reason = "Business reason is required for Work trips")
        } else {
            ValidationResult.Valid
        }
    }

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }
}
