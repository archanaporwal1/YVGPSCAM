package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.GpsTelemetry
import com.example.data.model.StampConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class StampRenderer(private val context: Context) {

    private val monoTypeface by lazy {
        try {
            ResourcesCompat.getFont(context, R.font.roboto_mono) ?: Typeface.MONOSPACE
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }

    private val sansTypeface by lazy {
        try {
            ResourcesCompat.getFont(context, R.font.montserrat) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
    }

    private val oswaldTypeface by lazy {
        try {
            ResourcesCompat.getFont(context, R.font.oswald) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    suspend fun renderAndSavePhoto(
        sourceBitmap: Bitmap,
        mapBitmap: Bitmap?,
        telemetry: GpsTelemetry,
        config: StampConfig,
        outputFile: File,
        thumbnailFile: File
    ): Long = withContext(Dispatchers.IO) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val workingBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(workingBitmap)

        drawStamp(canvas, width, height, mapBitmap, telemetry, config)

        FileOutputStream(outputFile).use { out ->
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val thumbWidth = 360
        val thumbHeight = (thumbWidth * (height.toFloat() / width)).toInt()
        val thumbBitmap = Bitmap.createScaledBitmap(workingBitmap, thumbWidth, thumbHeight, true)
        FileOutputStream(thumbnailFile).use { out ->
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        thumbBitmap.recycle()
        workingBitmap.recycle()

        writeExifData(outputFile, telemetry, config)

        outputFile.length()
    }

    private fun drawStamp(
        canvas: Canvas,
        imgWidth: Int,
        imgHeight: Int,
        mapBitmap: Bitmap?,
        telemetry: GpsTelemetry,
        config: StampConfig
    ) {
        val scale = imgWidth / 1080f
        val typeface = when (config.fontChoice) {
            "roboto_mono" -> monoTypeface
            "oswald" -> oswaldTypeface
            else -> sansTypeface
        }

        val primaryColor = try { Color.parseColor(config.textColorHex) } catch (_: Exception) { Color.WHITE }
        val accentColor = try { Color.parseColor(config.accentColorHex) } catch (_: Exception) { Color.parseColor("#D4AF37") }
        val bgColor = try { Color.parseColor(config.bgColorHex) } catch (_: Exception) { Color.parseColor("#CC141414") }

        when (config.templateId) {
            "survey_compact", "surveyor_compact" ->
                drawSurveyorCompact(canvas, imgWidth, imgHeight, scale, mapBitmap, telemetry, config, typeface, primaryColor, accentColor, bgColor)
            "blueprint_grid", "blueprint" ->
                drawBlueprintStamp(canvas, imgWidth, imgHeight, scale, mapBitmap, telemetry, config, typeface, primaryColor, accentColor)
            else ->
                drawGpsMapCameraClassicOrAdvance(canvas, imgWidth, imgHeight, scale, mapBitmap, telemetry, config, typeface, primaryColor, accentColor, bgColor)
        }
    }

    private fun drawGpsMapCameraClassicOrAdvance(
        canvas: Canvas,
        w: Int,
        h: Int,
        scale: Float,
        map: Bitmap?,
        telemetry: GpsTelemetry,
        config: StampConfig,
        typeface: Typeface,
        primaryColor: Int,
        accentColor: Int,
        bgColor: Int
    ) {
        val isAdvance = config.templateId == "advance"
        val outerMargin = 20f * scale
        val gap = 14f * scale
        val stampHeight = (if (isAdvance) 300f else 245f) * scale
        val stampTop = h - stampHeight - outerMargin
        val stampBottom = h - outerMargin

        var cardLeft = outerMargin

        // 1. Left Square Map Box (Signature GPS Map Camera layout)
        if (config.showMap && map != null) {
            val mapRect = RectF(
                outerMargin,
                stampTop,
                outerMargin + stampHeight,
                stampBottom
            )
            canvas.drawBitmap(map, null, mapRect, null)
            val mapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#88FFFFFF")
                strokeWidth = 2f * scale
                style = Paint.Style.STROKE
            }
            canvas.drawRoundRect(mapRect, 10f * scale, 10f * scale, mapBorderPaint)
            cardLeft = mapRect.right + gap
        }

        // 2. Right Dark Translucent Details Box
        val cardRect = RectF(cardLeft, stampTop, w - outerMargin, stampBottom)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, 10f * scale, 10f * scale, cardBgPaint)

        // 3. Top-Right Floating "GPS Map Camera" Brand Tab (Exact Susamp Infotech style)
        val badgeWidth = 265f * scale
        val badgeHeight = 36f * scale
        val badgeRect = RectF(
            cardRect.right - badgeWidth,
            cardRect.top - badgeHeight + (4f * scale),
            cardRect.right,
            cardRect.top + (4f * scale)
        )
        canvas.drawRoundRect(badgeRect, 8f * scale, 8f * scale, cardBgPaint)

        // Mini Gold Camera/Pin Icon inside badge
        val iconCenterX = badgeRect.left + 22f * scale
        val iconCenterY = badgeRect.centerY()
        val badgeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(iconCenterX, iconCenterY, 10f * scale, badgeIconPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = sansTypeface
            this.color = Color.WHITE
            this.isFakeBoldText = true
            this.textSize = 18.5f * scale
        }
        canvas.drawText("GPS Map Camera", iconCenterX + 16f * scale, iconCenterY + 6.5f * scale, badgeTextPaint)

        // 4. Text Content inside Right Details Card
        val padX = cardRect.left + 18f * scale
        var cursorY = cardRect.top + 38f * scale

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = primaryColor
            this.isFakeBoldText = true
            this.textSize = (config.fontSizeSp + 5f) * 1.95f * scale
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = primaryColor
            this.textSize = (config.fontSizeSp + 1f) * 1.75f * scale
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = Color.parseColor("#E2E8F0")
            this.textSize = config.fontSizeSp * 1.65f * scale
        }

        // Line 1: City, State, Country
        val headerLocation = telemetry.cityStateCountry.ifBlank { "San Francisco, California, USA" }
        canvas.drawText(ellipsize(headerLocation, 38), padX, cursorY, titlePaint)

        // Line 2: Full Street Address
        if (config.showAddress && telemetry.address.isNotBlank()) {
            cursorY += 32f * scale
            canvas.drawText(ellipsize(telemetry.address, 50), padX, cursorY, bodyPaint)
        }

        // Line 3: Lat / Long
        if (config.showCoords) {
            cursorY += 30f * scale
            val coordStr = if (config.coordinateFormat == "DMS") {
                formatToDms(telemetry.latitude, telemetry.longitude)
            } else {
                String.format(Locale.US, "Lat %.6f°  Long %.6f°", telemetry.latitude, telemetry.longitude)
            }
            canvas.drawText(coordStr, padX, cursorY, bodyPaint)
        }

        // Line 4: Date & Time (+ Plus Code if Classic)
        if (config.showDateTime) {
            cursorY += 29f * scale
            val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a 'GMT' Z", Locale.US).format(Date(telemetry.timestamp))
            canvas.drawText(dateStr, padX, cursorY, bodyPaint)
        }

        // Line 5: Plus Code & Weather / Compass / Wind / Humidity
        val envItems = mutableListOf<String>()
        if (config.showPlusCode && telemetry.plusCode.isNotBlank()) {
            envItems.add("Plus Code: ${telemetry.plusCode}")
        }
        if (config.showWeather) {
            envItems.add(String.format(Locale.US, "%.1f°C", telemetry.weatherTempC))
        }
        if (config.showWind) {
            envItems.add(String.format(Locale.US, "Wind: %.1f km/h", telemetry.windSpeedKmh))
        }
        if (config.showHumidity) {
            envItems.add("Humidity: ${telemetry.humidityPercent}%")
        }

        if (envItems.isNotEmpty()) {
            cursorY += 28f * scale
            canvas.drawText(envItems.joinToString("  |  "), padX, cursorY, subPaint)
        }

        // Line 6 (Advance or Sensor Row): Altitude, Accuracy, Compass, Magnetic Field, Notes
        val sensorItems = mutableListOf<String>()
        if (config.showAltitude) {
            sensorItems.add(String.format(Locale.US, "Altitude: %.1fm", telemetry.altitude))
        }
        if (config.showAccuracy) {
            sensorItems.add(String.format(Locale.US, "Accuracy: %.1fm", telemetry.accuracy))
        }
        if (config.showCompass) {
            sensorItems.add(String.format(Locale.US, "Compass: %.0f° %s", telemetry.bearing, getCardinalDirection(telemetry.bearing)))
        }
        if (isAdvance && config.showMagneticField) {
            sensorItems.add(String.format(Locale.US, "Magnetic: %.0f μT", telemetry.magneticFieldUt))
        }

        if (sensorItems.isNotEmpty()) {
            cursorY += 27f * scale
            canvas.drawText(sensorItems.joinToString("  |  "), padX, cursorY, subPaint)
        }

        if ((isAdvance || config.showNotes) && config.customNotes.isNotBlank()) {
            cursorY += 27f * scale
            val notePaint = Paint(subPaint).apply {
                color = accentColor
            }
            val prefix = if (config.showProjectTag && config.customProjectCode.isNotBlank()) {
                "[${config.customProjectCode}] "
            } else ""
            canvas.drawText("Note: $prefix${ellipsize(config.customNotes, 42)}", padX, cursorY, notePaint)
        }
    }

    private fun drawSurveyorCompact(
        canvas: Canvas,
        w: Int,
        h: Int,
        scale: Float,
        map: Bitmap?,
        telemetry: GpsTelemetry,
        config: StampConfig,
        typeface: Typeface,
        primaryColor: Int,
        accentColor: Int,
        bgColor: Int
    ) {
        val bannerHeight = (135f * scale).toInt()
        val rect = RectF(0f, (h - bannerHeight).toFloat(), w.toFloat(), h.toFloat())

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRect(rect, bgPaint)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 3f * scale
        }
        canvas.drawLine(0f, rect.top, w.toFloat(), rect.top, linePaint)

        var left = 24f * scale
        if (config.showMap && map != null) {
            val mapSize = bannerHeight - (20f * scale)
            val mapRect = RectF(left, rect.top + (10f * scale), left + mapSize, rect.top + (10f * scale) + mapSize)
            canvas.drawBitmap(map, null, mapRect, null)
            left += mapSize + (20f * scale)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = primaryColor
            this.textSize = (config.fontSizeSp + 1f) * 2.0f * scale
        }

        val metaPaint = Paint(textPaint).apply {
            this.typeface = monoTypeface
            this.color = accentColor
            this.textSize = config.fontSizeSp * 1.8f * scale
        }

        val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date(telemetry.timestamp))
        val coords = String.format(Locale.US, "Lat %.5f° Long %.5f°", telemetry.latitude, telemetry.longitude)
        val dir = getCardinalDirection(telemetry.bearing)

        canvas.drawText("${telemetry.cityStateCountry} • $coords", left, rect.top + (46f * scale), textPaint)
        canvas.drawText("$dateStr • Plus Code: ${telemetry.plusCode} • Alt: %.1fm • %s (%.0f°)".format(Locale.US, telemetry.altitude, dir, telemetry.bearing), left, rect.top + (92f * scale), metaPaint)
    }

    private fun drawBlueprintStamp(
        canvas: Canvas,
        w: Int,
        h: Int,
        scale: Float,
        map: Bitmap?,
        telemetry: GpsTelemetry,
        config: StampConfig,
        typeface: Typeface,
        primaryColor: Int,
        accentColor: Int
    ) {
        val stampHeight = (230f * scale).toInt()
        val margin = 20f * scale
        val rect = RectF(margin, h - stampHeight - margin, w - margin, h - margin)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E6081E3D") }
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 2f * scale
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, borderPaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = monoTypeface
            this.color = Color.parseColor("#00E5FF")
            this.textSize = 23f * scale
            this.isFakeBoldText = true
        }
        canvas.drawText("GPS MAP CAMERA // ${config.customProjectCode} // ${telemetry.plusCode}", rect.left + (20f * scale), rect.top + (35f * scale), headerPaint)

        var left = rect.left + (20f * scale)
        if (config.showMap && map != null) {
            val mapSize = stampHeight - (60f * scale)
            val mapRect = RectF(left, rect.top + (45f * scale), left + mapSize, rect.top + (45f * scale) + mapSize)
            canvas.drawBitmap(map, null, mapRect, null)
            canvas.drawRect(mapRect, borderPaint)
            left += mapSize + (20f * scale)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = monoTypeface
            this.color = Color.WHITE
            this.textSize = 19.5f * scale
        }

        var cy = rect.top + (72f * scale)
        canvas.drawText(ellipsize(telemetry.address, 48), left, cy, textPaint)
        cy += 30f * scale
        canvas.drawText(String.format(Locale.US, "Lat %.6f° Long %.6f° (±%.1fm)", telemetry.latitude, telemetry.longitude, telemetry.accuracy), left, cy, textPaint)
        cy += 30f * scale
        canvas.drawText(String.format(Locale.US, "Alt: %.1fm | Compass: %.0f° %s | %.1f°C | %.0f μT", telemetry.altitude, telemetry.bearing, getCardinalDirection(telemetry.bearing), telemetry.weatherTempC, telemetry.magneticFieldUt), left, cy, textPaint)
        cy += 30f * scale
        canvas.drawText(SimpleDateFormat("dd/MM/yyyy hh:mm:ss a 'GMT' Z", Locale.US).format(Date(telemetry.timestamp)), left, cy, textPaint)
    }

    private fun ellipsize(text: String, maxChars: Int): String {
        return if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
    }

    private fun formatToDms(latitude: Double, longitude: Double): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lngDir = if (longitude >= 0) "E" else "W"

        fun dmsString(decimal: Double): String {
            val d = decimal.toInt()
            val m = ((decimal - d) * 60).toInt()
            val s = ((decimal - d - m / 60.0) * 3600)
            return String.format(Locale.US, "%d°%d'%.1f\"", d, m, s)
        }

        return "Lat ${dmsString(abs(latitude))}$latDir  Long ${dmsString(abs(longitude))}$lngDir"
    }

    private fun getCardinalDirection(bearing: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
        val index = Math.round((bearing % 360) / 45f)
        return directions[index.toInt() % 8]
    }

    private fun writeExifData(file: File, telemetry: GpsTelemetry, config: StampConfig) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val lat = abs(telemetry.latitude)
            val latRef = if (telemetry.latitude >= 0) "N" else "S"
            val lng = abs(telemetry.longitude)
            val lngRef = if (telemetry.longitude >= 0) "E" else "W"

            fun convertToExifGps(decimal: Double): String {
                val degrees = decimal.toInt()
                val minutes = ((decimal - degrees) * 60).toInt()
                val seconds = ((decimal - degrees - minutes / 60.0) * 3600000).toInt()
                return "$degrees/1,$minutes/1,$seconds/1000"
            }

            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToExifGps(lat))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToExifGps(lng))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lngRef)
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(abs(telemetry.altitude) * 100).toInt()}/100")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (telemetry.altitude >= 0) "0" else "1")

            val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(telemetry.timestamp))
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifDateFormat)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateFormat)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "YVGSPCAM - GPS Map Camera")
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "PlusCode: ${telemetry.plusCode} | Project: ${config.customProjectCode} | Notes: ${config.customNotes} | Address: ${telemetry.address}")

            exif.saveAttributes()
        } catch (_: Exception) {
        }
    }
}
