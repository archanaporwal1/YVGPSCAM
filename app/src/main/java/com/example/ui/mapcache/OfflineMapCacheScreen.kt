package com.example.ui.mapcache

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.GpsTelemetry
import com.example.ui.GeoCameraViewModel
import com.example.ui.MapCacheInfo
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SusampBorderGray
import com.example.ui.theme.SusampCardWhite
import com.example.ui.theme.SusampTeal
import com.example.ui.theme.SusampTealLight
import com.example.ui.theme.SusampTextDark
import com.example.ui.theme.SusampTextGray
import com.example.ui.theme.SusampWhiteBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun OfflineMapCacheScreen(
    viewModel: GeoCameraViewModel,
    cacheInfo: MapCacheInfo,
    telemetry: GpsTelemetry,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isManualTab by remember { mutableStateOf(telemetry.isManualLocation) }

    var manualLat by remember(telemetry.latitude) { mutableStateOf(telemetry.latitude.toString()) }
    var manualLng by remember(telemetry.longitude) { mutableStateOf(telemetry.longitude.toString()) }
    var manualCity by remember(telemetry.cityStateCountry) { mutableStateOf(telemetry.cityStateCountry) }
    var manualAddress by remember(telemetry.address) { mutableStateOf(telemetry.address) }

    var mapPreview by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(telemetry.latitude, telemetry.longitude) {
        withContext(Dispatchers.IO) {
            mapPreview = viewModel.offlineMapManager.getMapSnapshot(
                lat = telemetry.latitude,
                lng = telemetry.longitude,
                width = 520,
                height = 260,
                mapType = "SATELLITE"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SusampWhiteBg)
            .verticalScroll(scrollState)
            .testTag("offline_map_cache_screen")
    ) {
        // 1. Susamp Infotech Segmented Header Tabs: [ Automatic | Manual ]
        Surface(
            color = SusampCardWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isManualTab = false
                            viewModel.locationCompassManager.setAutoLocationMode()
                        }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Automatic",
                        color = if (!isManualTab) SusampTeal else SusampTextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (!isManualTab) SusampTeal else Color.Transparent)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isManualTab = true }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Manual",
                        color = if (isManualTab) SusampTeal else SusampTextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (isManualTab) SusampTeal else Color.Transparent)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(14.dp)) {
            // 2. Live Map Banner with Red Location Pin & Coordinate Cards
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF1E293B))
                    ) {
                        if (mapPreview != null) {
                            AsyncImage(
                                model = mapPreview,
                                contentDescription = "Map Data Location Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SusampWhiteBg)
                                    .border(1.dp, SusampBorderGray, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("LATITUDE", color = SusampTextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = String.format(Locale.US, "%.6f°", telemetry.latitude),
                                        color = SusampTextDark,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RobotoMonoFontFamily
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SusampWhiteBg)
                                    .border(1.dp, SusampBorderGray, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("LONGITUDE", color = SusampTextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = String.format(Locale.US, "%.6f°", telemetry.longitude),
                                        color = SusampTextDark,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = RobotoMonoFontFamily
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("LOCATION HEADER", color = SusampTextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(telemetry.cityStateCountry, color = SusampTextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                color = SusampTealLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Plus: ${telemetry.plusCode}",
                                    color = SusampTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = RobotoMonoFontFamily,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("FULL STREET ADDRESS", color = SusampTextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(telemetry.address, color = SusampTextDark, fontSize = 12.sp)
                    }
                }
            }

            // Manual Location Editor (shown when Manual tab is active)
            if (isManualTab) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "MANUAL LOCATION DETAILS",
                            color = SusampTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualLat,
                                onValueChange = { manualLat = it },
                                label = { Text("Latitude") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SusampTeal,
                                    focusedLabelColor = SusampTeal,
                                    focusedTextColor = SusampTextDark,
                                    unfocusedTextColor = SusampTextDark
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = manualLng,
                                onValueChange = { manualLng = it },
                                label = { Text("Longitude") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SusampTeal,
                                    focusedLabelColor = SusampTeal,
                                    focusedTextColor = SusampTextDark,
                                    unfocusedTextColor = SusampTextDark
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualCity,
                            onValueChange = { manualCity = it },
                            label = { Text("City, State, Country") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SusampTeal,
                                focusedLabelColor = SusampTeal,
                                focusedTextColor = SusampTextDark,
                                unfocusedTextColor = SusampTextDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = { manualAddress = it },
                            label = { Text("Full Street Address") },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SusampTeal,
                                focusedLabelColor = SusampTeal,
                                focusedTextColor = SusampTextDark,
                                unfocusedTextColor = SusampTextDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val parsedLat = manualLat.toDoubleOrNull() ?: telemetry.latitude
                                val parsedLng = manualLng.toDoubleOrNull() ?: telemetry.longitude
                                viewModel.locationCompassManager.setSimulatedLocation(
                                    lat = parsedLat,
                                    lng = parsedLng,
                                    address = manualAddress,
                                    cityStateCountry = manualCity,
                                    isManual = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SusampTeal, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Manual Location Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Offline Map Cache Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SusampTealLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "Offline Cache",
                                tint = SusampTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Offline Map Tile Cache",
                                color = SusampTextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Pre-download map tiles for offline photo geotagging",
                                color = SusampTextGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatBox(
                            title = "CACHED TILES",
                            value = "${cacheInfo.tileCount}",
                            unit = "TILES",
                            color = SusampTeal,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            title = "STORAGE USAGE",
                            value = cacheInfo.formattedSize,
                            unit = "DISK",
                            color = SusampTextDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "STATUS: ${cacheInfo.statusMessage}",
                        color = if (cacheInfo.isCaching) SusampTeal else SusampTextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.preCacheCurrentRegion() },
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("pre_cache_region_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SusampTeal, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !cacheInfo.isCaching
                    ) {
                        if (cacheInfo.isCaching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Caching Regional Map Tiles...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pre-Cache Current Map Region", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.refreshCacheStats() },
                            modifier = Modifier.weight(1f).testTag("refresh_stats_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = SusampTeal)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh", fontSize = 12.sp, color = SusampTeal)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearMapCache() },
                            modifier = Modifier.weight(1f).testTag("clear_cache_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Cache", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SusampWhiteBg)
            .border(1.dp, SusampBorderGray, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(title, color = SusampTextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, color = SusampTextGray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
