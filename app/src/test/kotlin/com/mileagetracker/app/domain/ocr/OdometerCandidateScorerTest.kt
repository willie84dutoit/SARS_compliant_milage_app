package com.mileagetracker.app.domain.ocr

import com.mileagetracker.app.domain.ocr.OdometerCandidateScorer.RecognizedTextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerCandidateScorerTest {

    private val frameHeight = 1000

    @Test
    fun `isolated 6-digit candidate near the vertical center scores at or above the confident threshold`() {
        // Arrange: a single 6-digit element, vertically centered, no other text anywhere in frame.
        val odometerElement = RecognizedTextElement(
            text = "123456",
            lineText = "123456",
            left = 400,
            top = 480,
            right = 600,
            bottom = 520,
        )

        // Act
        val bestCandidate = OdometerCandidateScorer.findBestCandidate(
            allRecognizedElements = listOf(odometerElement),
            frameHeight = frameHeight,
        )

        // Assert
        assertEquals(123456.0, bestCandidate?.valueKm)
        assertTrue(
            "expected confidencePercent >= ${OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT} but was ${bestCandidate?.confidencePercent}",
            (bestCandidate?.confidencePercent ?: 0) >= OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT,
        )
    }

    @Test
    fun `same 6-digit candidate placed directly adjacent to a clock-like element scores below the confident threshold`() {
        // Arrange: the same digit element and position as the isolated case, but now a "14:32"
        // clock-style element sits immediately beside it on the same line.
        val odometerElement = RecognizedTextElement(
            text = "123456",
            lineText = "123456 14:32",
            left = 400,
            top = 480,
            right = 600,
            bottom = 520,
        )
        val clockElement = RecognizedTextElement(
            text = "14:32",
            lineText = "123456 14:32",
            left = 610,
            top = 480,
            right = 700,
            bottom = 520,
        )

        // Act
        val bestCandidate = OdometerCandidateScorer.findBestCandidate(
            allRecognizedElements = listOf(odometerElement, clockElement),
            frameHeight = frameHeight,
        )

        // Assert
        assertEquals(123456.0, bestCandidate?.valueKm)
        assertTrue(
            "expected confidencePercent < ${OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT} but was ${bestCandidate?.confidencePercent}",
            (bestCandidate?.confidencePercent ?: 100) < OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT,
        )
    }

    @Test
    fun `picks the highest scoring candidate when multiple digit runs are present in the frame`() {
        // Arrange: an isolated, centered, clean 6-digit odometer reading competes against a
        // 5-digit run crowded next to a "PM" suffix near the frame's top edge (clock/date-like).
        val odometerElement = RecognizedTextElement(
            text = "654321",
            lineText = "654321",
            left = 400,
            top = 480,
            right = 600,
            bottom = 520,
        )
        val noisyDigitElement = RecognizedTextElement(
            text = "11111",
            lineText = "11111 PM",
            left = 50,
            top = 10,
            right = 150,
            bottom = 40,
        )
        val pmSuffixElement = RecognizedTextElement(
            text = "PM",
            lineText = "11111 PM",
            left = 155,
            top = 10,
            right = 190,
            bottom = 40,
        )

        // Act
        val bestCandidate = OdometerCandidateScorer.findBestCandidate(
            allRecognizedElements = listOf(odometerElement, noisyDigitElement, pmSuffixElement),
            frameHeight = frameHeight,
        )

        // Assert
        assertEquals(654321.0, bestCandidate?.valueKm)
    }

    @Test
    fun `returns null when no element matches the odometer digit pattern`() {
        // Arrange: only non-digit-shaped text in the frame.
        val clockElement = RecognizedTextElement(
            text = "14:32",
            lineText = "14:32",
            left = 0,
            top = 0,
            right = 100,
            bottom = 40,
        )

        // Act
        val bestCandidate = OdometerCandidateScorer.findBestCandidate(
            allRecognizedElements = listOf(clockElement),
            frameHeight = frameHeight,
        )

        // Assert
        assertNull(bestCandidate)
    }

    @Test
    fun `a 5-digit candidate scores lower on digit-count than an otherwise identical 6-digit candidate`() {
        // Arrange: two isolated, centered, structurally pure candidates differing only in digit count.
        val sixDigitElement = RecognizedTextElement(
            text = "123456",
            lineText = "123456",
            left = 400,
            top = 480,
            right = 600,
            bottom = 520,
        )
        val fiveDigitElement = RecognizedTextElement(
            text = "12345",
            lineText = "12345",
            left = 400,
            top = 480,
            right = 600,
            bottom = 520,
        )

        // Act
        val sixDigitScore = OdometerCandidateScorer.findBestCandidate(listOf(sixDigitElement), frameHeight)?.confidencePercent
        val fiveDigitScore = OdometerCandidateScorer.findBestCandidate(listOf(fiveDigitElement), frameHeight)?.confidencePercent

        // Assert
        assertTrue(requireNotNull(sixDigitScore) > requireNotNull(fiveDigitScore))
    }
}
