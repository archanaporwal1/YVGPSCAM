package com.example.ui.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.GpsTelemetry
import com.example.data.model.StampConfig
import com.example.ui.GeoCameraViewModel
import com.example.ui.camera.GpsMapCameraStampOverlay
import com.example.ui.theme.SusampBorderGray
import com.example.ui.theme.SusampCardWhite
import com.example.ui.theme.SusampTeal
import com.example.ui.theme.SusampTealLight
import com.example.ui.theme.SusampTextDark
import com.example.ui.theme.SusampTextGray
import com.example.ui.theme.SusampWhiteBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StampStudioScreen(
    viewModel: GeoCameraViewModel,
    stampConfig: StampConfig,
    telemetry: GpsTelemetry,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var previewMapBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(telemetry.latitude, telemetry.longitude, stampConfig.mapType) {
        withContext(Dispatchers.IO) {
            previewMapBitmap = viewModel.offlineMapManager.getMapSnapshot(
                lat = telemetry.latitude,
                lng = telemetry.longitude,
                sizePx = 200,
                mapType = stampConfig.mapType
            )
        }
    }

    val templateTabs = listOf(
        Pair("advance", "Advance"),
        Pair("classic", "Classic")
    )

    val mapTypes = listOf("NORMAL", "SATELLITE", "TERRAIN", "HYBRID")

    val fontChoices = listOf(
        Pair("montserrat", "Default Sans"),
        Pair("roboto_mono", "Roboto Mono"),
        Pair("oswald", "Oswald Bold")
    )

    val accentColors = listOf(
        Pair("White", "#FFFFFF"),
        Pair("Teal", "#00BCD4"),
        Pair("Gold", "#D4AF37"),
        Pair("Yellow", "#FFEB3B"),
        Pair("Green", "#4CAF50"),
        Pair("Coral", "#FF5252")
    )

    val bgColors = listOf(
        Pair("Translucent Black", "#BF000000"),
        Pair("Dark Charcoal", "#CC141414"),
        Pair("Deep Navy", "#D90C2340"),
        Pair("Slate Gray", "#CC334155"),
        Pair("Solid Black", "#F2000000")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SusampWhiteBg)
            .verticalScroll(scrollState)
            .testTag("stamp_studio_screen")
    ) {
        // 1. Susamp Infotech White Segmented Tab Bar: [ Advance | Classic ]
        Surface(
            color = SusampCardWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                templateTabs.forEach { (id, title) ->
                    val isSelected = stampConfig.templateId == id ||
                        (id == "classic" && stampConfig.templateId != "advance")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.updateStampConfig { it.copy(templateId = id) } }
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) SusampTeal else SusampTextGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(if (isSelected) SusampTeal else Color.Transparent)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(14.dp)) {
            // 2. Live Template Preview Card on Realistic Photo Background (Susamp Infotech Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active Template",
                        tint = SusampTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (stampConfig.templateId == "advance") "Advance Template (Selected)" else "Classic Template (Selected)",
                        color = SusampTextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { viewModel.updateStampConfig { StampConfig() } },
                    modifier = Modifier.size(32.dp).testTag("reset_defaults_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Defaults",
                        tint = SusampTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, SusampTeal, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_live_street_view),
                        contentDescription = "Template Preview Background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Top-Right Edit Badge (Exact Susamp Infotech Template Card UI)
                    Surface(
                        color = SusampTeal,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editing Template",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Live Stamp Overlay at the bottom of the preview card
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(6.dp)
                    ) {
                        GpsMapCameraStampOverlay(
                            telemetry = telemetry,
                            config = stampConfig,
                            mapBitmap = previewMapBitmap,
                            onMapClick = {},
                            onStampClick = {}
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Map Type Selector Card
            StudioSectionTitle("01. MAP TYPE")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mapTypes.forEach { type ->
                        val isSelected = stampConfig.mapType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) SusampTeal else SusampTealLight.copy(alpha = 0.4f))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) SusampTeal else SusampBorderGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateStampConfig { it.copy(mapType = type) } }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                color = if (isSelected) Color.White else SusampTextDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Numbered Stamp Elements Checklist (Exact Susamp Infotech Edit Template list)
            StudioSectionTitle("02. EDIT TEMPLATE STAMP ELEMENTS")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    StampCheckItem("01. Map View (${stampConfig.mapType})", stampConfig.showMap) {
                        viewModel.updateStampConfig { c -> c.copy(showMap = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("02. Full Address & Location Title", stampConfig.showAddress) {
                        viewModel.updateStampConfig { c -> c.copy(showAddress = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("03. Lat/Long (${String.format("%.4f, %.4f", telemetry.latitude, telemetry.longitude)})", stampConfig.showCoords) {
                        viewModel.updateStampConfig { c -> c.copy(showCoords = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("04. DMS Coordinate Format", stampConfig.coordinateFormat == "DMS") {
                        viewModel.updateStampConfig { c -> c.copy(coordinateFormat = if (it) "DMS" else "DECIMAL") }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("05. Plus Code (${telemetry.plusCode})", stampConfig.showPlusCode) {
                        viewModel.updateStampConfig { c -> c.copy(showPlusCode = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("06. Date & Time (GMT Time Zone)", stampConfig.showDateTime) {
                        viewModel.updateStampConfig { c -> c.copy(showDateTime = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("07. Weather (${telemetry.weatherTempC}°C)", stampConfig.showWeather) {
                        viewModel.updateStampConfig { c -> c.copy(showWeather = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("08. Wind (${telemetry.windSpeedKmh} km/h)", stampConfig.showWind) {
                        viewModel.updateStampConfig { c -> c.copy(showWind = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("09. Humidity (${telemetry.humidityPercent}%)", stampConfig.showHumidity) {
                        viewModel.updateStampConfig { c -> c.copy(showHumidity = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("10. Compass (${telemetry.bearing.toInt()}°)", stampConfig.showCompass) {
                        viewModel.updateStampConfig { c -> c.copy(showCompass = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("11. Altitude (${telemetry.altitude.toInt()}m)", stampConfig.showAltitude) {
                        viewModel.updateStampConfig { c -> c.copy(showAltitude = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("12. Accuracy (${telemetry.accuracy.toInt()}m)", stampConfig.showAccuracy) {
                        viewModel.updateStampConfig { c -> c.copy(showAccuracy = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("13. Magnetic Field (${telemetry.magneticFieldUt.toInt()} μT)", stampConfig.showMagneticField) {
                        viewModel.updateStampConfig { c -> c.copy(showMagneticField = it) }
                    }
                    HorizontalDivider(color = SusampBorderGray)
                    StampCheckItem("14. Note / Hashtag", stampConfig.showNotes) {
                        viewModel.updateStampConfig { c -> c.copy(showNotes = it, showProjectTag = it) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Font Style & Stamp Colors Card
            StudioSectionTitle("03. FONT STYLE & STAMP COLORS")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Font Family", color = SusampTextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fontChoices.forEach { (choice, label) ->
                            val isSelected = stampConfig.fontChoice == choice
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SusampTeal else SusampWhiteBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) SusampTeal else SusampBorderGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.updateStampConfig { it.copy(fontChoice = choice) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else SusampTextDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", color = SusampTextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${stampConfig.fontSizeSp.toInt()} sp", color = SusampTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = stampConfig.fontSizeSp,
                        onValueChange = { size -> viewModel.updateStampConfig { it.copy(fontSizeSp = size) } },
                        valueRange = 10f..18f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = SusampTeal, activeTrackColor = SusampTeal)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Highlight & Note Color", color = SusampTextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        accentColors.forEach { (name, hex) ->
                            val isSelected = stampConfig.accentColorHex.equals(hex, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) SusampTealLight else SusampWhiteBg)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) SusampTeal else SusampBorderGray,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.updateStampConfig { it.copy(accentColorHex = hex) } }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(0.5.dp, Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(name, color = SusampTextDark, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Stamp Background Overlay", color = SusampTextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bgColors.forEach { (name, hex) ->
                            val isSelected = stampConfig.bgColorHex.equals(hex, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) SusampTeal else SusampBorderGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.updateStampConfig { it.copy(bgColorHex = hex) } }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Custom Note & Project Code Card
            StudioSectionTitle("04. NOTE / HASHTAG")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = stampConfig.customProjectCode,
                        onValueChange = { tag -> viewModel.updateStampConfig { it.copy(customProjectCode = tag) } },
                        label = { Text("Project / Survey Tag") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SusampTeal,
                            focusedLabelColor = SusampTeal,
                            focusedTextColor = SusampTextDark,
                            unfocusedTextColor = SusampTextDark
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("studio_project_code_field")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stampConfig.customNotes,
                        onValueChange = { note -> viewModel.updateStampConfig { it.copy(customNotes = note) } },
                        label = { Text("Note Text on Photo") },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SusampTeal,
                            focusedLabelColor = SusampTeal,
                            focusedTextColor = SusampTextDark,
                            unfocusedTextColor = SusampTextDark
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("studio_notes_field")
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StampCheckItem(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (checked) SusampTextDark else SusampTextGray,
            fontSize = 13.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
        )
        Icon(
            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = label,
            tint = if (checked) SusampTeal else SusampTextGray,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun StudioSectionTitle(title: String) {
    Text(
        text = title,
        color = SusampTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
