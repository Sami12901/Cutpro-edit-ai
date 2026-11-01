package com.cutpro.core.security.zip

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Adversarial stress harness targeting ZipSecurityValidator and directory traversal defenses.
 */
class AdversarialZipSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var destDir: File
    private lateinit var parentDir: File

    @Before
    fun setup() {
        parentDir = tempFolder.newFolder("safe_parent")
        destDir = File(parentDir, "dest_dir").apply { mkdirs() }
    }

    @Test
    fun testNestedDotDotPathTraversalPayloads() {
        val nestedPayloads = listOf(
            "../escaped.txt",
            "../../escaped.txt",
            "../../../etc/shadow",
            "sub/../../outside.txt",
            "a/b/c/../../../../escaped.txt",
            "dir/../..././../../escaped.txt",
            "media/sub/../../../../target.bin"
        )

        for (payload in nestedPayloads) {
            try {
                val targetFile = ZipSecurityValidator.validateEntryDestination(destDir, payload)
                // If it didn't throw, assert that the file cannot be outside destDir
                val canonicalDest = destDir.canonicalFile.path + File.separator
                if (!targetFile.canonicalPath.startsWith(canonicalDest)) {
                    fail("Payload '$payload' bypassed ZipSecurityValidator! Target: ${targetFile.canonicalPath}")
                }
                fail("Expected SecurityException for payload '$payload', but got: ${targetFile.canonicalPath}")
            } catch (e: SecurityException) {
                assertThat(e.message).contains("Zip-Slip")
            }
        }
    }

    @Test
    fun testWindowsBackslashPathTraversalPayloads() {
        val backslashPayloads = listOf(
            "..\\escaped.txt",
            "..\\..\\escaped.txt",
            "media\\..\\..\\escaped.txt",
            "sub\\nested\\..\\..\\..\\escaped.txt",
            "a\\..\\..\\b\\..\\..\\escaped.txt",
            "..\\..\\..\\Windows\\System32\\drivers\\etc\\hosts"
        )

        for (payload in backslashPayloads) {
            try {
                val targetFile = ZipSecurityValidator.validateEntryDestination(destDir, payload)
                val canonicalDest = destDir.canonicalFile.path + File.separator
                if (!targetFile.canonicalPath.startsWith(canonicalDest)) {
                    fail("Windows backslash payload '$payload' escaped destDir! Target: ${targetFile.canonicalPath}")
                }
                fail("Expected SecurityException for backslash payload '$payload', but got: ${targetFile.canonicalPath}")
            } catch (e: SecurityException) {
                assertThat(e.message).contains("Zip-Slip")
            }
        }
    }

    @Test
    fun testNullByteInjectionPayloads() {
        val nullBytePayloads = listOf(
            "valid_name.txt\u0000../../escaped.txt",
            "\u0000../escaped.txt",
            "media/asset.mp4\u0000/../../../evil.txt",
            "\u0000",
            "normal_file.txt\u0000"
        )

        for (payload in nullBytePayloads) {
            try {
                val target = ZipSecurityValidator.validateEntryDestination(destDir, payload)
                fail("Expected SecurityException for null byte payload, but returned: ${target.canonicalPath}")
            } catch (e: SecurityException) {
                assertThat(e.message).contains("Null byte injection detected")
            }
        }
    }

    @Test
    fun testPrefixCollisionAttacks() {
        // Destination directory is e.g. /tmp/dest_dir
        // Sibling attack target is /tmp/dest_dir_fake/malicious.txt
        val destDirName = destDir.name
        val prefixCollisions = listOf(
            "../${destDirName}_fake/malicious.txt",
            "../${destDirName}_evil/payload.bin",
            "../${destDirName}Extra/hack.sh",
            "../${destDirName}-suffix/test.txt"
        )

        for (payload in prefixCollisions) {
            try {
                val targetFile = ZipSecurityValidator.validateEntryDestination(destDir, payload)
                val canonicalDest = destDir.canonicalFile.path + File.separator
                if (!targetFile.canonicalPath.startsWith(canonicalDest)) {
                    fail("Prefix collision '$payload' escaped destDir! Target: ${targetFile.canonicalPath}")
                }
                fail("Expected SecurityException for prefix collision '$payload', but got: ${targetFile.canonicalPath}")
            } catch (e: SecurityException) {
                assertThat(e.message).contains("Zip-Slip")
            }
        }
    }

    @Test
    fun testUrlEncodedPathTraversalPayloads() {
        val urlEncodedPayloads = listOf(
            "%2e%2e%2fescaped.txt",
            "%2e%2e/escaped.txt",
            "..%2fescaped.txt",
            "%2e%2e%5cescaped.txt",
            "%252e%252e%252fescaped.txt",
            "media/%2e%2e/%2e%2e/escaped.txt"
        )

        for (payload in urlEncodedPayloads) {
            // Check behavior when evaluated directly
            val targetDirect = try {
                ZipSecurityValidator.validateEntryDestination(destDir, payload)
            } catch (e: SecurityException) {
                null
            }

            // Check behavior when URL decoded
            val decodedPayload = runCatching {
                URLDecoder.decode(payload, StandardCharsets.UTF_8.name())
            }.getOrDefault(payload)

            val targetDecoded = try {
                ZipSecurityValidator.validateEntryDestination(destDir, decodedPayload)
            } catch (e: SecurityException) {
                null
            }

            val canonicalDestPrefix = destDir.canonicalFile.path + File.separator
            // Assert that under NO interpretation does a file escape destDir
            if (targetDirect != null) {
                assertThat(targetDirect.canonicalPath.startsWith(canonicalDestPrefix)).isTrue()
            }
            if (targetDecoded != null) {
                assertThat(targetDecoded.canonicalPath.startsWith(canonicalDestPrefix)).isTrue()
            }
        }
    }

    @Test
    fun testZeroFilesEscapeVerificationUnderTraversal() {
        val payloads = listOf(
            "../pwned1.txt",
            "..\\pwned2.txt",
            "a/../../pwned3.txt",
            "foo/bar/../../../pwned4.txt"
        )

        for (payload in payloads) {
            try {
                val targetFile = ZipSecurityValidator.validateEntryDestination(destDir, payload)
                targetFile.writeText("malicious")
            } catch (ignored: SecurityException) {
                // Expected
            }
        }

        // Parent dir must NOT contain any escaped pwned files
        val parentFiles = parentDir.listFiles()?.filter { it != destDir } ?: emptyList()
        assertThat(parentFiles).isEmpty()
    }
}
