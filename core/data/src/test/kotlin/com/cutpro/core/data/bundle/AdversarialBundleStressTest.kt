package com.cutpro.core.data.bundle

import com.cutpro.core.security.zip.ZipSecurityValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AdversarialBundleStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var unpackager: CutproBundleUnpackager
    private lateinit var destDir: File

    @Before
    fun setup() {
        unpackager = CutproBundleUnpackager()
        destDir = tempFolder.newFolder("adversarial_unpack_dest")
    }

    @Test
    fun testZipBombExcessiveEntriesDefense() = runBlocking {
        // Construct a ZIP bomb containing 10,005 empty entries
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Manifest first
            zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
            zos.write("{\"version\": 1}".toByteArray())
            zos.closeEntry()

            // 10,005 entries to exceed MAX_ENTRIES (10,000)
            for (i in 1..10_005) {
                zos.putNextEntry(ZipEntry("${BundleArchiveLayout.MEDIA_DIR}/entry_$i.dat"))
                zos.closeEntry()
            }
        }

        val zipBytes = baos.toByteArray()
        val inputStream = ByteArrayInputStream(zipBytes)

        try {
            unpackager.unpackBundle(inputStream, destDir)
            fail("Expected SecurityException due to Zip bomb entry threshold (>10,000 entries)")
        } catch (e: SecurityException) {
            assertThat(e.message).contains("Zip bomb defense: Archive contains more than 10000 entries")
        }

        // Verify total cleanup of destination directory
        assertFalse("Destination directory must be wiped on security violation", destDir.exists())
    }

    @Test
    fun testZipBombByteCeilingThresholdDirectValidation() {
        // Assert resource limits under standard conditions
        ZipSecurityValidator.validateResourceLimits(1, 1000L)
        ZipSecurityValidator.validateResourceLimits(10_000, 50L * 1024 * 1024 * 1024)

        // Exceed byte limit: 50GB + 1 byte
        assertThrows(SecurityException::class.java) {
            ZipSecurityValidator.validateResourceLimits(1, 50L * 1024 * 1024 * 1024 + 1L)
        }

        // Exceed entry count: 10,001
        assertThrows(SecurityException::class.java) {
            ZipSecurityValidator.validateResourceLimits(10_001, 0L)
        }
    }

    @Test
    fun testSingleEntryZipBombAbortsStreamingLoop() = runBlocking {
        // Build a ZIP with 1 entry of 5000 bytes, but initialize unpackager with 1000 bytes limit
        val smallLimitUnpackager = CutproBundleUnpackager(maxUncompressedBytes = 1000L)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
            zos.write(ByteArray(5000) { 'a'.code.toByte() })
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        val customDest = tempFolder.newFolder("stream_ceiling_dest")

        try {
            smallLimitUnpackager.unpackBundle(ByteArrayInputStream(zipBytes), customDest)
            fail("Expected SecurityException due to byte extraction limit in read loop")
        } catch (e: SecurityException) {
            assertThat(e.message).contains("Bundle extracted bytes exceeded ceiling limit")
        }

        assertFalse("Destination must be wiped after stream ceiling breach", customDest.exists())
    }

    @Test
    fun testCorruptedAndTruncatedArchiveHandling() = runBlocking {
        // 1. Completely random / corrupted non-zip data
        val randomBytes = ByteArray(1024) { 0xFF.toByte() }
        try {
            unpackager.unpackBundle(ByteArrayInputStream(randomBytes), destDir)
            fail("Expected failure on corrupted archive")
        } catch (e: Throwable) {
            // Expected
            assertFalse(destDir.exists())
        }

        // 2. Truncated ZIP stream (valid header, cut in the middle)
        val validBaos = ByteArrayOutputStream()
        ZipOutputStream(validBaos).use { zos ->
            zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
            zos.write("{\"version\": 1}".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("${BundleArchiveLayout.MEDIA_DIR}/clip.mp4"))
            zos.write(ByteArray(5000) { 1.toByte() })
            zos.closeEntry()
        }
        val validBytes = validBaos.toByteArray()
        val truncatedBytes = validBytes.copyOf(validBytes.size / 2)

        val freshDestDir = tempFolder.newFolder("dest_truncated")
        try {
            unpackager.unpackBundle(ByteArrayInputStream(truncatedBytes), freshDestDir)
            fail("Expected failure on truncated archive")
        } catch (e: Throwable) {
            // Must clean up partially unpacked files
            assertFalse(freshDestDir.exists())
        }
    }

    @Test
    fun testMissingOrEmptyManifestRejection() = runBlocking {
        // Archive with media but no manifest
        val baosNoManifest = ByteArrayOutputStream()
        ZipOutputStream(baosNoManifest).use { zos ->
            zos.putNextEntry(ZipEntry("${BundleArchiveLayout.MEDIA_DIR}/clip.mp4"))
            zos.write("data".toByteArray())
            zos.closeEntry()
        }

        val dest1 = tempFolder.newFolder("dest_no_manifest")
        try {
            unpackager.unpackBundle(ByteArrayInputStream(baosNoManifest.toByteArray()), dest1)
            fail("Expected CorruptedBundleException for missing manifest")
        } catch (e: CorruptedBundleException) {
            assertThat(e.message).contains("Missing or empty 'project.cutproproj'")
            assertFalse(dest1.exists())
        }

        // Archive with empty 0-byte manifest
        val baosEmptyManifest = ByteArrayOutputStream()
        ZipOutputStream(baosEmptyManifest).use { zos ->
            zos.putNextEntry(ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME))
            zos.closeEntry() // 0 bytes
            zos.putNextEntry(ZipEntry("${BundleArchiveLayout.MEDIA_DIR}/clip.mp4"))
            zos.write("data".toByteArray())
            zos.closeEntry()
        }

        val dest2 = tempFolder.newFolder("dest_empty_manifest")
        try {
            unpackager.unpackBundle(ByteArrayInputStream(baosEmptyManifest.toByteArray()), dest2)
            fail("Expected CorruptedBundleException for 0-byte manifest")
        } catch (e: CorruptedBundleException) {
            assertThat(e.message).contains("Missing or empty 'project.cutproproj'")
            assertFalse(dest2.exists())
        }
    }
}
