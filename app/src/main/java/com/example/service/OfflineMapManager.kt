package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

class OfflineMapManager(private val context: Context) {

    private val cacheDir = File(context.filesDir, "map_cache").apply { if (!exists()) mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun getMapSnapshot(
        lat: Double,
        lng: Double,
        zoom: Int = 15,
        width: Int = 240,
        height: Int = 240,
        sizePx: Int = width,
        mapType: String = "SATELLITE"
    ): Bitmap = withContext(Dispatchers.IO) {
        val targetWidth = if (sizePx != 240 && width == 240) sizePx else width
        val targetHeight = if (sizePx != 240 && height == 240) sizePx else height

        if (mapType == "NORMAL" || mapType == "HYBRID") {
            val cachedFile = findCachedTileFile(lat, lng, zoom)
            if (cachedFile != null && cachedFile.exists()) {
                val bmp = BitmapFactory.decodeFile(cachedFile.absolutePath)
                if (bmp != null) {
                    val scaled = Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true)
                    return@withContext overlayMapPinAndBranding(scaled)
                }
            }

            val downloaded = downloadOsmTile(lat, lng, zoom, targetWidth, targetHeight)
            if (downloaded != null) {
                saveTileToDisk(lat, lng, zoom, downloaded)
                return@withContext overlayMapPinAndBranding(downloaded)
            }
        }

        return@withContext generateOfflineRadarBitmap(lat, lng, targetWidth, targetHeight, mapType)
    }

    suspend fun preCacheRegion(lat: Double, lng: Double, zoom: Int = 15): Int = withContext(Dispatchers.IO) {
        var count = 0
        val offsets = listOf(
            Pair(0.0, 0.0),
            Pair(0.005, 0.0),
            Pair(-0.005, 0.0),
            Pair(0.0, 0.005),
            Pair(0.0, -0.005)
        )
        for (offset in offsets) {
            val targetLat = lat + offset.first
            val targetLng = lng + offset.second
            val downloaded = downloadOsmTile(targetLat, targetLng, zoom, 256, 256)
            val bmp = downloaded ?: generateOfflineRadarBitmap(targetLat, targetLng, 256, 256, "SATELLITE")
            saveTileToDisk(targetLat, targetLng, zoom, bmp)
            count++
        }
        count
    }

    fun getCachedTileCount(): Int {
        return cacheDir.listFiles()?.size ?: 0
    }

    fun getCacheSizeFormatted(): String {
        val totalBytes = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        val mb = totalBytes.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", mb)
    }

    fun clearCache(): Boolean {
        cacheDir.listFiles()?.forEach { it.delete() }
        return true
    }

    private fun findCachedTileFile(lat: Double, lng: Double, zoom: Int): File? {
        val (tileX, tileY) = getTileNumber(lat, lng, zoom)
        val file = File(cacheDir, "tile_${zoom}_${tileX}_${tileY}.png")
        return if (file.exists()) file else null
    }

    private fun saveTileToDisk(lat: Double, lng: Double, zoom: Int, bitmap: Bitmap) {
        try {
            val (tileX, tileY) = getTileNumber(lat, lng, zoom)
            val file = File(cacheDir, "tile_${zoom}_${tileX}_${tileY}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
        } catch (_: Exception) {
        }
    }

    private fun downloadOsmTile(lat: Double, lng: Double, zoom: Int, width: Int, height: Int): Bitmap? {
        return try {
            val (tileX, tileY) = getTileNumber(lat, lng, zoom)
            val url = "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YVGSPCAM-FieldCamera/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()?.let { bytes ->
                    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (raw != null) {
                        return Bitmap.createScaledBitmap(raw, width, height, true)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun generateOfflineRadarBitmap(
        lat: Double,
        lng: Double,
        width: Int,
        height: Int,
        mapType: String = "SATELLITE"
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()

        val isSatellite = mapType == "SATELLITE" || mapType == "HYBRID"
        val isTerrain = mapType == "TERRAIN"

        // Base map land color (Satellite dark green/earth vs Normal street cream/gray)
        val bgPaint = Paint().apply {
            color = when {
                isSatellite -> Color.parseColor("#263829")
                isTerrain -> Color.parseColor("#D9E3C8")
                else -> Color.parseColor("#EBF0EC")
            }
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw city blocks & vegetation patches for realistic satellite/map look
        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isSatellite -> Color.parseColor("#314936")
                isTerrain -> Color.parseColor("#C5D6B0")
                else -> Color.parseColor("#DCE4DE")
            }
            style = Paint.Style.FILL
        }
        canvas.drawRect(w * 0.06f, h * 0.08f, w * 0.42f, h * 0.36f, blockPaint)
        canvas.drawRect(w * 0.56f, h * 0.10f, w * 0.92f, h * 0.40f, blockPaint)
        canvas.drawRect(w * 0.08f, h * 0.54f, w * 0.44f, h * 0.88f, blockPaint)
        canvas.drawRect(w * 0.58f, h * 0.56f, w * 0.90f, h * 0.85f, blockPaint)

        // Urban building footprints in satellite/hybrid view
        val roofPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSatellite) Color.parseColor("#47554B") else Color.parseColor("#CBD5E1")
            style = Paint.Style.FILL
        }
        canvas.drawRect(w * 0.12f, h * 0.14f, w * 0.34f, h * 0.30f, roofPaint)
        canvas.drawRect(w * 0.62f, h * 0.18f, w * 0.84f, h * 0.34f, roofPaint)
        canvas.drawRect(w * 0.64f, h * 0.62f, w * 0.82f, h * 0.78f, roofPaint)

        // Minor streets
        val minorRoadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSatellite) Color.parseColor("#6E7B73") else Color.parseColor("#FFFFFF")
            strokeWidth = (w * 0.032f).coerceAtLeast(3f)
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, h * 0.25f, w, h * 0.25f, minorRoadPaint)
        canvas.drawLine(0f, h * 0.72f, w, h * 0.72f, minorRoadPaint)
        canvas.drawLine(w * 0.26f, 0f, w * 0.26f, h, minorRoadPaint)
        canvas.drawLine(w * 0.76f, 0f, w * 0.76f, h, minorRoadPaint)

