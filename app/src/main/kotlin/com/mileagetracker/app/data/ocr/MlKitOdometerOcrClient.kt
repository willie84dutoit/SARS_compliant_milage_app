package com.mileagetracker.app.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.ocr.OdometerTextParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * ML Kit-backed [OdometerOcrClient]. T-001 scaffolding only: this is the minimum wiring needed
 * to compile against the domain interface. ml-ocr-specialist (T-005) owns tuning the confidence
 * threshold mapping, bounding-box filtering, and any preprocessing this client needs — see
 * blueprint open question 2.
 *
 * Locked threshold from project facts: confidence >= 80% -> [OdometerOcrResult.Confident],
 * otherwise [OdometerOcrResult.LowConfidence] (if any digit run was found) or
 * [OdometerOcrResult.NoTextFound].
 */
class MlKitOdometerOcrClient @Inject constructor() : OdometerOcrClient {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        private const val CONFIDENT_THRESHOLD_PERCENT = 80

        // T-001 scaffolding placeholder: ML Kit's Text block API does not expose a single
        // numeric confidence percentage the same way across versions. ml-ocr-specialist (T-005)
        // must replace this constant with the real confidence derivation.
        private const val PLACEHOLDER_CONFIDENCE_PERCENT = 0
    }

    /**
     * T-018 correctness-critical boundary: an OCR failure (ML Kit throwing, a corrupt bitmap, a
     * Play Services hiccup) must NEVER propagate out of this function. The odometer-capture flow
     * treats [OdometerOcrResult.NoTextFound] as "fall back to manual entry" — a thrown exception
     * here would instead leave the trip's UI stuck waiting on a coroutine that never completes,
     * trapping the trip in PENDING_OCR with no way for the user to proceed. [CancellationException]
     * is the one exception that must still propagate (structured-concurrency contract: cancelling
     * this call, e.g. the user backgrounding the capture screen, must not be swallowed as if OCR
     * "failed").
     */
    override suspend fun recognizeText(odometerPhoto: Bitmap): OdometerOcrResult {
        return try {
            val inputImage = InputImage.fromBitmap(odometerPhoto, /* rotationDegrees = */ 0)
            val recognizedText = textRecognizer.process(inputImage).await()

            val candidateOdometerKm = OdometerTextParser.extractCandidateOdometerKm(recognizedText.text)
                ?: return OdometerOcrResult.NoTextFound

            // PLACEHOLDER_CONFIDENCE_PERCENT forces every result to LowConfidence until T-005
            // wires up a real confidence score — this is deliberate: scaffolding must never
            // silently claim a confident OCR result it cannot actually justify.
            if (PLACEHOLDER_CONFIDENCE_PERCENT >= CONFIDENT_THRESHOLD_PERCENT) {
                OdometerOcrResult.Confident(candidateOdometerKm, PLACEHOLDER_CONFIDENCE_PERCENT)
            } else {
                OdometerOcrResult.LowConfidence(candidateOdometerKm, PLACEHOLDER_CONFIDENCE_PERCENT)
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (ocrFailure: Exception) {
            Timber.tag("MT-OCR").e(ocrFailure, "ML Kit text recognition failed; falling back to NoTextFound")
            OdometerOcrResult.NoTextFound
        }
    }
}
