package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import com.example.data.model.GpsTelemetry
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

class LocationCompassManager(
    private val context: Context
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _telemetry = MutableStateFlow(GpsTelemetry())
    val telemetry: StateFlow<GpsTelemetry> = _telemetry.asStateFlow()

    private var locationCallback: LocationCallback? = null
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    init {
        startSensorListeners()
    }

    private fun startSensorListeners() {
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            @Suppress("DEPRECATION")
            val orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)
            orientationSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        val magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !_telemetry.value.isManualLocation) {
                    updateLocation(loc)
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!_telemetry.value.isManualLocation) {
                        result.lastLocation?.let { updateLocation(it) }
                    }
                }
            }

            locationCallback?.let {
                fusedLocationClient.requestLocationUpdates(request, it, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    fun unregister() {
        stopLocationUpdates()
        sensorManager?.unregisterListener(this)
    }

    private fun updateLocation(loc: Location) {
        val current = _telemetry.value
        val plus = encodePlusCode(loc.latitude, loc.longitude)
        _telemetry.value = current.copy(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else current.altitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else current.accuracy,
            bearing = if (loc.hasBearing()) loc.bearing else current.bearing,
            plusCode = plus,
            timestamp = System.currentTimeMillis(),
            isGpsFixed = true
        )
        fetchAddress(loc.latitude, loc.longitude)
    }

    private fun fetchAddress(lat: Double, lng: Double) {
        scope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        formatAddress(addresses.firstOrNull())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    formatAddress(addresses?.firstOrNull())
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun formatAddress(address: Address?) {
        if (address != null) {
            val locality = address.locality ?: address.subAdminArea ?: ""
            val adminArea = address.adminArea ?: ""
            val country = address.countryName ?: ""
            val headerParts = listOf(locality, adminArea, country).filter { it.isNotBlank() }
            val headerStr = if (headerParts.isNotEmpty()) {
                headerParts.joinToString(", ")
            } else {
                _telemetry.value.cityStateCountry
            }

            val fullLine = address.getAddressLine(0)
            val streetStr = if (!fullLine.isNullOrBlank()) {
                fullLine
            } else {
                val thoroughfare = address.thoroughfare ?: address.subThoroughfare ?: ""
                val postal = address.postalCode ?: ""
                listOf(thoroughfare, locality, adminArea, postal, country)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { _telemetry.value.address }
            }

            _telemetry.value = _telemetry.value.copy(
                cityStateCountry = headerStr,
                address = streetStr
            )
        }
    }

    fun setSimulatedLocation(
        lat: Double,
        lng: Double,
        address: String,
        alt: Double = 42.0,
        cityStateCountry: String? = null,
        isManual: Boolean = true
    ) {
        val header = cityStateCountry ?: address.split(",").takeLast(3).joinToString(", ").trim().ifBlank { address }
        _telemetry.value = _telemetry.value.copy(
            latitude = lat,
            longitude = lng,
            cityStateCountry = header,
            address = address,
            altitude = alt,
            plusCode = encodePlusCode(lat, lng),
            timestamp = System.currentTimeMillis(),
            isGpsFixed = true,
            isManualLocation = isManual
        )
    }

    fun setAutoLocationMode() {
        _telemetry.value = _telemetry.value.copy(isManualLocation = false)
        startLocationUpdates()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                _telemetry.value = _telemetry.value.copy(bearing = azimuth)
            }
            @Suppress("DEPRECATION")
            Sensor.TYPE_ORIENTATION -> {
                val azimuth = (event.values[0] + 360f) % 360f
                _telemetry.value = _telemetry.value.copy(bearing = azimuth)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val bx = event.values[0]
                val by = event.values[1]
                val bz = event.values[2]
                val magnitude = sqrt(bx * bx + by * by + bz * bz)
                if (magnitude > 0.1f) {
                    _telemetry.value = _telemetry.value.copy(magneticFieldUt = magnitude)
                }
            }
            Sensor.TYPE_PRESSURE -> {
                val hpa = event.values[0]
                if (hpa > 100f) {
                    _telemetry.value = _telemetry.value.copy(pressureHpa = hpa)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val PLUS_CHARS = "23456789CFGHJMPQRVWX"

        fun encodePlusCode(latitude: Double, longitude: Double): String {
            var lat = ((latitude + 90.0).coerceIn(0.0, 179.999999)) / 20.0
            var lng = ((longitude + 180.0).coerceIn(0.0, 359.999999)) / 20.0
            val sb = StringBuilder()
            for (i in 0 until 5) {
                val latDigit = floor(lat).toInt().coerceIn(0, 19)
                val lngDigit = floor(lng).toInt().coerceIn(0, 19)
                sb.append(PLUS_CHARS[latDigit])
                sb.append(PLUS_CHARS[lngDigit])
                lat = (lat - latDigit) * 20.0
                lng = (lng - lngDigit) * 20.0
                if (i == 3) sb.append('+')
            }
            return sb.toString()
        }

        fun formatDms(coordinate: Double, isLatitude: Boolean): String {
            val direction = if (isLatitude) {
                if (coordinate >= 0) "N" else "S"
            } else {
                if (coordinate >= 0) "E" else "W"
            }
            val absCoord = abs(coordinate)
            val degrees = absCoord.toInt()
            val minutesDouble = (absCoord - degrees) * 60.0
            val minutes = minutesDouble.toInt()
            val seconds = (minutesDouble - minutes) * 60.0
            return String.format(Locale.US, "%d° %02d' %04.1f\" %s", degrees, minutes, seconds, direction)
        }

        fun formatDecimal(coordinate: Double, isLatitude: Boolean): String {
            val direction = if (isLatitude) {
                if (coordinate >= 0) "N" else "S"
            } else {
                if (coordinate >= 0) "E" else "W"
            }
            return String.format(Locale.US, "%.6f° %s", abs(coordinate), direction)
        }

        fun getBearingDirection(bearing: Float): String {
            val normalized = (bearing % 360 + 360) % 360
            return when {
                normalized >= 337.5 || normalized < 22.5 -> "N"
                normalized < 67.5 -> "NE"
                normalized < 112.5 -> "E"
                normalized < 157.5 -> "SE"
                normalized < 202.5 -> "S"
                normalized < 247.5 -> "SW"
                normalized < 292.5 -> "W"
                else -> "NW"
            }
        }
    }
}
