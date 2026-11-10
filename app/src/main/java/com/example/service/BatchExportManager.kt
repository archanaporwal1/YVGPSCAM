package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.PhotoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BatchExportManager(private val context: Context) {

    private val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }

    suspend fun createBatchZip(
        photos: List<PhotoRecord>,
        onProgress: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(exportDir, "YVGSPCAM_Export_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val total = photos.size.coerceAtLeast(1)

            // 1. Add all photo files
            photos.forEachIndexed { index, photo ->
                val sourceFile = File(photo.filePath)
                if (sourceFile.exists()) {
                    val entryName = "photos/${sourceFile.name}"
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(sourceFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
                onProgress?.invoke((index + 1).toFloat() / (total + 2))
            }

            // 2. Generate and add metadata_report.csv
            val csvContent = buildCsvReport(photos)
            zos.putNextEntry(ZipEntry("metadata_report.csv"))
            zos.write(csvContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            onProgress?.invoke((total + 1).toFloat() / (total + 2))

            // 3. Generate and add Google Earth locations.kml
            val kmlContent = buildKmlReport(photos)
            zos.putNextEntry(ZipEntry("locations.kml"))
            zos.write(kmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            onProgress?.invoke(1.0f)
        }

        zipFile
    }

    private fun buildCsvReport(photos: List<PhotoRecord>): String {
        val sb = StringBuilder()
        sb.append("Photo Name,Timestamp,Date Time,Latitude,Longitude,Altitude (m),Compass Azimuth,Accuracy (m),Address,Project Tag,Notes,Template,File Size (bytes)\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        photos.forEach { p ->
            val fileName = File(p.filePath).name
            val dateStr = dateFormat.format(Date(p.timestamp))
            val safeAddress = "\"${p.address.replace("\"", "\"\"")}\""
            val safeNotes = "\"${p.inspectorNotes.replace("\"", "\"\"")}\""
            val safeProject = "\"${p.projectTag.replace("\"", "\"\"")}\""
            sb.append("$fileName,${p.timestamp},$dateStr,${p.latitude},${p.longitude},${p.altitude},${p.bearing},${p.accuracy},$safeAddress,$safeProject,$safeNotes,${p.templateId},${p.fileSizeBytes}\n")
        }
        return sb.toString()
    }

    private fun buildKmlReport(photos: List<PhotoRecord>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>YVGSPCAM Fieldwork Export</name>\n")
        sb.append("    <description>Geotagged photos collected with YVGSPCAM</description>\n")
        photos.forEach { p ->
            val fileName = File(p.filePath).name
            sb.append("    <Placemark>\n")
            sb.append("      <name>${p.projectTag.ifBlank { fileName }}</name>\n")
            sb.append("      <description>File: $fileName\nAddress: ${p.address}\nNotes: ${p.inspectorNotes}\nAlt: ${p.altitude}m</description>\n")
            sb.append("      <Point>\n")
            sb.append("        <coordinates>${p.longitude},${p.latitude},${p.altitude}</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }
        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    fun shareZipToCloud(zipFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "YVGSPCAM Batch Export: ${zipFile.name}")
            putExtra(
                Intent.EXTRA_TEXT,
                "Backup package from YVGSPCAM containing geotagged photos, CSV audit log, and KML coordinates."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export to Cloud Services / Drive")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun sharePhotosDirect(photos: List<PhotoRecord>) {
        if (photos.isEmpty()) return
        val uris = ArrayList<Uri>()
        photos.forEach { p ->
            val f = File(p.filePath)
            if (f.exists()) {
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
            }
        }
        if (uris.isEmpty()) return

        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${photos.size} Photos via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
