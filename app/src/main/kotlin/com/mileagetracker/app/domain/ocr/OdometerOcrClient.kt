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
    suspend fun recognizeText(odometerPhoto: Bitmap): OdometerOcrResult
}
