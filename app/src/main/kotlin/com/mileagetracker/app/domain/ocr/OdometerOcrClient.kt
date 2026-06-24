package com.mileagetracker.app.domain.ocr

import android.graphics.Bitmap

/**
 * Domain-facing interface over the OCR engine. The concrete implementation
 * ([com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient]) wraps ML Kit's TextRecognizer;
 * ml-ocr-specialist owns the exact recognizer configuration (T-001 blueprint open question 2).
 *
 * NOTE: this interface takes android.graphics.Bitmap, which technically means "domain" is not
 * 100% Android-free for this one file. This is a deliberate, narrow exception — Bitmap is part
 * of the Android platform SDK (not a third-party framework like Room/Compose/CameraX) and
 * avoiding it here would mean inventing a parallel image-buffer abstraction for zero practical
 * gain in a single-app MVP. Documented so it isn't mistaken for an oversight later.
 */
interface OdometerOcrClient {
    /**
     * [startOdometerKm] is the trip's starting odometer reading, required for T-005.4's sanity
     * check: even a geometrically/structurally "confident" OCR read must be rejected — and fall
     * through to manual entry, never silently saved — if the parsed value is less than
     * [startOdometerKm] (an odometer cannot decrease over the course of a trip). This check is
     * deliberately inside the OCR client rather than left to callers: [OdometerOcrResult.Confident]
     * must remain an unconditional guarantee that every validation this domain owns has already
     * passed, so no caller of this interface ever needs to re-derive "but is this number
     * plausible" after receiving [OdometerOcrResult.Confident]. See [OdometerSanityCheck] for the
     * actual decision logic and the fuller comment on why this seam was chosen over checking in
     * the ViewModel instead; see [com.mileagetracker.app.data.ocr.MlKitOdometerOcrClient] for how
     * the implementation wires the ML Kit adapter into it.
     */
    /**
     * [odometerPhoto] is [Bitmap?] rather than [Bitmap] to avoid a plain-JVM-test construction
     * failure: Android's [android.graphics.Bitmap] static factories throw RuntimeException("Stub!")
     * in unit-test environments, so test helpers pass null as a "don't-care" value. Implementations
     * MUST treat a null bitmap as [OdometerOcrResult.NoTextFound] and MUST NOT crash.
     */
    suspend fun recognizeText(odometerPhoto: Bitmap?, startOdometerKm: Double): OdometerOcrResult
}
