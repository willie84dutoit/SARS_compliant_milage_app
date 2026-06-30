package com.mileagetracker.app.domain.export

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-032 Half A / Pass 2 sanity tests for [VerificationRecipeDocument]. These are not exhaustive
 * Markdown-parsing tests — they guard against silent drift between the recipe's stated encoding
 * pins and the actual encoding used by [IntegritySidecarGenerator] /
 * [com.mileagetracker.app.data.signing.TripSigner], since this document is read by a human (or a
 * third-party script) outside the app and a mismatch here would only surface as a confusing
 * verification failure with no stack trace to follow.
 */
class VerificationRecipeDocumentTest {

    @Test
    fun `content states all four required encoding pins`() {
        val content = VerificationRecipeDocument.content
        assertTrue("must mention SHA256withECDSA", content.contains("SHA256withECDSA"))
        assertTrue("must mention DER encoding", content.contains("DER"))
        assertTrue("must mention P-256 curve", content.contains("P-256"))
        assertTrue("must mention no-padding Base64", content.contains("no padding") || content.contains("no-padding"))
    }

    @Test
    fun `content includes both a Python and an openssl verification recipe`() {
        val content = VerificationRecipeDocument.content
        assertTrue("must include a Python cryptography example", content.contains("cryptography.hazmat"))
        assertTrue("must include an openssl CLI example", content.contains("openssl dgst"))
    }

    @Test
    fun `content states the key-lifecycle and chain-truncation limitations`() {
        val content = VerificationRecipeDocument.content
        assertTrue(
            "must state the key-lifecycle limitation",
            content.contains("uninstalled") || content.contains("Key lifecycle"),
        )
        assertTrue(
            "must state the end-of-chain truncation limitation",
            content.contains("truncation", ignoreCase = true),
        )
    }
}
