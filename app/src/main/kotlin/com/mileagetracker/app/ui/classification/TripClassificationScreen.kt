package com.mileagetracker.app.ui.classification

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import com.mileagetracker.app.domain.model.TripClassification
import com.mileagetracker.app.domain.ocr.OdometerOcrResult
import com.mileagetracker.app.ui.common.MileageTrackerScaffold
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

private const val CLASSIFICATION_SCREEN_LOG_TAG = "MT-UI"
private const val OCR_TEMP_DIRECTORY_NAME = "ocr_temp"

// T-031 N-2: cap the long edge of the decoded odometer bitmap at this value to stay well under
// the ~48 MB budget a 12 MP full-res ARGB_8888 image would consume. 2048 px is sufficient
// for ML Kit text recognition and eliminates the 2× peak during rotation.
private const val MAX_ODOMETER_BITMAP_LONG_EDGE_PX = 2048

/**
 * Unified trip-completion screen: classification + odometer photo + reading in one place,
 * one Save. Replaces the former two-screen flow (TripClassificationScreen → OdometerCaptureScreen).
 *
 * T-024 Cancel contract (preserved unchanged):
 * - [onCancelClassification] must be implemented by the nav host as `navController.popBackStack()`
 *   NOT navigate(HomeStatus). This returns to the existing Home back-stack entry whose T-022
 *   autoRoutedToClassificationTripId gate is already set, preventing re-fire.
 *
 * Camera permission (geo-sensors refinement #8):
 * - This screen owns its own `rememberLauncherForActivityResult(RequestPermission())` for CAMERA.
 * - On tap of "Take odometer photo": check current permission; if denied, launch the request.
 * - On grant: open the inline camera overlay.
 * - On denial: grey the button + show helper text; editable reading field stays fully usable.
 * - The button is ALWAYS visible (greyed when denied) — it is never hidden (geo-sensors #7).
 *
 * Camera overlay (CameraX):
 * - Implemented as a full-screen Box conditional on [TripClassificationUiState.isCameraPreviewVisible].
 * - [ImageCapture], executor, and [BoundCameraProviderHolder] are remembered INSIDE the overlay
 *   composable so DisposableEffect correctly releases camera resources when the overlay is dismissed.
 *
 * Odometer reading field:
 * - UNCONDITIONALLY visible regardless of camera/photo state (geo-sensors refinement #6).
 * - Pre-filled from OCR; user may correct.
 * - Field and OCR context label hidden while [isOcrInProgress] is true to avoid flicker.
 */
