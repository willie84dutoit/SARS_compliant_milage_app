package com.mileagetracker.app.domain.ocr

/**
 * Extracts a plausible odometer reading from raw OCR text using the locked `\b\d{5,6}\b` digit
 * pattern (T-005.2). This flat-string, first-match form is intentionally kept simple and is no
 * longer how [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient] derives its confidence
 * score as of T-005.3 — multi-candidate scoring against bounding-box geometry now lives in
 * [OdometerCandidateScorer], which applies the same digit pattern per recognized text *element*
 * (not the whole flattened block) so isolation/position scoring has per-element boxes to work
 * with. This object is retained because (a) it is still a correct, simpler building block for any
 * future caller that only needs "is there a plausible odometer-shaped digit run in this text" with
 * no geometry available, and (b) deleting it would silently drop its 4 existing unit tests' coverage
 * for the regex's own correctness (5 vs 6 digits, no-match, too-short) — that coverage is still
 * valid; it gates the regex [OdometerCandidateScorer] also depends on.
 */
object OdometerTextParser {

    /** Matches a 5-6 digit run, the expected odometer digit-count range (brief-adjacent convention). */
    private val odometerDigitsPattern = Regex("\\b\\d{5,6}\\b")

    /**
     * Returns the first 5-6 digit run found in [rawRecognizedText], or null if none match.
     * No confidence scoring happens here — see [OdometerCandidateScorer] for the real,
     * geometry-aware confidence derivation used by the production OCR client.
     */
    fun extractCandidateOdometerKm(rawRecognizedText: String): Double? {
        val match = odometerDigitsPattern.find(rawRecognizedText) ?: return null
        return match.value.toDoubleOrNull()
    }
}
