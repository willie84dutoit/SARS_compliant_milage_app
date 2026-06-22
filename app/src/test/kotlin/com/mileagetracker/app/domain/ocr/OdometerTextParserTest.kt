package com.mileagetracker.app.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OdometerTextParserTest {

    @Test
    fun `extracts a 6-digit odometer reading from surrounding text`() {
        val candidate = OdometerTextParser.extractCandidateOdometerKm("ODO 123456 km")
        assertEquals(123456.0, candidate)
    }

    @Test
    fun `extracts a 5-digit odometer reading`() {
        val candidate = OdometerTextParser.extractCandidateOdometerKm("12345")
        assertEquals(12345.0, candidate)
    }

    @Test
    fun `returns null when no digit run matches the expected length`() {
        val candidate = OdometerTextParser.extractCandidateOdometerKm("no digits here")
        assertNull(candidate)
    }

    @Test
    fun `returns null for digit runs shorter than 5 digits`() {
        val candidate = OdometerTextParser.extractCandidateOdometerKm("1234")
        assertNull(candidate)
    }
}
