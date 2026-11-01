package com.cutpro.core.data.bundle

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CutproBundleRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var unpackager: CutproBundleUnpackager
    private lateinit var unpackDestDir: File

    @Before
    fun setup() {
        unpackager = CutproBundleUnpackager()
        unpackDestDir = tempFolder.newFolder("unpacked_project")
    }

    @Test
    fun unpackBundle_successfullyUnpacksValidBundleWithManifestAndMedia() = runBlocking {
        val zipStream = createValidBundleZip(
            manifestContent = "{\"version\":1,\"projectId\":\"proj-123\"}",
            mediaFiles = mapOf("clip1.mp4" to "fake-video-bytes".toByteArray())
        )

        val result = unpackager.unpackBundle(zipStream, unpackDestDir)
        assertTrue(result.manifestFile.exists())
        assertEquals("{\"version\":1,\"projectId\":\"proj-123\"}", result.manifestFile.readText())
        assertEquals(1, result.extractedMediaFiles.size)
        assertEquals("clip1.mp4", result.extractedMediaFiles[0].name)
    }

    @Test
    fun unpackBundle_throwsCorruptedBundleExceptionWhenManifestIsMissing() = runBlocking {
        val zipStream = createValidBundleZip(
            manifestContent = null,
            mediaFiles = mapOf("clip1.mp4" to "fake-video-bytes".toByteArray())
        )

        try {
            unpackager.unpackBundle(zipStream, unpackDestDir)
            fail("Expected CorruptedBundleException")
        } catch (e: CorruptedBundleException) {
            assertTrue(e.message!!.contains("Missing or empty 'project.cutproproj'"))
        }
    }

    @Test
    fun unpackBundle_abortsAndCleansUpDestinationDirectoryOnZipSlipAttack() = runBlocking {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
            zos.write("valid-manifest".toByteArray())
            zos.closeEntry()

            // Malicious entry attempting path traversal
            zos.putNextEntry(ZipEntry("../outside_evil.txt"))
            zos.write("malicious-code".toByteArray())
            zos.closeEntry()
        }

        try {
            unpackager.unpackBundle(ByteArrayInputStream(baos.toByteArray()), unpackDestDir)
            fail("Expected SecurityException")
        } catch (e: SecurityException) {
            // Verify all unpacked files in destination were wiped clean
            assertFalse(unpackDestDir.exists())
            assertFalse(File(tempFolder.root, "outside_evil.txt").exists())
        }
    }

    private fun createValidBundleZip(manifestContent: String?, mediaFiles: Map<String, ByteArray>): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (manifestContent != null) {
                zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
                zos.write(manifestContent.toByteArray())
                zos.closeEntry()
            }
            for ((name, bytes) in mediaFiles) {
                zos.putNextEntry(ZipEntry("${BundleArchiveLayout.MEDIA_DIR}/$name"))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }
}
