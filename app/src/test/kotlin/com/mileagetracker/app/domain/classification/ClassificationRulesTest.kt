package com.mileagetracker.app.domain.classification

import com.mileagetracker.app.domain.model.TripClassification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationRulesTest {

    @Test
    fun `private trip with no business reason is satisfied`() {
        val isSatisfied = ClassificationRules.isBusinessReasonSatisfied(TripClassification.PRIVATE, businessReason = null)
        assertTrue(isSatisfied)
    }

    @Test
    fun `work trip with blank business reason is not satisfied`() {
        val isSatisfied = ClassificationRules.isBusinessReasonSatisfied(TripClassification.WORK, businessReason = "   ")
        assertFalse(isSatisfied)
    }

    @Test
    fun `work trip with non-blank business reason is satisfied`() {
        val isSatisfied = ClassificationRules.isBusinessReasonSatisfied(TripClassification.WORK, businessReason = "Client visit")
        assertTrue(isSatisfied)
    }

    @Test
    fun `validateBusinessReason returns Invalid for null reason`() {
        val result = ClassificationRules.validateBusinessReason(null)
        assertTrue(result is ClassificationRules.ValidationResult.Invalid)
    }

    @Test
    fun `validateBusinessReason returns Valid for non-blank reason`() {
        val result = ClassificationRules.validateBusinessReason("Client visit")
        assertTrue(result is ClassificationRules.ValidationResult.Valid)
    }
}
