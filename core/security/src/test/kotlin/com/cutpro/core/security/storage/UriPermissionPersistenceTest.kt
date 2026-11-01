package com.cutpro.core.security.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UriPermissionPersistenceTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var manager: UriPermissionManager

    @Before
    fun setup() {
        context = mock(Context::class.java)
        contentResolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(contentResolver)
        manager = UriPermissionManager(context)
    }

    @Test
    fun takePersistableReadPermission_callsTakePersistableUriPermissionWithReadFlag() {
        val contentUri = Uri.parse("content://com.android.providers.media.documents/document/video%3A12")
        val success = manager.takePersistableReadPermission(contentUri)

        assertTrue(success)
        verify(contentResolver).takePersistableUriPermission(
            eq(contentUri),
            eq(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    @Test
    fun takePersistableReadPermission_returnsFalseOnSecurityExceptionForTransientPickerUris() {
        val pickerUri = Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/100")
        doThrow(SecurityException("No persistable permission grants found"))
            .`when`(contentResolver)
            .takePersistableUriPermission(eq(pickerUri), eq(Intent.FLAG_GRANT_READ_URI_PERMISSION))

        val success = manager.takePersistableReadPermission(pickerUri)
        assertFalse(success)
    }

    @Test
    fun takePersistableReadPermission_returnsTrueForFileUris() {
        val fileUri = Uri.parse("file:///data/user/0/app/file.mp4")
        val success = manager.takePersistableReadPermission(fileUri)
        assertTrue(success)
    }

    @Test
    fun releasePersistableReadPermission_callsReleaseOnResolver() {
        val contentUri = Uri.parse("content://media/external/video/media/1")
        manager.releasePersistableReadPermission(contentUri)
        verify(contentResolver).releasePersistableUriPermission(
            eq(contentUri),
            eq(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
