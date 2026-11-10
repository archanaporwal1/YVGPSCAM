package com.example

import com.example.service.LocationCompassManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

    @Test
    fun testCoordinatesDmsFormatting() {
        val latDms = LocationCompassManager.formatDms(37.774929, true)
        assertTrue(latDms.contains("37°"))
        assertTrue(latDms.endsWith("N"))

        val southDms = LocationCompassManager.formatDms(-33.8688, true)
        assertTrue(southDms.endsWith("S"))

        val lngDms = LocationCompassManager.formatDms(-122.419416, false)
        assertTrue(lngDms.contains("122°"))
        assertTrue(lngDms.endsWith("W"))

        val eastDms = LocationCompassManager.formatDms(151.2093, false)
        assertTrue(eastDms.endsWith("E"))
    }

    @Test
    fun testCoordinatesDecimalFormatting() {
        val latDec = LocationCompassManager.formatDecimal(37.774929, true)
        assertEquals("37.774929° N", latDec)

        val lngDec = LocationCompassManager.formatDecimal(-122.419416, false)
        assertEquals("122.419416° W", lngDec)
    }

    @Test
    fun testCompassCardinalDirections() {
        assertEquals("N", LocationCompassManager.getBearingDirection(0f))
        assertEquals("N", LocationCompassManager.getBearingDirection(359f))
        assertEquals("NE", LocationCompassManager.getBearingDirection(45f))
        assertEquals("E", LocationCompassManager.getBearingDirection(90f))
        assertEquals("SE", LocationCompassManager.getBearingDirection(135f))
        assertEquals("S", LocationCompassManager.getBearingDirection(180f))
        assertEquals("SW", LocationCompassManager.getBearingDirection(225f))
        assertEquals("W", LocationCompassManager.getBearingDirection(270f))
        assertEquals("NW", LocationCompassManager.getBearingDirection(315f))
    }

    @Test
    fun testZipArchivePackaging() {
        val tempZip = File.createTempFile("test_export", ".zip")
        ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
            zos.putNextEntry(ZipEntry("photos/sample.jpg"))
            zos.write("dummy photo".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("metadata_report.csv"))
            zos.write("Photo Name,Latitude,Longitude\nsample.jpg,37.7749,-122.4194".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("locations.kml"))
            zos.write("<kml></kml>".toByteArray())
            zos.closeEntry()
        }

        assertTrue(tempZip.exists())
        ZipFile(tempZip).use { zf ->
            assertNotNull(zf.getEntry("photos/sample.jpg"))
            assertNotNull(zf.getEntry("metadata_report.csv"))
            assertNotNull(zf.getEntry("locations.kml"))
        }
        tempZip.delete()
    }
}
