package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_records")
data class PhotoRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val thumbnailPath: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,
    val accuracy: Float,
    val address: String,
    val projectTag: String,
    val inspectorNotes: String,
    val templateId: String,
    val fileSizeBytes: Long
)

data class GpsTelemetry(
    val latitude: Double = 37.774929,
    val longitude: Double = -122.419416,
    val altitude: Double = 42.0,
    val bearing: Float = 45f,
    val accuracy: Float = 3.2f,
    val cityStateCountry: String = "San Francisco, California, United States",
    val address: String = "1 Dr Carlton B Goodlett Pl, Civic Center, San Francisco, CA 94102, USA",
    val plusCode: String = "849VQHFJ+X6",
    val weatherTempC: Float = 24.5f,
    val windSpeedKmh: Float = 11.2f,
    val humidityPercent: Int = 58,
    val pressureHpa: Float = 1013.2f,
    val magneticFieldUt: Float = 46.5f,
    val timestamp: Long = System.currentTimeMillis(),
    val isGpsFixed: Boolean = true,
    val isManualLocation: Boolean = false
)

data class StampConfig(
    val templateId: String = "classic",
    val mapType: String = "SATELLITE",
    val fontChoice: String = "montserrat",
    val textColorHex: String = "#FFFFFF",
    val bgColorHex: String = "#CC141414",
    val accentColorHex: String = "#D4AF37",
    val fontSizeSp: Float = 12f,
    val showMap: Boolean = true,
    val showAddress: Boolean = true,
    val showCoords: Boolean = true,
    val showDateTime: Boolean = true,
    val showPlusCode: Boolean = true,
    val showWeather: Boolean = true,
    val showWind: Boolean = true,
    val showHumidity: Boolean = true,
    val showPressure: Boolean = false,
    val showAltitude: Boolean = true,
    val showAccuracy: Boolean = true,
    val showCompass: Boolean = true,
    val showMagneticField: Boolean = true,
    val showProjectTag: Boolean = true,
    val showNotes: Boolean = true,
    val coordinateFormat: String = "DECIMAL",
    val customProjectCode: String = "PRJ-2026-YV",
    val customNotes: String = "Captured by YVGSPCAM • Field Verified"
)