@Composable
fun TripClassificationScreen(
    tripId: String,
    onClassificationSaved: () -> Unit,
    onCancelClassification: () -> Unit,
    viewModel: TripClassificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // geo-sensors refinement #8: own camera permission request; do not assume Setup granted it.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.onTakeOdometerPhotoClicked()
        } else {
            Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).i(
                "TripClassificationScreen: Camera permission denied tripId=%s — field still usable for manual entry",
                tripId,
            )
        }
    }

    MileageTrackerScaffold(screenTitle = "Classify Trip") { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                // ---- Section 1: Classification ----
                Text("Was this trip Work or Private?")
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    val workIsSelected = uiState.selectedClassification == TripClassification.WORK
                    val privateIsSelected = uiState.selectedClassification == TripClassification.PRIVATE

                    Button(
                        onClick = { viewModel.onClassificationSelected(TripClassification.WORK) },
                        modifier = Modifier.weight(1f),
                        colors = if (workIsSelected) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Text(if (workIsSelected) "Work (selected)" else "Work")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { viewModel.onClassificationSelected(TripClassification.PRIVATE) },
                        modifier = Modifier.weight(1f),
                        colors = if (privateIsSelected) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        Text(if (privateIsSelected) "Private (selected)" else "Private")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.selectedClassification == TripClassification.WORK) {
                    // H-4 fix: label and helper text convey SARS expectations.
                    OutlinedTextField(
                        value = uiState.businessReasonText,
                        onValueChange = viewModel::onBusinessReasonChanged,
                        label = { Text("Business purpose, destination & client") },
                        supportingText = { Text("e.g. Client visit — Acme Corp, Sandton | Delivery — Warehouse, Cape Town") },
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                uiState.validationErrorMessage?.let { validationErrorText ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(validationErrorText, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- Section 2: Odometer ----
                Text("Odometer reading", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))

                // Thumbnail (shown once a photo has been taken and OCR is not running).
                val snapshotBitmap = uiState.capturedBitmap
                if (snapshotBitmap != null && !uiState.isOcrInProgress) {
                    Image(
                        bitmap = snapshotBitmap.asImageBitmap(),
                        contentDescription = "Odometer photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.onRetakeOdometerPhoto() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retake photo")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // OCR in-progress indicator (shown while OCR is running).
                if (uiState.isOcrInProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Reading odometer...")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // OCR context label (shown after OCR completes).
                if (!uiState.isOcrInProgress && uiState.ocrResult != null) {
                    val ocrContextText = when (val ocrResultSnapshot = uiState.ocrResult) {
                        is OdometerOcrResult.Confident ->
                            "OCR reading: ${ocrResultSnapshot.valueKm} km (${ocrResultSnapshot.confidencePercent}% confidence)"
                        is OdometerOcrResult.LowConfidence ->
                            "Low confidence (${ocrResultSnapshot.confidencePercent}%) — please verify:"
                        OdometerOcrResult.NoTextFound ->
                            "Could not read odometer — enter reading manually:"
                        null -> null
                    }
                    ocrContextText?.let { contextLabel ->
                        Text(contextLabel, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Editable reading field — UNCONDITIONALLY visible (geo-sensors refinement #6).
                // Hidden only while OCR is in progress to avoid empty→prefilled flicker.
                if (!uiState.isOcrInProgress) {
                    OutlinedTextField(
                        value = uiState.odometerReadingText,
                        onValueChange = viewModel::onOdometerReadingChanged,
                        label = { Text("Odometer reading (km)") },
                        supportingText = { Text("Leave blank if unknown") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.odometerReadingValidationError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    uiState.odometerReadingValidationError?.let { readingError ->
                        Text(readingError, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // "Take odometer photo" button — always visible, greyed when camera denied.
                // Hidden once a photo is being reviewed (capturedBitmap non-null and not in OCR).
                if (uiState.capturedBitmap == null && !uiState.isOcrInProgress) {
                    Button(
                        onClick = {
                            Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).i(
                                "TripClassificationScreen: Take odometer photo tapped tripId=%s hasCameraPermission=%s",
                                tripId,
                                hasCameraPermission,
                            )
                            if (hasCameraPermission) {
                                viewModel.onTakeOdometerPhotoClicked()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = true, // always enabled; greyed only when camera is denied (below)
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (!hasCameraPermission) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Text("Take odometer photo")
                    }
                    // geo-sensors refinement #7: helper shown when camera is denied.
                    if (!hasCameraPermission) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Camera unavailable — enter reading manually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Save error (from try/catch in ViewModel).
                uiState.saveError?.let { saveErrorText ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(saveErrorText, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- Section 3: Save / Cancel ----
                Button(
                    onClick = { viewModel.onSaveClassification(onClassificationSaved) },
                    enabled = uiState.selectedClassification != null &&
                        !uiState.isSaving &&
                        !uiState.isOcrInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        Timber.tag(CLASSIFICATION_SCREEN_LOG_TAG).i(
                            "TripClassificationScreen: Cancel tapped tripId=%s",
                            tripId,
                        )
                        onCancelClassification()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }

            // Camera overlay — full-screen, shown on top of the scrollable content.
            // CameraX resources are scoped INSIDE this conditional block so DisposableEffect
            // fires correctly when the overlay is dismissed (spec risk mitigation).
            if (uiState.isCameraPreviewVisible) {
                InlineCameraOverlay(
                    tripId = tripId,
                    onBitmapReady = { capturedBitmap, capturedFileUri ->
                        viewModel.captureAndRunOcr(capturedBitmap, capturedFileUri)
                    },
                    onDismiss = { viewModel.onCameraOverlayDismissed() },
                    // T-031 N-3: route capture/decode errors to the ViewModel so the user
                    // sees visible feedback (error text + retake affordance stays available).
                    onCaptureOrDecodeError = { errorMessage ->
                        viewModel.onCaptureOrDecodeError(errorMessage)
                    },
                )
            }
        }
    }
}

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
private fun InlineCameraOverlay(
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
