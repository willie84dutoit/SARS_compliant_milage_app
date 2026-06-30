package com.mileagetracker.app.ui.classification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

private const val CLASSIFICATION_SCREEN_LOG_TAG = "MT-UI"
private const val OCR_TEMP_DIRECTORY_NAME = "ocr_temp"
private const val MAX_ODOMETER_BITMAP_LONG_EDGE_PX = 2048

/**
 * Full-screen camera preview overlay. Composed only while
 * [TripClassificationUiState.isCameraPreviewVisible] is true. CameraX [ImageCapture], executor,
 * and [BoundCameraProviderHolder] are [remember]'d here — scoped to this composable's lifetime —
 * so [DisposableEffect] releases them when the overlay is removed from composition (spec risk
 * mitigation: do NOT hoist these remembers to the screen root).
 *
 * Rotation handling: identical to the former OdometerCaptureScreen. Post-MVP: add
 * androidx.exifinterface for precise EXIF-based rotation.
 */
@Composable
fun InlineCameraOverlay(
    tripId: String,
    onBitmapReady: (Bitmap, String) -> Unit,
    onDismiss: () -> Unit,
    // T-031 N-3: caller supplies the error handler so the ViewModel (which holds error state)
    // is reached without threading it through an intermediate layer.
    onCaptureOrDecodeError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCaptureUseCase = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val boundCameraProviderHolder = remember { BoundCameraProviderHolder() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            boundCameraProviderHolder.provider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    val ocrTempDirectory = remember(context) {
        File(context.filesDir, OCR_TEMP_DIRECTORY_NAME).also { it.mkdirs() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreviewWithCapture(
            lifecycleOwner = lifecycleOwner,
            imageCaptureUseCase = imageCaptureUseCase,
            cameraExecutor = cameraExecutor,
            ocrTempDirectory = ocrTempDirectory,
            boundCameraProviderHolder = boundCameraProviderHolder,
            onBitmapReady = onBitmapReady,
            onCaptureError = { captureException ->
                // T-031 N-3: log and propagate so the ViewModel can close the overlay
                // and show the user visible error feedback.
                Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).e(
                    captureException,
                    "InlineCameraOverlay: ImageCapture/decode failed for tripId=%s",
                    tripId,
                )
                onCaptureOrDecodeError(
                    captureException.message ?: "Camera capture failed",
                )
            },
        )

        // Dismiss button top-right.
        IconButton(
            onClick = {
                Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).i(
                    "InlineCameraOverlay: Cancel tapped tripId=%s",
                    tripId,
                )
                onDismiss()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel camera",
                tint = Color.White,
            )
        }
    }
}

/**
 * Mutable holder for the [ProcessCameraProvider]. Stored as a [remember]'d object so
 * [DisposableEffect] can unbind without putting the provider into Compose state.
 */
private class BoundCameraProviderHolder {
    var provider: ProcessCameraProvider? = null
}

@Composable
private fun CameraPreviewWithCapture(
    lifecycleOwner: LifecycleOwner,
    imageCaptureUseCase: ImageCapture,
    cameraExecutor: java.util.concurrent.ExecutorService,
    ocrTempDirectory: File,
    boundCameraProviderHolder: BoundCameraProviderHolder,
    onBitmapReady: (Bitmap, String) -> Unit,
    onCaptureError: (ImageCaptureException) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { factoryContext ->
                PreviewView(factoryContext).also { previewView ->
                    previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(factoryContext)
                    cameraProviderFuture.addListener(
                        {
                            val cameraProvider = cameraProviderFuture.get()
                            boundCameraProviderHolder.provider = cameraProvider
                            val previewUseCase = Preview.Builder().build().also { preview ->
                                preview.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    previewUseCase,
                                    imageCaptureUseCase,
                                )
                            } catch (bindingException: Exception) {
                                Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).e(
                                    bindingException,
                                    "CameraPreviewWithCapture: bindToLifecycle failed",
                                )
                            }
                        },
                        ContextCompat.getMainExecutor(factoryContext),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Button(
            onClick = {
                Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).i("CameraPreviewWithCapture: Capture tapped")
                val outputFile = File(ocrTempDirectory, "odometer_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                imageCaptureUseCase.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            // T-031 N-2: Downsample on decode to avoid holding a full-res
                            // bitmap (≈48 MB ARGB_8888 for 12 MP) in Compose state when it
                            // is only ever displayed as a small thumbnail.
                            //
                            // Resolution contract:
                            //   Long edge is capped at MAX_ODOMETER_BITMAP_LONG_EDGE_PX (2048).
                            //   This is sufficient for ML Kit text recognition (which does not
                            //   need full resolution) and eliminates the ~2× peak during
                            //   rotation that caused OOM on low-RAM devices.
                            //   The downsampled bitmap is fed to both the thumbnail AND OCR —
                            //   no separate full-res decode is performed.
                            val boundsOnlyOptions = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(outputFile.absolutePath, boundsOnlyOptions)
                            val rawImageWidth = boundsOnlyOptions.outWidth
                            val rawImageHeight = boundsOnlyOptions.outHeight

                            val longEdgePixels = maxOf(rawImageWidth, rawImageHeight)
                            val computedSampleSize = if (longEdgePixels > MAX_ODOMETER_BITMAP_LONG_EDGE_PX) {
                                var sampleSize = 1
                                while (longEdgePixels / (sampleSize * 2) > MAX_ODOMETER_BITMAP_LONG_EDGE_PX) {
                                    sampleSize *= 2
                                }
                                sampleSize * 2
                            } else {
                                1
                            }

                            val downsampledOptions = BitmapFactory.Options().apply {
                                inSampleSize = computedSampleSize
                            }
                            val downsampledBitmap = BitmapFactory.decodeFile(
                                outputFile.absolutePath,
                                downsampledOptions,
                            )
                            if (downsampledBitmap == null) {
                                Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).e(
                                    "CameraPreviewWithCapture: BitmapFactory returned null for %s — notifying caller",
                                    outputFile.absolutePath,
                                )
                                onCaptureError(
                                    ImageCaptureException(
                                        ImageCapture.ERROR_UNKNOWN,
                                        "Bitmap decode returned null for ${outputFile.absolutePath}",
                                        null,
                                    ),
                                )
                                return
                            }

                            val capturedRotationDegrees = imageCaptureUseCase.targetRotation.let {
                                when (it) {
                                    android.view.Surface.ROTATION_0 -> 90
                                    android.view.Surface.ROTATION_90 -> 0
                                    android.view.Surface.ROTATION_180 -> 270
                                    android.view.Surface.ROTATION_270 -> 180
                                    else -> 0
                                }
                            }
                            val uprightBitmap = if (capturedRotationDegrees != 0) {
                                val rotationMatrix = Matrix().apply {
                                    postRotate(capturedRotationDegrees.toFloat())
                                }
                                Bitmap.createBitmap(
                                    downsampledBitmap, 0, 0,
                                    downsampledBitmap.width, downsampledBitmap.height,
                                    rotationMatrix, true,
                                )
                            } else {
                                downsampledBitmap
                            }
                            onBitmapReady(uprightBitmap, outputFile.toURI().toString())
                        }

                        override fun onError(captureException: ImageCaptureException) {
                            onCaptureError(captureException)
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text("Capture")
        }
    }
}
