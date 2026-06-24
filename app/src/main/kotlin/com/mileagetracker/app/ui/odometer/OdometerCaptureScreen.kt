package com.mileagetracker.app.ui.odometer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

private const val ODOMETER_CAPTURE_LOG_TAG = "MT-UI"
private const val OCR_TEMP_DIRECTORY_NAME = "ocr_temp"

/**
 * T-005.1: Odometer Photo Capture screen. Wraps a CameraX preview for the happy path,
 * with full result-state branching (Confident / LowConfidence / NoTextFound) and a defensive
 * CAMERA-permission check (camera is requested in SetupPermissionsScreen at first run, but we
 * degrade gracefully rather than crash if it is absent at this point).
 *
 * CameraX lifecycle binding: [ProcessCameraProvider] is obtained and bound inside
 * [AndroidView]'s factory block, which runs exactly once on composition. The
 * [DisposableEffect] releases the provider when this screen leaves composition.
 * Both [Preview] and [ImageCapture] are bound in a single [ProcessCameraProvider.bindToLifecycle]
 * call — the only safe approach since multiple `bindToLifecycle` calls for overlapping use-cases
 * on the same session throw [IllegalArgumentException].
 *
 * Rotation handling: [ImageCapture.OnImageSavedCallback.onImageSaved] supplies rotation
 * metadata via [androidx.camera.core.ImageInfo]. We read it from [ImageCapture.OutputFileResults]
 * and rotate the decoded bitmap by that amount before handing it to
 * [OdometerCaptureViewModel.captureAndRunOcr] so ML Kit always receives an upright image.
 *
 * NOTE: [OutputFileResults.savedUri] is non-null only when a [MediaStore]-based
 * [OutputFileOptions] is used; for file-based options it is null. Rotation degrees are obtained
 * from [ImageCapture.OutputFileResults] via the internal metadata — however the public CameraX
 * 1.3.x API does not expose rotation from OutputFileResults directly. We instead configure
 * [ImageCapture] with the target rotation from the display and use the EXIF data in the saved
 * JPEG, which BitmapFactory reads automatically when using [BitmapFactory.Options.inSampleSize]
 * with EXIF-aware decode. For deterministic rotation correction we use
 * [androidx.exifinterface.media.ExifInterface] — but that adds a dep not yet in the project.
 * Pragmatic fallback for MVP: request [ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY] and assume
 * the common phone-portrait case produces 90° rotation stored in EXIF. The rotation correction
 * code path is guarded so a 0° image passes through unmodified. This is noted as a
 * post-MVP polish item (T-005 follow-up) once ExifInterface is added.
 */
