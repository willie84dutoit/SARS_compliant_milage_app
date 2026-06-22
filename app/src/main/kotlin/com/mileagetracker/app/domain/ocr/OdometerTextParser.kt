package com.mileagetracker.app.domain.ocr

/**
 * Extracts a plausible odometer reading from raw OCR text. T-001 scaffolding only: this is the
 * regex/parsing skeleton; ml-ocr-specialist (T-005) owns tuning the bounding-box filtering rules
 * and any preprocessing-driven adjustments once the real ML Kit client exists.
 */
object OdometerTextParser {

    /** Matches a 5-6 digit run, the expected odometer digit-count range (brief-adjacent convention). */
    private val odometerDigitsPattern = Regex("\\b\\d{5,6}\\b")

    /**
     * Returns the first 5-6 digit run found in [rawRecognizedText], or null if none match.
     * Does not itself decide confidence — that comes from the OCR engine's own per-block score,
     * combined with this match in [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient].
     */
    fun extractCandidateOdometerKm(rawRecognizedText: String): Double? {
        val match = odometerDigitsPattern.find(rawRecognizedText) ?: return null
        return match.value.toDoubleOrNull()
    }
}
