package com.cutpro.core.security.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

class UriPermissionManager(private val context: Context) {

    /**
     * Attempts to acquire persistable read permission for a content URI across device reboots.
     * Returns true if permission was successfully persisted; returns false if URI does not support persistable grants.
     */
    fun takePersistableReadPermission(uri: Uri): Boolean {
        // file:// or android.resource:// URIs do not require persistable content grants
        if (uri.scheme != "content") {
            return true
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (e: SecurityException) {
            // Transient Photo Picker URIs or third-party providers without persistable grants
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Releases a previously persisted read permission when a project is deleted.
     */
    fun releasePersistableReadPermission(uri: Uri) {
        if (uri.scheme != "content") return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Ignored on cleanup
        }
    }

    /**
     * Verifies that the URI is readable right now.
     */
    fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
