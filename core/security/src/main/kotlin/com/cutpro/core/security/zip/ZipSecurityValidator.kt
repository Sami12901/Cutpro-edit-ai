package com.cutpro.core.security.zip

import java.io.File

object ZipSecurityValidator {

    const val MAX_ENTRIES = 10_000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 50L * 1024 * 1024 * 1024 // 50 GB safety ceiling

    /**
     * Validates that an archive entry target path stays strictly inside the canonical destination directory.
     * Throws SecurityException immediately if directory traversal is attempted.
     *
     * @param destinationDir The destination directory where the file will be extracted.
     * @param entryName The relative name of the ZIP entry.
     * @return The validated target File.
     */
    fun validateEntryDestination(destinationDir: File, entryName: String): File {
        if (entryName.contains('\u0000')) {
            throw SecurityException("Null byte injection detected in entry name: '$entryName'")
        }

        val canonicalDestDir = destinationDir.canonicalFile
        val canonicalDestPrefix = if (canonicalDestDir.path.endsWith(File.separator)) {
            canonicalDestDir.path
        } else {
            canonicalDestDir.path + File.separator
        }

        val targetFile = File(destinationDir, entryName).canonicalFile
        val targetPath = targetFile.path

        if (!targetPath.startsWith(canonicalDestPrefix)) {
            throw SecurityException(
                "Zip-Slip vulnerability detected! Entry '$entryName' traverses outside destination directory '$canonicalDestDir'."
            )
        }

        return targetFile
    }

    /**
     * Checks if extracting the entry would violate Zip-Bomb safety thresholds.
     */
    fun validateResourceLimits(entryIndex: Int, totalBytesExtracted: Long) {
        if (entryIndex > MAX_ENTRIES) {
            throw SecurityException("Zip bomb defense: Archive contains more than $MAX_ENTRIES entries.")
        }
        if (totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw SecurityException(
                "Zip bomb defense: Total uncompressed bytes ($totalBytesExtracted) exceeds limit of $MAX_TOTAL_UNCOMPRESSED_BYTES bytes."
            )
        }
    }
}