        // Major arterial road + yellow highway route (Google Maps style)
        val roadCasingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSatellite) Color.parseColor("#94A3B8") else Color.parseColor("#D1D5DB")
            strokeWidth = (w * 0.075f).coerceAtLeast(7f)
            style = Paint.Style.STROKE
        }
        val roadFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSatellite) Color.parseColor("#CBD5E1") else Color.parseColor("#FDE68A")
            strokeWidth = (w * 0.055f).coerceAtLeast(5f)
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, h * 0.48f, w, h * 0.48f, roadCasingPaint)
        canvas.drawLine(w * 0.49f, 0f, w * 0.49f, h, roadCasingPaint)
        canvas.drawLine(0f, h * 0.48f, w, h * 0.48f, roadFillPaint)
        canvas.drawLine(w * 0.49f, 0f, w * 0.49f, h, roadFillPaint)

        return overlayMapPinAndBranding(bitmap)
    }

    private fun overlayMapPinAndBranding(baseBitmap: Bitmap): Bitmap {
        val working = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(working)
        val w = working.width.toFloat()
        val h = working.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // GPS accuracy halo around center pin
        val haloFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#284285F4")
            style = Paint.Style.FILL
        }
        val haloStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#884285F4")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(cx, cy, w * 0.16f, haloFill)
        canvas.drawCircle(cx, cy, w * 0.16f, haloStroke)

        // Pin drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55000000")
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(cx - w * 0.04f, cy - h * 0.01f, cx + w * 0.04f, cy + h * 0.025f),
            shadowPaint
        )

        // Signature Red Google-style Teardrop Location Pin
        val pinRadius = w * 0.085f
        val pinCenterY = cy - pinRadius * 1.45f
        val pinPath = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - pinRadius * 0.85f, pinCenterY + pinRadius * 0.35f)
            quadTo(cx, pinCenterY - pinRadius * 1.4f, cx + pinRadius * 0.85f, pinCenterY + pinRadius * 0.35f)
            close()
        }
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335")
            style = Paint.Style.FILL
        }
        val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = (w * 0.012f).coerceAtLeast(1.5f)
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(cx, pinCenterY, pinRadius, pinPaint)
        canvas.drawPath(pinPath, pinPaint)
        canvas.drawCircle(cx, pinCenterY, pinRadius, pinStrokePaint)

        // Dark red inner dot of pin
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E1913")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, pinCenterY, pinRadius * 0.38f, innerDotPaint)

        // Bottom-left "Google / GPS Map" watermark just like GPS Map Camera
        val brandShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AA000000")
            textSize = (w * 0.095f).coerceAtLeast(14f)
            isFakeBoldText = true
        }
        val brandTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * 0.095f).coerceAtLeast(14f)
            isFakeBoldText = true
        }
        canvas.drawText("Google", w * 0.06f + 1f, h - h * 0.06f + 1f, brandShadowPaint)
        canvas.drawText("Google", w * 0.06f, h - h * 0.06f, brandTextPaint)

        return working
    }

    private fun getTileNumber(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val x = Math.floor((lng + 180.0) / 360.0 * (1 shl zoom)).toInt()
        val latRad = Math.toRadians(lat)
        val y = Math.floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
        return Pair(x, y)
    }
}
