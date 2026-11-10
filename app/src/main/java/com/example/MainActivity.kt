package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GeoCameraViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.gallery.GalleryScreen
import com.example.ui.mapcache.OfflineMapCacheScreen
import com.example.ui.studio.StampStudioScreen
import com.example.ui.theme.MontserratFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SusampBorderGray
import com.example.ui.theme.SusampCardWhite
import com.example.ui.theme.SusampTeal
import com.example.ui.theme.SusampTealLight
import com.example.ui.theme.SusampTextGray
import com.example.ui.theme.SusampWhiteBg

enum class AppScreen {
    CAMERA,
    TEMPLATES,
    GALLERY,
    MAP_CACHE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: GeoCameraViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.CAMERA) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val camGranted = results[Manifest.permission.CAMERA]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        val locGranted = (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
            (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

        hasCameraPermission = camGranted
        hasLocationPermission = locGranted
        if (locGranted) {
            viewModel.locationCompassManager.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.locationCompassManager.startLocationUpdates()
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.CAMERA) {
        currentScreen = AppScreen.CAMERA
    }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val stampConfig by viewModel.stampConfig.collectAsStateWithLifecycle()
    val photos by viewModel.allPhotos.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedPhotoIds.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val mapCacheInfo by viewModel.mapCacheInfo.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (currentScreen == AppScreen.CAMERA) Color.Black else SusampWhiteBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (currentScreen != AppScreen.CAMERA) {
                AppTopHeader(
                    currentScreen = currentScreen,
                    onBackToCamera = { currentScreen = AppScreen.CAMERA }
                )
            }
        },
        bottomBar = {
            if (currentScreen != AppScreen.CAMERA) {
                AppBottomNavBar(
                    currentScreen = currentScreen,
                    photoCount = photos.size,
                    onScreenSelected = { currentScreen = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.CAMERA -> {
                    CameraScreen(
                        viewModel = viewModel,
                        telemetry = telemetry,
                        stampConfig = stampConfig,
                        isCapturing = isCapturing,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onNavigateToGallery = { currentScreen = AppScreen.GALLERY },
                        onNavigateToStudio = { currentScreen = AppScreen.TEMPLATES },
                        onNavigateToMapData = { currentScreen = AppScreen.MAP_CACHE },
                        photoCount = photos.size
                    )
                }
                AppScreen.TEMPLATES -> {
                    StampStudioScreen(
                        viewModel = viewModel,
                        stampConfig = stampConfig,
                        telemetry = telemetry
                    )
                }
                AppScreen.GALLERY -> {
                    GalleryScreen(
                        viewModel = viewModel,
                        photos = photos,
                        selectedIds = selectedIds,
                        exportStatus = exportStatus,
                        onNavigateToCamera = { currentScreen = AppScreen.CAMERA }
                    )
                }
                AppScreen.MAP_CACHE -> {
                    OfflineMapCacheScreen(
                        viewModel = viewModel,
                        cacheInfo = mapCacheInfo,
                        telemetry = telemetry
                    )
                }
            }
        }
    }
}

@Composable
fun AppTopHeader(
    currentScreen: AppScreen,
    onBackToCamera: () -> Unit
) {
    Surface(
        color = SusampTeal,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackToCamera,
                    modifier = Modifier.testTag("back_to_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Camera",
                        tint = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.yvgspcam_logo),
                        contentDescription = "YVGSPCAM Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (currentScreen) {
                            AppScreen.TEMPLATES -> "Template"
                            AppScreen.GALLERY -> "Collection"
                            AppScreen.MAP_CACHE -> "Map Data"
                            else -> "GPS Map Camera"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MontserratFontFamily
                    )
                }
            }
        }
    }
}

@Composable
fun AppBottomNavBar(
    currentScreen: AppScreen,
    photoCount: Int,
    onScreenSelected: (AppScreen) -> Unit
) {
    NavigationBar(
        containerColor = SusampCardWhite,
        tonalElevation = 8.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .border(
                width = 1.dp,
                color = SusampBorderGray
            )
    ) {
        NavigationBarItem(
            selected = currentScreen == AppScreen.CAMERA,
            onClick = { onScreenSelected(AppScreen.CAMERA) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.CAMERA) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                    contentDescription = stringResource(R.string.camera_tab)
                )
            },
            label = { Text(stringResource(R.string.camera_tab), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SusampTeal,
                selectedTextColor = SusampTeal,
                indicatorColor = SusampTealLight,
                unselectedIconColor = SusampTextGray,
                unselectedTextColor = SusampTextGray
            ),
            modifier = Modifier.testTag("nav_camera_tab")
        )

        NavigationBarItem(
            selected = currentScreen == AppScreen.TEMPLATES,
            onClick = { onScreenSelected(AppScreen.TEMPLATES) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.TEMPLATES) Icons.Filled.Style else Icons.Outlined.Style,
                    contentDescription = stringResource(R.string.templates_tab)
                )
            },
            label = { Text("Template", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SusampTeal,
                selectedTextColor = SusampTeal,
                indicatorColor = SusampTealLight,
                unselectedIconColor = SusampTextGray,
                unselectedTextColor = SusampTextGray
            ),
            modifier = Modifier.testTag("nav_templates_tab")
        )

        NavigationBarItem(
            selected = currentScreen == AppScreen.GALLERY,
            onClick = { onScreenSelected(AppScreen.GALLERY) },
            icon = {
                if (photoCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = SusampTeal,
                                contentColor = Color.White
                            ) {
                                Text("$photoCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (currentScreen == AppScreen.GALLERY) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                            contentDescription = stringResource(R.string.gallery_tab)
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (currentScreen == AppScreen.GALLERY) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                        contentDescription = stringResource(R.string.gallery_tab)
                    )
                }
            },
            label = { Text("Collection", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SusampTeal,
                selectedTextColor = SusampTeal,
                indicatorColor = SusampTealLight,
                unselectedIconColor = SusampTextGray,
                unselectedTextColor = SusampTextGray
            ),
            modifier = Modifier.testTag("nav_gallery_tab")
        )

        NavigationBarItem(
            selected = currentScreen == AppScreen.MAP_CACHE,
            onClick = { onScreenSelected(AppScreen.MAP_CACHE) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.MAP_CACHE) Icons.Filled.Map else Icons.Outlined.Map,
                    contentDescription = stringResource(R.string.offline_map_tab)
                )
            },
            label = { Text("Map Data", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = SusampTeal,
                selectedTextColor = SusampTeal,
                indicatorColor = SusampTealLight,
                unselectedIconColor = SusampTextGray,
                unselectedTextColor = SusampTextGray
            ),
            modifier = Modifier.testTag("nav_map_cache_tab")
        )
    }
}