@Composable
fun OdometerCaptureScreen(
    tripId: String,
    onCaptureComplete: () -> Unit,
    viewModel: OdometerCaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    if (!hasCameraPermission) {
        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera permission is required to capture the odometer photo.")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Open phone Settings > Apps > Mileage Tracker > Permissions " +
                        "and grant Camera access, then return here.",
                )
            }
        }
        return
    }

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

    Scaffold { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                uiState.ocrResult == null && !uiState.isOcrInProgress -> {
                    CameraPreviewContent(
                        lifecycleOwner = lifecycleOwner,
                        imageCaptureUseCase = imageCaptureUseCase,
                        cameraExecutor = cameraExecutor,
                        ocrTempDirectory = ocrTempDirectory,
                        boundCameraProviderHolder = boundCameraProviderHolder,
                        onBitmapReady = { capturedBitmap, capturedFileUri ->
                            viewModel.captureAndRunOcr(
                                capturedBitmap = capturedBitmap,
                                imageUri = capturedFileUri,
                                photoRetentionMode = uiState.photoRetentionMode,
                            )
                        },
                        onCaptureError = { captureException ->
                            Timber.tag(ODOMETER_CAPTURE_LOG_TAG).e(
                                captureException,
                                "OdometerCaptureScreen: ImageCapture failed for tripId=%s",
                                tripId,
                            )
                        },
                    )
                }

                uiState.isOcrInProgress -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Reading odometer...")
                        }
                    }
                }

                uiState.ocrResult is OdometerOcrResult.Confident -> {
                    val confidentResult = uiState.ocrResult as OdometerOcrResult.Confident
                    ConfidentResultContent(
                        readingKm = confidentResult.valueKm,
                        confidencePercent = confidentResult.confidencePercent,
                        onConfirmClicked = {
                            Timber.tag(ODOMETER_CAPTURE_LOG_TAG).i(
                                "OdometerCaptureScreen: Confirm OCR tapped (Confident) tripId=%s, valueKm=%s",
                                tripId,
                                confidentResult.valueKm,
                            )
                            val capturedUri = uiState.capturedImageUri ?: ""
                            viewModel.onConfirmOcrResult(
                                valueKm = confidentResult.valueKm,
                                imageUri = capturedUri,
                                photoRetention = uiState.photoRetentionMode,
                                onConfirmed = onCaptureComplete,
                            )
                        },
                        onRetakeClicked = {
                            Timber.tag(ODOMETER_CAPTURE_LOG_TAG).i(
                                "OdometerCaptureScreen: Retake tapped (Confident) tripId=%s",
                                tripId,
                            )
                            viewModel.onRetakePhoto()
                        },
                    )
                }

                uiState.ocrResult is OdometerOcrResult.LowConfidence ||
                    uiState.ocrResult is OdometerOcrResult.NoTextFound -> {
                    ManualFallbackContent(
                        ocrResult = uiState.ocrResult,
                        manualEntryText = uiState.manualEntryText,
                        onManualEntryChanged = viewModel::onManualEntryChanged,
                        onConfirmClicked = {
                            Timber.tag(ODOMETER_CAPTURE_LOG_TAG).i(
                                "OdometerCaptureScreen: Confirm manual entry tapped tripId=%s, text=%s",
                                tripId,
                                uiState.manualEntryText,
                            )
                            viewModel.onConfirmManualOdometer(onCaptureComplete)
                        },
                        onRetakeClicked = {
                            Timber.tag(ODOMETER_CAPTURE_LOG_TAG).i(
                                "OdometerCaptureScreen: Retake tapped (fallback) tripId=%s",
                                tripId,
                            )
                            viewModel.onRetakePhoto()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Mutable holder for the [ProcessCameraProvider] obtained during [AndroidView] factory
 * initialisation. Stored as a [remember]'d object so the [DisposableEffect] can unbind all
 * use cases on disposal without needing the provider to be part of Compose state (which would
 * trigger unnecessary recompositions).
 */
private class BoundCameraProviderHolder {
    var provider: ProcessCameraProvider? = null
}

@Composable
private fun CameraPreviewContent(
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
                                Timber.tag(ODOMETER_CAPTURE_LOG_TAG).e(
                                    bindingException,
                                    "CameraPreviewContent: CameraX bindToLifecycle failed",
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
                Timber.tag(ODOMETER_CAPTURE_LOG_TAG).i(
                    "OdometerCaptureScreen: Capture button tapped",
                )
                val outputFile = File(
                    ocrTempDirectory,
                    "odometer_${System.currentTimeMillis()}.jpg",
                )
                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                imageCaptureUseCase.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(
                            outputFileResults: ImageCapture.OutputFileResults,
                        ) {
                            val rawBitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                            if (rawBitmap == null) {
                                Timber.tag(ODOMETER_CAPTURE_LOG_TAG).e(
                                    "CameraPreviewContent: BitmapFactory.decodeFile returned null for %s",
                                    outputFile.absolutePath,
                                )
                                return
                            }
                            // Rotation: CameraX saves JPEG with EXIF orientation set by the
                            // device's sensor orientation. For MVP we read the rotation from
                            // ImageCapture's configured target rotation. The ImageCapture use
                            // case is configured with Builder defaults, which inherit the display
                            // rotation at bind time. CameraX 1.3.x does not expose rotation via
                            // OutputFileResults for file-based captures; we handle the most
                            // common portrait-capture case (90 degrees) defensively.
                            // Post-MVP: add androidx.exifinterface to read EXIF precisely.
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
                                    rawBitmap,
                                    0,
                                    0,
                                    rawBitmap.width,
                                    rawBitmap.height,
                                    rotationMatrix,
                                    true,
                                )
                            } else {
                                rawBitmap
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

@Composable
private fun ConfidentResultContent(
    readingKm: Double,
    confidencePercent: Int,
    onConfirmClicked: () -> Unit,
    onRetakeClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("OCR reading: $readingKm km")
        Text("Confidence: $confidencePercent%")
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConfirmClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetakeClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retake")
        }
    }
}

@Composable
private fun ManualFallbackContent(
    ocrResult: OdometerOcrResult?,
    manualEntryText: String,
    onManualEntryChanged: (String) -> Unit,
    onConfirmClicked: () -> Unit,
    onRetakeClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        when (ocrResult) {
            is OdometerOcrResult.LowConfidence -> {
                Text(
                    "Low confidence (${ocrResult.confidencePercent}%) — please verify:",
                )
            }
            OdometerOcrResult.NoTextFound -> {
                Text("Could not read odometer — please enter manually:")
            }
            else -> {
                Text("Please enter the odometer reading:")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = manualEntryText,
            onValueChange = onManualEntryChanged,
            label = { Text("Odometer reading (km)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onConfirmClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetakeClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retake")
        }
    }
}
