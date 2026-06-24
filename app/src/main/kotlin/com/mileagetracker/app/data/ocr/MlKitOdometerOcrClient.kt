package com.mileagetracker.app.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mileagetracker.app.domain.ocr.OdometerCandidateScorer
import com.mileagetracker.app.domain.ocr.OdometerCandidateScorer.RecognizedTextElement
import com.mileagetracker.app.domain.ocr.OdometerOcrClient
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.domain.ocr.OdometerSanityCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * ML Kit-backed [OdometerOcrClient]. Real confidence scoring as of T-005.3/T-005.4:
 * [OdometerCandidateScorer] (a plain-JVM, unit-testable domain class) does the actual scoring —
 * this class's only job is the thin adapter layer: ML Kit's `Text` tree (`TextBlock` -> `Line` ->
 * `Element`, each with a real `android.graphics.Rect`) gets flattened into plain
 * [RecognizedTextElement]s with plain `Int` box edges, because `Rect` is not safely usable in this
 * project's plain JVM unit tests (see [OdometerCandidateScorer]'s class doc for the experimental
 * proof) — but it IS safe here, in production code running on a real device/emulator, which is
 * exactly why the Rect-touching code is confined to this one adapter method.
 *
 * Locked threshold from project facts: confidence >= 80% -> [OdometerOcrResult.Confident],
 * otherwise [OdometerOcrResult.LowConfidence] (if any digit run was found) or
 * [OdometerOcrResult.NoTextFound].
 */
class MlKitOdometerOcrClient @Inject constructor() : OdometerOcrClient {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
    override suspend fun recognizeText(odometerPhoto: Bitmap?, startOdometerKm: Double): OdometerOcrResult {
        if (odometerPhoto == null) {
            Timber.tag("MT-OCR").e("recognizeText: odometerPhoto is null; returning NoTextFound")
            return OdometerOcrResult.NoTextFound
        }
        return try {
            val inputImage = InputImage.fromBitmap(odometerPhoto, /* rotationDegrees = */ 0)
            val recognizedText = textRecognizer.process(inputImage).await()

            val allRecognizedElements = flattenToRecognizedTextElements(recognizedText)
            val bestCandidate = OdometerCandidateScorer.findBestCandidate(allRecognizedElements, odometerPhoto.height)
                ?: return OdometerOcrResult.NoTextFound

            val ocrResult = OdometerSanityCheck.toOcrResult(bestCandidate, startOdometerKm)
            if (ocrResult is OdometerOcrResult.LowConfidence && bestCandidate.confidencePercent >= OdometerCandidateScorer.CONFIDENT_THRESHOLD_PERCENT) {
                Timber.tag("MT-OCR").w(
                    "OCR candidate %.1f rejected: below trip startOdometerKm=%.1f despite confidencePercent=%d; falling back to manual entry",
                    bestCandidate.valueKm,
                    startOdometerKm,
                    bestCandidate.confidencePercent,
                )
            }
            ocrResult
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (ocrFailure: Exception) {
            Timber.tag("MT-OCR").e(ocrFailure, "ML Kit text recognition failed; falling back to NoTextFound")
            OdometerOcrResult.NoTextFound
        }
    }

    /**
     * Flattens ML Kit's `TextBlock -> Line -> Element` tree into one list of
     * [RecognizedTextElement]s, converting each `Element`'s real `android.graphics.Rect` bounding
     * box into plain `Int` edges. Each element carries its parent [Text.Line]'s full text as
     * [RecognizedTextElement.lineText] — structural-purity scoring needs the *line* (e.g. a clock
     * display's `"14:32"` sibling element on the same line as a digit run), not just the matched
     * element's own digits.
     */
    private fun flattenToRecognizedTextElements(recognizedText: Text): List<RecognizedTextElement> {
        return recognizedText.textBlocks.flatMap { textBlock ->
            textBlock.lines.flatMap { line ->
                line.elements.map { element ->
                    val boundingBox = element.boundingBox
                    RecognizedTextElement(
                        text = element.text,
                        lineText = line.text,
                        left = boundingBox?.left ?: 0,
                        top = boundingBox?.top ?: 0,
                        right = boundingBox?.right ?: 0,
                        bottom = boundingBox?.bottom ?: 0,
                    )
                }
            }
        }
    }
}
