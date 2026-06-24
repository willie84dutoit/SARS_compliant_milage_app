package com.mileagetracker.app.domain.ocr

/**
 * T-005.3: scores every recognized-text candidate that matches the odometer digit pattern and
 * picks the single most plausible one. ML Kit's on-device Latin text recognizer exposes bounding
 * boxes per text element but no per-element confidence number — this class derives a 0-100
 * confidence score from geometry and text structure instead, per the blueprint's exact weights.
 *
 * Deliberately plain-JVM, no `android.graphics.Rect`: confirmed experimentally (ml-ocr-specialist,
 * T-005) that AGP's unit-test "mockable android.jar" stubs `Rect`'s int-constructor to a no-op
 * (fields silently stay 0) and stubs `centerX()`/`centerY()` to throw
 * `RuntimeException("not mocked")` — the same failure mode `GpsAnchorTracker` documented for
 * `android.location.Location` during T-004. There is no Robolectric dependency in this project to
 * paper over that, so this scorer takes plain `Int` bounding-box edges
 * ([RecognizedTextElement.left]/[top]/[right]/[bottom]) instead of a real `Rect`. Only the thin
 * ML-Kit adapter in [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient] touches the real
 * `android.graphics.Rect` type, converting it to [RecognizedTextElement] at the boundary.
 */
object OdometerCandidateScorer {

    /** Locked project fact: >= this score is reported as [OdometerOcrResult.Confident]. */
    const val CONFIDENT_THRESHOLD_PERCENT = 80

    private const val DIGIT_COUNT_WEIGHT = 0.25
    private const val ISOLATION_WEIGHT = 0.35
    private const val VERTICAL_POSITION_WEIGHT = 0.15
    private const val STRUCTURAL_PURITY_WEIGHT = 0.25

    private const val SIX_DIGIT_SCORE = 100.0
    private const val FIVE_DIGIT_SCORE = 80.0

    /** Characters/substrings whose presence in the candidate's full text line disqualifies purity. */
    private val structuralImpurityMarkers = listOf(":", "°", "AM", "PM")

    private val odometerDigitsPattern = Regex("\\b\\d{5,6}\\b")

    /**
     * One text element as ML Kit reports it: its own matched text plus the bounding box of the
     * *line* it belongs to (line text is what structural-purity checks against, since a clock or
     * "AM"/"PM" suffix is usually a sibling element on the same line as the digit run, not part of
     * the digit element itself) and plain int box edges for geometry scoring.
     */
    data class RecognizedTextElement(
        val text: String,
        val lineText: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val centerX: Double get() = (left + right) / 2.0
        val centerY: Double get() = (top + bottom) / 2.0
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** One scored candidate, returned so callers/tests can see the winning value and its score. */
    data class ScoredCandidate(val valueKm: Double, val confidencePercent: Int)

    /**
     * Scores every element in [allRecognizedElements] whose own [RecognizedTextElement.text]
     * matches the `\b\d{5,6}\b` odometer-digit pattern, against the full set of elements in the
     * frame (so isolation scoring can measure distance to every *other* element), and returns the
     * highest-scoring candidate. Returns null if no element matches the digit pattern at all —
     * that is the "no text found" case, not a zero-confidence candidate.
     *
     * [frameHeight] is the captured image's full pixel height, needed for the vertical-position
     * sub-score's "closeness to the image's vertical center" definition.
     */
    fun findBestCandidate(allRecognizedElements: List<RecognizedTextElement>, frameHeight: Int): ScoredCandidate? {
        val digitCandidates = allRecognizedElements.filter { odometerDigitsPattern.matches(it.text) }
        if (digitCandidates.isEmpty()) return null

        return digitCandidates
            .map { candidate ->
                val confidencePercent = scoreCandidate(candidate, allRecognizedElements, frameHeight)
                ScoredCandidate(valueKm = requireNotNull(candidate.text.toDoubleOrNull()), confidencePercent = confidencePercent)
            }
            .maxByOrNull { it.confidencePercent }
    }

    private fun scoreCandidate(
        candidate: RecognizedTextElement,
        allRecognizedElements: List<RecognizedTextElement>,
        frameHeight: Int,
    ): Int {
        val digitCountScore = scoreDigitCount(candidate.text)
        val isolationScore = scoreIsolation(candidate, allRecognizedElements)
        val verticalPositionScore = scoreVerticalPosition(candidate, frameHeight)
        val structuralPurityScore = scoreStructuralPurity(candidate.lineText)

        val weightedTotal =
            digitCountScore * DIGIT_COUNT_WEIGHT +
                isolationScore * ISOLATION_WEIGHT +
                verticalPositionScore * VERTICAL_POSITION_WEIGHT +
                structuralPurityScore * STRUCTURAL_PURITY_WEIGHT

        return weightedTotal.toInt()
    }

    private fun scoreDigitCount(candidateText: String): Double =
        if (candidateText.length == 6) SIX_DIGIT_SCORE else FIVE_DIGIT_SCORE

    /**
     * Normalized distance from [candidate] to the nearest *other* recognized element anywhere in
     * the frame. Farther away scores higher — this is the sub-score that excludes a clock or
     * temperature reading sitting near the odometer digits. Normalizes against the candidate's own
     * diagonal size so the score is resolution-independent: a gap of "one digit-height away" reads
     * the same whether the photo is 480p or 4K.
     */
    private fun scoreIsolation(candidate: RecognizedTextElement, allRecognizedElements: List<RecognizedTextElement>): Double {
        val otherElements = allRecognizedElements.filter { it !== candidate }
        if (otherElements.isEmpty()) return 100.0

        val nearestDistance = otherElements.minOf { other -> centerDistance(candidate, other) }

        val normalizationUnit = maxOf(candidate.width, candidate.height, 1).toDouble()
        val normalizedDistance = nearestDistance / normalizationUnit

        // A neighbor at >= 3x the candidate's own size away is treated as fully isolated (100);
        // a neighbor overlapping/touching the candidate (distance 0) scores 0.
        val isolationCeilingMultiplier = 3.0
        return (normalizedDistance / isolationCeilingMultiplier * 100.0).coerceIn(0.0, 100.0)
    }

    private fun centerDistance(first: RecognizedTextElement, second: RecognizedTextElement): Double {
        val deltaX = first.centerX - second.centerX
        val deltaY = first.centerY - second.centerY
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    /** Closeness to the image's vertical center; [frameHeight] <= 0 is treated as "unknown" -> neutral 50. */
    private fun scoreVerticalPosition(candidate: RecognizedTextElement, frameHeight: Int): Double {
        if (frameHeight <= 0) return 50.0

        val frameCenterY = frameHeight / 2.0
        val maxPossibleDistance = frameHeight / 2.0
        val candidateDistanceFromCenter = kotlin.math.abs(candidate.centerY - frameCenterY)

        val normalizedDistance = (candidateDistanceFromCenter / maxPossibleDistance).coerceIn(0.0, 1.0)
        return (1.0 - normalizedDistance) * 100.0
    }

    private fun scoreStructuralPurity(lineText: String): Double {
        val containsImpurityMarker = structuralImpurityMarkers.any { marker -> lineText.contains(marker, ignoreCase = false) }
        return if (containsImpurityMarker) 0.0 else 100.0
    }
}
