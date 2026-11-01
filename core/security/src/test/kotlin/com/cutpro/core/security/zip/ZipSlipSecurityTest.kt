package com.cutpro.core.security.zip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipSlipSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var destDir: File
    private lateinit var outsideFile: File

    @Before
    fun setup() {
        destDir = tempFolder.newFolder("safe_destination")
        outsideFile = File(tempFolder.root, "escaped_file.txt")
    }

    @Test
    fun validateEntryDestination_allowsValidEntriesWithinDestination() {
        val normalEntry = "media/video1.mp4"
        val target = ZipSecurityValidator.validateEntryDestination(destDir, normalEntry)
        assertTrue(target.canonicalPath.startsWith(destDir.canonicalPath))
    }

    @Test
    fun validateEntryDestination_throwsSecurityExceptionOnDotDotParentTraversal() {
        val maliciousEntry = "../escaped_file.txt"
        try {
            ZipSecurityValidator.validateEntryDestination(destDir, maliciousEntry)
            fail("Expected SecurityException on directory traversal attempt")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Zip-Slip vulnerability detected"))
        }
    }

    @Test
    fun validateEntryDestination_throwsSecurityExceptionOnDeepNestedTraversal() {
        val maliciousEntry = "media/../../../../system_file.txt"
        try {
            ZipSecurityValidator.validateEntryDestination(destDir, maliciousEntry)
            fail("Expected SecurityException on deep directory traversal attempt")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Zip-Slip vulnerability detected"))
        }
    }

    @Test
    fun validateEntryDestination_preventsPrefixMatchExploit() {
        // Case: destDir is safe_destination
        // attacker tries safe_destination_evil/pwned.txt
        val maliciousEntry = "../safe_destination_evil/pwned.txt"
        try {
            ZipSecurityValidator.validateEntryDestination(destDir, maliciousEntry)
            fail("Expected SecurityException on prefix match exploit")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Zip-Slip vulnerability detected"))
        }
    }

    @Test
    fun validateEntryDestination_throwsSecurityExceptionOnNullByteInjection() {
        val nullByteEntry = "valid_name.txt\u0000../../etc/passwd"
        try {
            ZipSecurityValidator.validateEntryDestination(destDir, nullByteEntry)
            fail("Expected SecurityException on null byte entry")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Null byte injection detected"))
        }
    }

    @Test
    fun validateResourceLimits_detectsEntryLimitAndByteLimit() {
        // Valid bounds
        ZipSecurityValidator.validateResourceLimits(100, 1024L)

        // Exceed entry count
        try {
            ZipSecurityValidator.validateResourceLimits(10_001, 100L)
            fail("Expected SecurityException on excessive entries")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Zip bomb defense"))
        }

        // Exceed byte limit (50 GB)
        try {
            ZipSecurityValidator.validateResourceLimits(5, 51L * 1024 * 1024 * 1024)
            fail("Expected SecurityException on excessive uncompressed bytes")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Zip bomb defense"))
        }
    }
}
