package com.example.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.data.model.PhotoRecord
import com.example.service.LocationCompassManager
import com.example.ui.ExportStatus
import com.example.ui.GeoCameraViewModel
import com.example.ui.theme.FolderYellow
import com.example.ui.theme.SusampBorderGray
import com.example.ui.theme.SusampCardWhite
import com.example.ui.theme.SusampTeal
import com.example.ui.theme.SusampTextDark
import com.example.ui.theme.SusampTextGray
import com.example.ui.theme.SusampWhiteBg
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryScreen(
    viewModel: GeoCameraViewModel,
    photos: List<PhotoRecord>,
    selectedIds: Set<Long>,
    exportStatus: ExportStatus,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inspectingPhoto by remember { mutableStateOf<PhotoRecord?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SusampWhiteBg)
            .testTag("gallery_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Susamp Infotech Default Folder & Selection Header
            if (photos.isNotEmpty()) {
                Surface(
                    color = SusampCardWhite,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = FolderYellow,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Default Folder (YVGSPCAM)",
                                    color = SusampTextDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedIds.isNotEmpty()) "${selectedIds.size} Selected of ${photos.size} Photos" else "${photos.size} Geotagged Photos",
                                    color = SusampTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (selectedIds.size == photos.size) viewModel.clearSelection() else viewModel.selectAllPhotos()
                                },
                                modifier = Modifier.size(36.dp).testTag("select_all_photos_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Select All",
                                    tint = if (selectedIds.size == photos.size) SusampTeal else SusampTextGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (selectedIds.isNotEmpty()) {
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.size(36.dp).testTag("delete_selected_photos_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Photo Grid or Empty State
            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = SusampTeal,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No Geotagged Photos Yet", color = SusampTextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Capture photos in the camera screen to stamp GPS coordinates, satellite map, address, and weather.",
                            color = SusampTextGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToCamera,
                            colors = ButtonDefaults.buttonColors(containerColor = SusampTeal, contentColor = Color.White)
                        ) {
                            Text("Open GPS Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).testTag("photos_grid")
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelected = selectedIds.contains(photo.id)
                        PhotoGridTile(
                            photo = photo,
                            isSelected = isSelected,
                            onToggleSelect = { viewModel.togglePhotoSelection(photo.id) },
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.togglePhotoSelection(photo.id)
                                } else {
                                    inspectingPhoto = photo
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Batch Export Bar
            if (photos.isNotEmpty()) {
                val isProcessing = exportStatus is ExportStatus.Processing
                Surface(
                    color = SusampCardWhite,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportBatchToZip() },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cloud_batch_export_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = SusampTeal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                val progress = (exportStatus as ExportStatus.Processing).progress
                                Text("Packaging ZIP ${(progress * 100).toInt()}%...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                val count = if (selectedIds.isNotEmpty()) selectedIds.size else photos.size
                                Text("Batch Export $count Photos (Drive / ZIP / KML)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Photo Detail Dialog
        inspectingPhoto?.let { photo ->
            InspectPhotoModal(
                photo = photo,
                onDismiss = { inspectingPhoto = null },
                onShare = { viewModel.shareSelectedPhotosDirect() },
                onDelete = {
                    viewModel.deleteSinglePhoto(photo)
                    inspectingPhoto = null
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Photos", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete ${selectedIds.size} selected photos?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSelectedPhotos()
                        showDeleteConfirm = false
                    }) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PhotoGridTile(
    photo: PhotoRecord,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    val file = File(photo.thumbnailPath.ifBlank { photo.filePath })
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) SusampTeal else SusampBorderGray,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .testTag("photo_tile_${photo.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SusampCardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(file),
                    contentDescription = photo.projectTag,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(SusampWhiteBg), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SusampTeal)
                }
            }

            // Selection badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) SusampTeal else Color(0x99000000))
                    .clickable { onToggleSelect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom pill with coordinates
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = photo.address.ifBlank { "GPS Stamped Photo" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = "Lat ${String.format("%.4f", photo.latitude)}° Long ${String.format("%.4f", photo.longitude)}°",
                    color = Color(0xFFE2E8F0),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun InspectPhotoModal(
    photo: PhotoRecord,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val file = File(photo.filePath)
    val scroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF50A0A0A))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = "GPS Photo Preview",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Row {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = SusampTeal)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (file.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(file),
                            contentDescription = "Full photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().background(Color.Black)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().height(155.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SusampCardWhite)
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
                        val dateFormatted = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(Date(photo.timestamp))
                        val latDms = LocationCompassManager.formatDms(photo.latitude, true)
                        val lngDms = LocationCompassManager.formatDms(photo.longitude, false)
                        val dir = LocationCompassManager.getBearingDirection(photo.bearing)

                        InspectRow("Coordinates (DMS)", "$latDms, $lngDms")
                        InspectRow("Coordinates (Dec)", "${photo.latitude}, ${photo.longitude}")
                        InspectRow("Address", photo.address)
                        InspectRow("Altitude", "${String.format("%.1f", photo.altitude)} m")
                        InspectRow("Compass", "${photo.bearing.toInt()}° $dir")
                        InspectRow("Accuracy", "±${String.format("%.1f", photo.accuracy)} m")
                        InspectRow("Date & Time", dateFormatted)
                        if (photo.inspectorNotes.isNotBlank()) {
                            InspectRow("Note", photo.inspectorNotes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InspectRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = SusampTextGray, fontSize = 11.sp)
        Text(value, color = SusampTextDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
