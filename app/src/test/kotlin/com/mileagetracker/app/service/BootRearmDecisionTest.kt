package com.mileagetracker.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-035 tests for [BootRearmDecision] — pure decision logic, no Android framework dependency, so
 * this runs as a plain JVM unit test with no Robolectric/instrumentation needed. Consistent with
 * this project's "hand-written fakes only, no mocking framework" convention (mirrors
 * `SetupPermissionsPlannerTest`).
 */
class BootRearmDecisionTest {

    @Test
    fun `shouldStartDetectionService returns true when fine location is granted`() {
        val result = BootRearmDecision.shouldStartDetectionService(isFineLocationGranted = true)

        assertTrue(result)
    }

    @Test
    fun `shouldStartDetectionService returns false when fine location is not granted`() {
        val result = BootRearmDecision.shouldStartDetectionService(isFineLocationGranted = false)

        assertFalse(result)
    }
}
