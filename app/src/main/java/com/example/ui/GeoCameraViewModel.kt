package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.GpsTelemetry
import com.example.data.model.PhotoRecord
import com.example.data.model.StampConfig
import com.example.data.repository.PhotoRepository
import com.example.service.BatchExportManager
import com.example.service.LocationCompassManager
import com.example.service.OfflineMapManager
import com.example.service.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ExportStatus {
    object Idle : ExportStatus
    data class Processing(val progress: Float) : ExportStatus
    data class Completed(val zipFileName: String, val fileSizeBytes: Long) : ExportStatus
    data class Error(val message: String) : ExportStatus
}

data class MapCacheInfo(
    val tileCount: Int = 0,
    val formattedSize: String = "0.0 MB",
    val isCaching: Boolean = false,
    val statusMessage: String = "Ready"
)

class GeoCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = PhotoRepository(db.photoDao())
    val locationCompassManager = LocationCompassManager(application)
    val offlineMapManager = OfflineMapManager(application)
    private val stampRenderer = StampRenderer(application)
    val batchExportManager = BatchExportManager(application)

    val telemetry: StateFlow<GpsTelemetry> = locationCompassManager.telemetry

    private val _stampConfig = MutableStateFlow(StampConfig())
    val stampConfig: StateFlow<StampConfig> = _stampConfig.asStateFlow()

    val allPhotos: StateFlow<List<PhotoRecord>> = repository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _exportStatus = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val exportStatus: StateFlow<ExportStatus> = _exportStatus.asStateFlow()

    private val _mapCacheInfo = MutableStateFlow(MapCacheInfo())
    val mapCacheInfo: StateFlow<MapCacheInfo> = _mapCacheInfo.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<PhotoRecord?>(null)
    val lastCapturedPhoto: StateFlow<PhotoRecord?> = _lastCapturedPhoto.asStateFlow()

    private val imagesDir = File(application.getExternalFilesDir(null), "Pictures/YVGSPCAM").apply { mkdirs() }
    private val thumbsDir = File(application.filesDir, "thumbs").apply { mkdirs() }

    init {
        refreshCacheStats()
        locationCompassManager.startLocationUpdates()
        seedSampleDataIfEmpty()
    }

    private fun seedSampleDataIfEmpty() {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getPhotoById(1)
            if (existing == null && allPhotos.value.isEmpty()) {
                createSamplePhoto(
                    lat = 37.774929,
                    lng = -122.419416,
                    address = "Civic Center Plaza, San Francisco, CA",
                    project = "PRJ-SF-01",
                    notes = "Foundation concrete core inspection verified",
                    color = "#D4AF37"
                )
                createSamplePhoto(
                    lat = 37.787994,
                    lng = -122.407437,
                    address = "Union Square West, San Francisco, CA",
                    project = "PRJ-SF-02",
                    notes = "Structural elevation & survey benchmark point",
                    color = "#00E5FF"
                )
            }
        }
    }

    private suspend fun createSamplePhoto(
        lat: Double,
        lng: Double,
        address: String,
        project: String,
        notes: String,
        color: String
    ) {
        val rawPhoto = android.graphics.BitmapFactory.decodeResource(
            getApplication<Application>().resources,
            com.example.R.drawable.img_live_street_view
        )
        val dummyBmp = if (rawPhoto != null) {
            Bitmap.createScaledBitmap(rawPhoto, 1080, 1440, true)
        } else {
            Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.parseColor("#1B263B"))
            }
        }

        val time = System.currentTimeMillis()
        val baseName = "IMG_SAMPLE_${System.currentTimeMillis()}"
        val photoFile = File(imagesDir, "$baseName.jpg")
        val thumbFile = File(thumbsDir, "${baseName}_thumb.jpg")

        val sampleTelemetry = GpsTelemetry(
            latitude = lat,
            longitude = lng,
            altitude = 38.5,
            bearing = 65f,
            accuracy = 2.8f,
            cityStateCountry = "San Francisco, California, United States",
            address = address,
            plusCode = LocationCompassManager.encodePlusCode(lat, lng),
            timestamp = time,
            isGpsFixed = true
        )

        val mapBmp = offlineMapManager.getMapSnapshot(lat, lng, mapType = _stampConfig.value.mapType)
        val sampleConfig = _stampConfig.value.copy(
            customProjectCode = project,
            customNotes = notes,
            accentColorHex = color
        )

        val bytes = stampRenderer.renderAndSavePhoto(
            sourceBitmap = dummyBmp,
            mapBitmap = mapBmp,
            telemetry = sampleTelemetry,
            config = sampleConfig,
            outputFile = photoFile,
            thumbnailFile = thumbFile
        )

        repository.insertPhoto(
            PhotoRecord(
                filePath = photoFile.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                timestamp = time,
                latitude = lat,
                longitude = lng,
                altitude = 38.5,
                bearing = 65f,
                accuracy = 2.8f,
                address = address,
                projectTag = project,
                inspectorNotes = notes,
                templateId = sampleConfig.templateId,
                fileSizeBytes = bytes
            )
        )
    }

    fun updateStampConfig(update: (StampConfig) -> StampConfig) {
        _stampConfig.value = update(_stampConfig.value)
    }

    fun capturePhotoWithStamp(sourceBitmap: Bitmap) {
        viewModelScope.launch {
            _isCapturing.value = true
            try {
                val currentTelemetry = telemetry.value
                val currentConfig = _stampConfig.value

                val mapBitmap = if (currentConfig.showMap) {
                    offlineMapManager.getMapSnapshot(
                        lat = currentTelemetry.latitude,
                        lng = currentTelemetry.longitude,
                        mapType = currentConfig.mapType
                    )
                } else null

                val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val photoFile = File(imagesDir, "YV_GPS_${timestampStr}.jpg")
                val thumbFile = File(thumbsDir, "YV_GPS_${timestampStr}_thumb.jpg")

                val fileSizeBytes = stampRenderer.renderAndSavePhoto(
                    sourceBitmap = sourceBitmap,
                    mapBitmap = mapBitmap,
                    telemetry = currentTelemetry,
                    config = currentConfig,
                    outputFile = photoFile,
                    thumbnailFile = thumbFile
                )

                val record = PhotoRecord(
                    filePath = photoFile.absolutePath,
                    thumbnailPath = thumbFile.absolutePath,
                    timestamp = currentTelemetry.timestamp,
                    latitude = currentTelemetry.latitude,
                    longitude = currentTelemetry.longitude,
                    altitude = currentTelemetry.altitude,
                    bearing = currentTelemetry.bearing,
                    accuracy = currentTelemetry.accuracy,
                    address = currentTelemetry.address,
                    projectTag = currentConfig.customProjectCode,
                    inspectorNotes = currentConfig.customNotes,
                    templateId = currentConfig.templateId,
                    fileSizeBytes = fileSizeBytes
                )

                val id = repository.insertPhoto(record)
                _lastCapturedPhoto.value = record.copy(id = id)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCapturing.value = false
            }
        }
    }

    fun togglePhotoSelection(id: Long) {
        val current = _selectedPhotoIds.value
        _selectedPhotoIds.value = if (current.contains(id)) current - id else current + id
    }

    fun selectAllPhotos() {
        val allIds = allPhotos.value.map { it.id }.toSet()
        _selectedPhotoIds.value = allIds
    }

    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun deleteSelectedPhotos() {
        viewModelScope.launch {
            val selected = allPhotos.value.filter { _selectedPhotoIds.value.contains(it.id) }
            repository.deletePhotosByIds(selected)
            clearSelection()
        }
    }

    fun deleteSinglePhoto(photo: PhotoRecord) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            if (_lastCapturedPhoto.value?.id == photo.id) {
                _lastCapturedPhoto.value = null
            }
        }
    }

    fun exportBatchToZip() {
        viewModelScope.launch {
            val selectedPhotos = if (_selectedPhotoIds.value.isNotEmpty()) {
                allPhotos.value.filter { _selectedPhotoIds.value.contains(it.id) }
            } else {
                allPhotos.value
            }

            if (selectedPhotos.isEmpty()) {
                _exportStatus.value = ExportStatus.Error("No photos selected for batch export")
                return@launch
            }

            _exportStatus.value = ExportStatus.Processing(0f)

            try {
                val zipFile = batchExportManager.createBatchZip(selectedPhotos) { progress ->
                    _exportStatus.value = ExportStatus.Processing(progress)
                }

                _exportStatus.value = ExportStatus.Completed(zipFile.name, zipFile.length())
                batchExportManager.shareZipToCloud(zipFile)
            } catch (e: Exception) {
                _exportStatus.value = ExportStatus.Error(e.message ?: "Failed to generate batch zip")
            }
        }
    }

    fun shareSelectedPhotosDirect() {
        val selectedPhotos = if (_selectedPhotoIds.value.isNotEmpty()) {
            allPhotos.value.filter { _selectedPhotoIds.value.contains(it.id) }
        } else {
            allPhotos.value
        }
        if (selectedPhotos.isNotEmpty()) {
            batchExportManager.sharePhotosDirect(selectedPhotos)
        }
    }

    fun resetExportStatus() {
        _exportStatus.value = ExportStatus.Idle
    }

    fun preCacheCurrentRegion() {
        viewModelScope.launch {
            _mapCacheInfo.value = _mapCacheInfo.value.copy(
                isCaching = true,
                statusMessage = "Caching tiles around current GPS..."
            )
            val current = telemetry.value
            val cachedCount = offlineMapManager.preCacheRegion(current.latitude, current.longitude)
            refreshCacheStats()
            _mapCacheInfo.value = _mapCacheInfo.value.copy(
                isCaching = false,
                statusMessage = "Pre-cached $cachedCount map tiles successfully"
            )
        }
    }

    fun clearMapCache() {
        offlineMapManager.clearCache()
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        _mapCacheInfo.value = MapCacheInfo(
            tileCount = offlineMapManager.getCachedTileCount(),
            formattedSize = offlineMapManager.getCacheSizeFormatted(),
            isCaching = false,
            statusMessage = "Ready"
        )
    }

    override fun onCleared() {
        super.onCleared()
        locationCompassManager.unregister()
    }
}
