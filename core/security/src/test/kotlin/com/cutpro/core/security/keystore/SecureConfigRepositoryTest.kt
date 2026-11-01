package com.cutpro.core.security.keystore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureConfigRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SecureConfigRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = AndroidSecureConfigRepository(prefs)
    }

    @Test
    fun testStringStorage() {
        assertThat(repository.getString("test_key")).isNull()
        repository.putString("test_key", "secret_value")
        assertThat(repository.getString("test_key")).isEqualTo("secret_value")
        assertThat(repository.contains("test_key")).isTrue()

        repository.remove("test_key")
        assertThat(repository.getString("test_key")).isNull()
    }

    @Test
    fun testBooleanStorage() {
        assertThat(repository.getBoolean("flag", defaultValue = false)).isFalse()
        repository.putBoolean("flag", true)
        assertThat(repository.getBoolean("flag", defaultValue = false)).isTrue()
    }

    @Test
    fun testNumericStorage() {
        repository.putLong("timestamp", 123456789L)
        assertThat(repository.getLong("timestamp")).isEqualTo(123456789L)

        repository.putInt("count", 42)
        assertThat(repository.getInt("count")).isEqualTo(42)
    }

    @Test
    fun testClear() {
        repository.putString("k1", "v1")
        repository.putInt("k2", 2)
        repository.clear()
        assertThat(repository.contains("k1")).isFalse()
        assertThat(repository.contains("k2")).isFalse()
    }
}
