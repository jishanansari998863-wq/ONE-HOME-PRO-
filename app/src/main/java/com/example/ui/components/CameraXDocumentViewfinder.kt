package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.document.DocumentCategoryOption
import com.example.ui.theme.ArchitecturalGold
import com.example.ui.theme.PureWhite
import com.example.ui.theme.QatarNavy
import java.io.File
import kotlin.math.min

@Composable
fun CameraXDocumentViewfinderDialog(
    category: DocumentCategoryOption,
    isScanMode: Boolean,
    onCapturedUri: (Uri) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraUnavailableMsg by remember { mutableStateOf<String?>(null) }
    var isTakingPicture by remember { mutableStateOf(false) }

    var cameraControl by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var pendingSystemUri by remember { mutableStateOf<Uri?>(null) }

    val systemCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingSystemUri
        if (success && uri != null) {
            onCapturedUri(uri)
        } else if (!success) {
            onError("Camera capture cancelled")
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = runCatching { ProcessCameraProvider.getInstance(context) }.getOrElse {
            cameraUnavailableMsg = it.message ?: "Camera provider unavailable"
            return@LaunchedEffect
        }
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(selector)) {
                    cameraUnavailableMsg = "Requested camera lens not detected on this device. Use System Camera fallback below."
                    return@runCatching
                }
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                provider.unbindAll()
                val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                cameraControl = cam
                imageCapture = capture
                cameraUnavailableMsg = null
            }.onFailure { err ->
                cameraUnavailableMsg = err.message ?: "Camera initialization failed. Use System Camera fallback."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    LaunchedEffect(torchEnabled, cameraControl) {
        cameraControl?.cameraControl?.enableTorch(torchEnabled)
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("camerax_viewfinder_dialog"),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            if (cameraUnavailableMsg == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = ArchitecturalGold, modifier = Modifier.size(48.dp))
                    Text(
                        text = cameraUnavailableMsg ?: "Camera unavailable",
                        color = PureWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val uri = createTempCameraImageUri(context)
                            pendingSystemUri = uri
                            systemCameraLauncher.launch(uri)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("system_camera_fallback_btn")
                    ) {
                        Text("Open System Camera App")
                    }
                }
            }

            // Document alignment frame guide when Scan Document mode is active
            if (isScanMode && cameraUnavailableMsg == null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxW = size.width * 0.86f
                    val maxH = size.height * 0.62f
                    val aspect = category.frameAspectRatio
                    val frameW = min(maxW, maxH * aspect)
                    val frameH = frameW / aspect
                    val left = (size.width - frameW) / 2f
                    val top = (size.height - frameH) / 2f
                    drawRoundRect(
                        color = ArchitecturalGold,
                        topLeft = Offset(left, top),
                        size = Size(frameW, frameH),
                        cornerRadius = CornerRadius(24f, 24f),
                        style = Stroke(width = 5f)
                    )
                }
            }

            // Top controls bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("camera_close_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Close camera", tint = PureWhite)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isScanMode) "Scan ${category.label}" else "Photo Capture",
                        color = PureWhite,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanMode) {
                        Text(
                            text = "Align edges inside gold frame",
                            color = ArchitecturalGold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(
                            if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle flash",
                            tint = if (torchEnabled) ArchitecturalGold else PureWhite
                        )
                    }
                    IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = PureWhite)
                    }
                }
            }

            // Bottom capture bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = {
                        val uri = createTempCameraImageUri(context)
                        pendingSystemUri = uri
                        systemCameraLauncher.launch(uri)
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("System Cam", color = PureWhite)
                }

                FloatingActionButton(
                    onClick = {
                        val capture = imageCapture
                        if (capture == null) {
                            val uri = createTempCameraImageUri(context)
                            pendingSystemUri = uri
                            systemCameraLauncher.launch(uri)
                            return@FloatingActionButton
                        }
                        if (isTakingPicture) return@FloatingActionButton
                        isTakingPicture = true
                        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
                        val outFile = File.createTempFile("scan_${System.currentTimeMillis()}_", ".jpg", dir)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isTakingPicture = false
                                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(outFile)
                                    onCapturedUri(savedUri)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isTakingPicture = false
                                    onError("Capture failed: ${exception.message}")
                                }
                            }
                        )
                    },
                    shape = CircleShape,
                    containerColor = ArchitecturalGold,
                    contentColor = QatarNavy,
                    modifier = Modifier.size(68.dp).testTag("camera_shutter_btn")
                ) {
                    if (isTakingPicture) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = QatarNavy, strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture photo", modifier = Modifier.size(30.dp))
                    }
                }

                Spacer(Modifier.width(72.dp))
            }
        }
    }
}
