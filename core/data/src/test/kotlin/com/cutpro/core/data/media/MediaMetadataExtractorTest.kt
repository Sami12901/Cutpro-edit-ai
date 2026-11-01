package com.cutpro.core.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import com.cutpro.core.security.storage.UriPermissionManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaMetadataExtractorTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var extractor: MediaMetadataExtractor
    private lateinit var permissionManager: UriPermissionManager
    private lateinit var importManager: MediaImportManager

    @Before
    fun setup() {
        context = mock(Context::class.java)
        contentResolver = mock(ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(contentResolver)
        extractor = MediaMetadataExtractor(context)
        permissionManager = mock(UriPermissionManager::class.java)
        importManager = MediaImportManager(context, permissionManager, extractor)
    }

    @Test
    fun extractMetadata_rejectsZeroByteFileWithCorruptedMediaException(): Unit = runBlocking {
        val testUri = Uri.parse("content://media/test_zero.mp4")
        val mockAfd = mock(AssetFileDescriptor::class.java)
        `when`(mockAfd.length).thenReturn(0L)
        `when`(contentResolver.openAssetFileDescriptor(testUri, "r")).thenReturn(mockAfd)

        val result = extractor.extractMetadata(testUri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(CorruptedMediaException::class.java)
    }

    @Test
    fun extractMetadata_rejectsUnsupportedFormat(): Unit = runBlocking {
        val testUri = Uri.parse("content://media/unsupported.xyz")
        val mockAfd = mock(AssetFileDescriptor::class.java)
        `when`(mockAfd.length).thenReturn(1024L)
        `when`(contentResolver.openAssetFileDescriptor(testUri, "r")).thenReturn(mockAfd)
        `when`(contentResolver.getType(testUri)).thenReturn("image/xyz-unknown")

        val result = extractor.extractMetadata(testUri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(UnsupportedMediaFormatException::class.java)
    }
}
