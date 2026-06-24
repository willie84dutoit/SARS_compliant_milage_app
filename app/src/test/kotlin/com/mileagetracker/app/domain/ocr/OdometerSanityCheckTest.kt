package com.mileagetracker.app.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerSanityCheckTest {

    @Test
    fun `a confident candidate at or above the trip's starting odometer is accepted as Confident`() {
        // Arrange
        val scoredCandidate = OdometerCandidateScorer.ScoredCandidate(valueKm = 50100.0, confidencePercent = 90)

        // Act
        val ocrResult = OdometerSanityCheck.toOcrResult(scoredCandidate, startOdometerKm = 50000.0)

        // Assert
        val confidentResult = ocrResult as OdometerOcrResult.Confident
        assertEquals(50100.0, confidentResult.valueKm, 0.001)
        assertEquals(90, confidentResult.confidencePercent)
    }

    @Test
    fun `a confident candidate below the trip's starting odometer is rejected and demoted to LowConfidence`() {
        // Arrange: the OCR engine is "confident" about a value, but it is physically impossible
        // — an odometer cannot decrease over the course of a trip.
        val scoredCandidate = OdometerCandidateScorer.ScoredCandidate(valueKm = 49000.0, confidencePercent = 95)

        // Act
        val ocrResult = OdometerSanityCheck.toOcrResult(scoredCandidate, startOdometerKm = 50000.0)

        // Assert: must NOT be Confident — falls through to manual entry instead.
        assertTrue(ocrResult is OdometerOcrResult.LowConfidence)
        val lowConfidenceResult = ocrResult as OdometerOcrResult.LowConfidence
        assertEquals(49000.0, lowConfidenceResult.bestGuessValueKm)
        // The real computed score is carried forward, not a placeholder — manual entry still
        // shows the actual confidence the engine had, even though it was rejected.
        assertEquals(95, lowConfidenceResult.confidencePercent)
    }

    @Test
    fun `a candidate exactly equal to the trip's starting odometer is accepted as Confident`() {
        // Arrange: zero distance traveled is valid (trip started and immediately stopped).
        val scoredCandidate = OdometerCandidateScorer.ScoredCandidate(valueKm = 50000.0, confidencePercent = 85)

        // Act
        val ocrResult = OdometerSanityCheck.toOcrResult(scoredCandidate, startOdometerKm = 50000.0)

        // Assert
        assertTrue(ocrResult is OdometerOcrResult.Confident)
    }

    @Test
    fun `a low-confidence candidate below the threshold stays LowConfidence regardless of the sanity check`() {
        // Arrange: confidence is already below 80, independent of the odometer-decrease rule.
        val scoredCandidate = OdometerCandidateScorer.ScoredCandidate(valueKm = 50100.0, confidencePercent = 60)

        // Act
        val ocrResult = OdometerSanityCheck.toOcrResult(scoredCandidate, startOdometerKm = 50000.0)

        // Assert
        val lowConfidenceResult = ocrResult as OdometerOcrResult.LowConfidence
        assertEquals(60, lowConfidenceResult.confidencePercent)
    }
}
