package com.mileagetracker.app.domain.ocr

/**
 * Result of running OCR against an odometer photo (brief §5.3). The domain layer defines this
 * shape; [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient] is the only producer
 * (ml-ocr-specialist owns the ML Kit configuration behind it, per T-001 blueprint open question 2).
 */
sealed interface OdometerOcrResult {

    /** Confidence >= the locked 80% threshold — [valueKm] is accepted without manual entry. */
    data class Confident(val valueKm: Double, val confidencePercent: Int) : OdometerOcrResult

    /** Text was found but confidence is below 80% — manual entry fallback must be shown. */
    data class LowConfidence(val bestGuessValueKm: Double?, val confidencePercent: Int) : OdometerOcrResult

    /** No digit sequence matched the expected odometer pattern at all. */
    data object NoTextFound : OdometerOcrResult
}
