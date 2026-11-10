package com.example.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraManager
import android.media.MediaActionSound
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.GpsTelemetry
import com.example.data.model.StampConfig
import com.example.service.LocationCompassManager
import com.example.ui.GeoCameraViewModel
import com.example.ui.theme.FolderYellow
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MapPinRed
import com.example.ui.theme.SusampTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.abs

enum class ViewfinderMode(val label: String) {
    BACK_CAMERA("REAR"),
    FRONT_CAMERA("FRONT"),
    FIELD_SIMULATION("SCENE")
}

@SuppressLint("RestrictedApi")
@Composable
fun CameraScreen(
    viewModel: GeoCameraViewModel,
    telemetry: GpsTelemetry,
    stampConfig: StampConfig,
    isCapturing: Boolean,
    hasCameraPermission: Boolean = false,
    onRequestCameraPermission: () -> Unit = {},
    onNavigateToGallery: () -> Unit,
    onNavigateToStudio: () -> Unit,
    onNavigateToMapData: () -> Unit = {},
    photoCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isRunningOnPhysicalCamera = remember(context) {
        hasUsableCameraDevice(context) && !isEmulatorEnvironment()
    }

    var viewfinderMode by remember {
        mutableStateOf(
            if (hasCameraPermission && isRunningOnPhysicalCamera) {
                ViewfinderMode.BACK_CAMERA
            } else {
                ViewfinderMode.FIELD_SIMULATION
            }
        )
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var activeCameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var isStreamActive by remember { mutableStateOf(false) }

    var flashEnabled by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var mirrorEnabled by remember { mutableStateOf(false) }
    var gridVisible by remember { mutableStateOf(true) }
    var aspectRatioLabel by remember { mutableStateOf("4:3") }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var activeCountdown by remember { mutableIntStateOf(0) }
    var isVideoMode by remember { mutableStateOf(false) }

    var liveMapPreview by remember { mutableStateOf<Bitmap?>(null) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Bind CameraX lifecycle safely only when viewfinderMode, permission, or flash changes
    LaunchedEffect(viewfinderMode, hasCameraPermission, flashEnabled, lifecycleOwner) {
        if (viewfinderMode == ViewfinderMode.FIELD_SIMULATION || !hasCameraPermission) {
            try {
                activeCameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            imageCapture = null
            isStreamActive = false
            return@LaunchedEffect
        }

        isStreamActive = false
        val provider = getCameraProviderSuspend(context)
        if (provider == null) {
            imageCapture = null
            viewfinderMode = ViewfinderMode.FIELD_SIMULATION
            return@LaunchedEffect
        }
        activeCameraProvider = provider

        try {
            provider.unbindAll()

            val desiredSelector = if (viewfinderMode == ViewfinderMode.FRONT_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val resolvedSelector = when {
                provider.hasCamera(desiredSelector) -> desiredSelector
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> null
            }

            if (resolvedSelector == null) {
                provider.unbindAll()
                imageCapture = null
                viewfinderMode = ViewfinderMode.FIELD_SIMULATION
                return@LaunchedEffect
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF
                )
                .build()
            imageCapture = capture

            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                resolvedSelector,
                preview,
                capture
            )

            val streamObserver = Observer<PreviewView.StreamState> { streamState ->
                if (streamState == PreviewView.StreamState.STREAMING) {
                    isStreamActive = true
                }
            }
            previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

            val stateObserver = Observer<CameraState> { state ->
                if (state.error != null || state.type == CameraState.Type.PENDING_OPEN) {
                    try {
                        provider.unbindAll()
                    } catch (_: Exception) {
                    }
                    imageCapture = null
                    isStreamActive = false
                    if (viewfinderMode != ViewfinderMode.FIELD_SIMULATION) {
                        viewfinderMode = ViewfinderMode.FIELD_SIMULATION
                    }
                } else if (state.type == CameraState.Type.OPEN) {
                    isStreamActive = true
                }
            }
            camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)

            // Fast watchdog: unbind within 850ms if hardware stream doesn't open so Camera2CameraImpl
            // never enters its 10,000ms reopen retry loop.
            delay(850L)
            if (!isStreamActive && viewfinderMode != ViewfinderMode.FIELD_SIMULATION) {
                try {
                    provider.unbindAll()
                } catch (_: Exception) {
                }
                imageCapture = null
                viewfinderMode = ViewfinderMode.FIELD_SIMULATION
            }
        } catch (_: Exception) {
            try {
                provider.unbindAll()
            } catch (_: Exception) {
            }
            imageCapture = null
            isStreamActive = false
            viewfinderMode = ViewfinderMode.FIELD_SIMULATION
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                activeCameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(telemetry.latitude, telemetry.longitude, stampConfig.showMap, stampConfig.mapType) {
        if (stampConfig.showMap) {
            withContext(Dispatchers.IO) {
                liveMapPreview = viewModel.offlineMapManager.getMapSnapshot(
                    lat = telemetry.latitude,
                    lng = telemetry.longitude,
                    sizePx = 220,
                    mapType = stampConfig.mapType
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val useHardwarePreview = hasCameraPermission && viewfinderMode != ViewfinderMode.FIELD_SIMULATION

        // 1. LIVE VIEWFINDER AREA
        if (useHardwarePreview) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleX = if (mirrorEnabled) -1f else 1f, scaleY = 1f)
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_live_street_view),
                contentDescription = "Live Outdoor Viewfinder",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleX = if (mirrorEnabled) -1f else 1f, scaleY = 1f)
            )
        }

        // 2. CAMERA GRID & CENTER SPIRIT LEVEL OVERLAY (Susamp Infotech Style)
        if (gridVisible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val gridColor = Color(0x45FFFFFF)
                drawLine(gridColor, start = Offset(w / 3f, 0f), end = Offset(w / 3f, h), strokeWidth = 1f)
                drawLine(gridColor, start = Offset(2f * w / 3f, 0f), end = Offset(2f * w / 3f, h), strokeWidth = 1f)
                drawLine(gridColor, start = Offset(0f, h / 3f), end = Offset(w, h / 3f), strokeWidth = 1f)
                drawLine(gridColor, start = Offset(0f, 2f * h / 3f), end = Offset(w, 2f * h / 3f), strokeWidth = 1f)

                // Center Spirit Level Circle & Horizon Line
                val cx = w / 2f
                val cy = h * 0.38f
                drawCircle(
                    color = Color(0xAAFFFFFF),
                    radius = 26.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
                drawLine(
                    color = SusampTeal,
                    start = Offset(cx - 20.dp.toPx(), cy),
                    end = Offset(cx + 20.dp.toPx(), cy),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy - 10.dp.toPx()),
                    end = Offset(cx, cy + 10.dp.toPx()),
                    strokeWidth = 1.5f
                )
            }
        }

        // Countdown Overlay when timer > 0
        if (activeCountdown > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$activeCountdown",
                    color = Color.White,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. TOP BLACK CAMERA CONTROL BAR (Exact Susamp Infotech 7-Icon Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Flash
            IconButton(
                onClick = { flashEnabled = !flashEnabled },
                modifier = Modifier.size(36.dp).testTag("flash_toggle_button")
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flash",
                    tint = if (flashEnabled) SusampTeal else Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }

            // 2. Timer (Off / 3s / 5s)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        timerSeconds = when (timerSeconds) {
                            0 -> 3
                            3 -> 5
                            else -> 0
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = if (timerSeconds > 0) SusampTeal else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    if (timerSeconds > 0) {
                        Text(
                            text = "${timerSeconds}s",
                            color = SusampTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            // 3. Shutter Sound On/Off
            IconButton(
                onClick = { soundEnabled = !soundEnabled },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Sound",
                    tint = if (soundEnabled) Color.White else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 4. Mirror Flip
            IconButton(
                onClick = { mirrorEnabled = !mirrorEnabled },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = "Mirror",
                    tint = if (mirrorEnabled) SusampTeal else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 5. Ratio (4:3 / 16:9 / 1:1)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                    .clickable {
                        aspectRatioLabel = when (aspectRatioLabel) {
                            "4:3" -> "16:9"
                            "16:9" -> "1:1"
                            else -> "4:3"
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = aspectRatioLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 6. Grid & Spirit Level
            IconButton(
                onClick = { gridVisible = !gridVisible },
                modifier = Modifier.size(36.dp).testTag("grid_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grid",
                    tint = if (gridVisible) SusampTeal else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 7. Camera Switch (Rear / Front / Scene)
            IconButton(
                onClick = {
                    val nextMode = when (viewfinderMode) {
                        ViewfinderMode.FIELD_SIMULATION -> ViewfinderMode.BACK_CAMERA
                        ViewfinderMode.BACK_CAMERA -> ViewfinderMode.FRONT_CAMERA
                        ViewfinderMode.FRONT_CAMERA -> ViewfinderMode.FIELD_SIMULATION
                    }
                    if (nextMode != ViewfinderMode.FIELD_SIMULATION && !hasCameraPermission) {
                        onRequestCameraPermission()
                    } else {
                        if (nextMode == ViewfinderMode.FIELD_SIMULATION) {
                            try {
                                activeCameraProvider?.unbindAll()
                            } catch (_: Exception) {
                            }
                            imageCapture = null
                        }
                        viewfinderMode = nextMode
                    }
                },
                modifier = Modifier.size(36.dp).testTag("camera_mode_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = if (viewfinderMode == ViewfinderMode.FIELD_SIMULATION) Color.White else SusampTeal,
                    modifier = Modifier.size(21.dp)
                )
            }

            // 8. Settings (Opens Template & Stamp Settings)
            IconButton(
                onClick = onNavigateToStudio,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. BOTTOM STAMP OVERLAY + PHOTO/VIDEO STRIP + 5-ICON CAMERA BAR
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Signature Susamp Infotech "GPS Map Camera" Stamp Overlay
            GpsMapCameraStampOverlay(
                telemetry = telemetry,
                config = stampConfig,
                mapBitmap = liveMapPreview,
                onMapClick = onNavigateToMapData,
                onStampClick = onNavigateToStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // Bottom Black Camera Control Deck (Photo/Video Mode Strip + 5-Button Row)
            val lastPhoto by viewModel.lastCapturedPhoto.collectAsStateWithLifecycle()

            Surface(
                color = Color(0xF2000000),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 6.dp, bottom = 10.dp)
                ) {
                    // Upper Mode Selector Strip: VIDEO | PHOTO (Susamp Infotech Style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isVideoMode) SusampTeal else Color.Transparent)
                                .clickable { isVideoMode = true }
                                .padding(horizontal = 14.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "VIDEO",
                                color = if (isVideoMode) Color.White else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (!isVideoMode) SusampTeal else Color.Transparent)
                                .clickable { isVideoMode = false }
                                .padding(horizontal = 14.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "PHOTO",
                                color = if (!isVideoMode) Color.White else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Lower 5-Button Action Row: Collection | Map Data | Shutter | Template | Default
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Collection
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToGallery)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("gallery_shortcut_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1F2937))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (lastPhoto != null && lastPhoto?.thumbnailPath != null) {
                                    AsyncImage(
                                        model = File(lastPhoto!!.thumbnailPath),
                                        contentDescription = "Collection",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Collection",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Collection",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 2. Map Data
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToMapData)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("map_data_shortcut_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, SusampTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Map Data",
                                    tint = MapPinRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Map Data",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 3. Center Shutter Button (White Circle with Teal Ring & Camera Icon)
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .border(3.dp, SusampTeal, CircleShape)
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(enabled = !isCapturing && activeCountdown == 0) {
                                    coroutineScope.launch {
                                        if (timerSeconds > 0) {
                                            for (t in timerSeconds downTo 1) {
                                                activeCountdown = t
                                                delay(1000L)
                                            }
                                            activeCountdown = 0
                                        }
                                        if (soundEnabled) {
                                            try {
                                                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
                                            } catch (_: Exception) {
                                            }
                                        }
                                        triggerPhotoCapture(
                                            context = context,
                                            imageCapture = imageCapture,
                                            useHardwareCapture = useHardwarePreview && isStreamActive,
                                            onBitmapReady = { bmp ->
                                                viewModel.capturePhotoWithStamp(bmp)
                                            }
                                        )
                                    }
                                }
                                .testTag("camera_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = SusampTeal,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isVideoMode) Icons.Default.Videocam else Icons.Default.CameraAlt,
                                    contentDescription = "Capture Geotagged Photo",
                                    tint = if (isVideoMode) MapPinRed else SusampTeal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 4. Template
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToStudio)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("template_studio_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, SusampTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DashboardCustomize,
                                    contentDescription = "Template",
                                    tint = SusampTeal,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Template",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // 5. Default (Folders & Cloud Batch Export)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNavigateToGallery)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("folders_export_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (photoCount > 0) {
                                        Badge(
                                            containerColor = SusampTeal,
                                            contentColor = Color.White
                                        ) {
                                            Text("$photoCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, Color(0xFF475569), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Default Folder",
                                        tint = FolderYellow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Default",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Exact Replica of the Susamp Infotech "GPS Map Camera" Stamp Overlay:
 * - Left Square Satellite/Road Map with Red Location Pin & Google watermark
 * - Right Dark Translucent Card with floating top-right "[Pin] GPS Map Camera" badge
 * - City, State, Country bold header
 * - Full street address
 * - Lat / Long
 * - Date & Time with GMT offset
 * - 2-Column Advance/Classic telemetry rows (Wind, Humidity, Weather, Compass, Altitude, Magnetic, Accuracy, Plus Code)
 */
@Composable
fun GpsMapCameraStampOverlay(
    telemetry: GpsTelemetry,
    config: StampConfig,
    mapBitmap: Bitmap?,
    onMapClick: () -> Unit,
    onStampClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontRes = when (config.fontChoice) {
        "roboto_mono" -> R.font.roboto_mono
        "oswald" -> R.font.oswald
        else -> R.font.montserrat
    }
    val stampFont = FontFamily(Font(fontRes))
    val textColor = try { Color(android.graphics.Color.parseColor(config.textColorHex)) } catch (_: Exception) { Color.White }
    val bgColor = try { Color(android.graphics.Color.parseColor(config.bgColorHex)) } catch (_: Exception) { Color(0xBF000000) }
    val accentColor = try { Color(android.graphics.Color.parseColor(config.accentColorHex)) } catch (_: Exception) { GoldPrimary }

    val isAdvance = config.templateId == "advance"

    Column(modifier = modifier) {
        // Floating Top-Right "GPS Map Camera" Brand Tab (Exact replica of Susamp Infotech stamp header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                modifier = Modifier.offset(y = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.yvgspcam_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "GPS Map Camera",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = stampFont
                    )
                }
            }
        }

        // Main Horizontal Stamp Row: [Square Map on Left] + [Dark Data Card on Right]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            if (config.showMap && mapBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(if (isAdvance) 124.dp else 110.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0x88FFFFFF), RoundedCornerShape(6.dp))
                        .clickable(onClick = onMapClick)
                ) {
                    AsyncImage(
                        model = mapBitmap,
                        contentDescription = "GPS Map Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Right Dark Info Box
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 0.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 6.dp
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onStampClick)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    // 1. City, State, Country (Large Bold Title)
                    Text(
                        text = telemetry.cityStateCountry.ifBlank { "San Francisco, California, USA" },
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = stampFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // 2. Full Street Address
                    if (config.showAddress && telemetry.address.isNotBlank()) {
                        Text(
                            text = telemetry.address,
                            color = textColor.copy(alpha = 0.95f),
                            fontSize = 10.sp,
                            fontFamily = stampFont,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 3. Lat & Long
                    if (config.showCoords) {
                        val coordText = if (config.coordinateFormat == "DMS") {
                            val latDms = LocationCompassManager.formatDms(telemetry.latitude, true)
                            val lngDms = LocationCompassManager.formatDms(telemetry.longitude, false)
                            "Lat $latDms  Long $lngDms"
                        } else {
                            String.format(Locale.US, "Lat %.6f°  Long %.6f°", telemetry.latitude, telemetry.longitude)
                        }
                        Text(
                            text = coordText,
                            color = textColor,
                            fontSize = 10.sp,
                            fontFamily = stampFont,
                            maxLines = 1
                        )
                    }

                    // 4. Date & Time with GMT Offset
                    if (config.showDateTime) {
                        val dateFormatted = SimpleDateFormat("dd/MM/yyyy hh:mm a 'GMT' Z", Locale.US)
                            .format(Date(telemetry.timestamp))
                        Text(
                            text = dateFormatted,
                            color = textColor.copy(alpha = 0.92f),
                            fontSize = 9.5.sp,
                            fontFamily = stampFont,
                            maxLines = 1
                        )
                    }

                    // 5. Weather / Wind / Humidity / Compass Row
                    val row1Parts = mutableListOf<String>()
                    if (config.showWind) row1Parts.add(String.format(Locale.US, "🌬 %.1f km/h", telemetry.windSpeedKmh))
                    if (config.showHumidity) row1Parts.add("💧 ${telemetry.humidityPercent}%")
                    if (config.showWeather) row1Parts.add(String.format(Locale.US, "🌡 %.1f°C", telemetry.weatherTempC))
                    if (config.showCompass) {
                        row1Parts.add(
                            String.format(
                                Locale.US,
                                "🧭 %.0f° %s",
                                telemetry.bearing,
                                getCompassHeading(telemetry.bearing)
                            )
                        )
                    }
                    if (row1Parts.isNotEmpty()) {
                        Text(
                            text = row1Parts.joinToString("   "),
                            color = Color(0xFFE2E8F0),
                            fontSize = 9.sp,
                            fontFamily = stampFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 6. Advance / Plus Code / Altitude / Accuracy / Magnetic Field Row
                    if (isAdvance || config.showPlusCode || config.showAltitude) {
                        val row2Parts = mutableListOf<String>()
                        if (config.showPlusCode && telemetry.plusCode.isNotBlank()) {
                            row2Parts.add("Plus Code : ${telemetry.plusCode}")
                        }
                        if (config.showAltitude) {
                            row2Parts.add(String.format(Locale.US, "⛰ %.0fm", telemetry.altitude))
                        }
                        if (config.showAccuracy) {
                            row2Parts.add(String.format(Locale.US, "🎯 %.0fm", telemetry.accuracy))
                        }
                        if (config.showMagneticField) {
                            row2Parts.add(String.format(Locale.US, "🧲 %.0f μT", telemetry.magneticFieldUt))
                        }
                        if (row2Parts.isNotEmpty()) {
                            Text(
                                text = row2Parts.joinToString("   "),
                                color = Color(0xFFCBD5E1),
                                fontSize = 9.sp,
                                fontFamily = stampFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 7. Note / Project Row
                    if (config.showNotes && config.customNotes.isNotBlank()) {
                        Text(
                            text = "Note : ${config.customNotes}",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontFamily = stampFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private suspend fun getCameraProviderSuspend(context: Context): ProcessCameraProvider? =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = try {
                    future.get()
                } catch (_: Exception) {
                    null
                }
                if (cont.isActive) {
                    cont.resume(provider)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

private fun isEmulatorEnvironment(): Boolean {
    return (Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.lowercase(Locale.US).contains("emulator")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.lowercase(Locale.US).contains("emulator")
        || Build.MODEL.contains("Android SDK built for")
        || Build.MODEL.contains("sdk_gphone")
        || Build.HARDWARE.contains("goldfish")
        || Build.HARDWARE.contains("ranchu")
        || Build.PRODUCT.contains("sdk_gphone")
        || Build.PRODUCT.contains("vbox86p")
        || Build.PRODUCT.contains("emulator")
        || Build.PRODUCT.contains("simulator"))
}

private fun hasUsableCameraDevice(context: Context): Boolean {
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val ids = cameraManager?.cameraIdList
        !ids.isNullOrEmpty()
    } catch (_: Exception) {
        false
    }
}

private fun triggerPhotoCapture(
    context: Context,
    imageCapture: ImageCapture?,
    useHardwareCapture: Boolean,
    onBitmapReady: (Bitmap) -> Unit
) {
    if (imageCapture != null && useHardwareCapture) {
        val executor = Executors.newSingleThreadExecutor()
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(context, image)
                    image.close()
                    onBitmapReady(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    val fallback = loadOutdoorStreetBitmap(context)
                    onBitmapReady(fallback)
                }
            }
        )
    } else {
        val fallback = loadOutdoorStreetBitmap(context)
        onBitmapReady(fallback)
    }
}

private fun imageProxyToBitmap(
    context: Context,
    image: ImageProxy
): Bitmap {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bitmap ?: loadOutdoorStreetBitmap(context)
}

private fun loadOutdoorStreetBitmap(context: Context): Bitmap {
    val raw = BitmapFactory.decodeResource(context.resources, R.drawable.img_live_street_view)
    if (raw != null) {
        return Bitmap.createScaledBitmap(raw, 1080, 1440, true)
    }
    return Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
}

private fun getCompassHeading(degrees: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val idx = Math.round((degrees % 360) / 45f)
    return directions[abs(idx.toInt()) % 8]
}
