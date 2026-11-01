package com.cutpro.core.security.keystore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

object SecurePreferencesProvider {

    private const val PREFS_FILE_NAME = "cutpro_secure_prefs"
    private const val MASTER_KEY_ALIAS = "_cutpro_master_key_"
    private const val TAG = "SecurePrefsProvider"

    /**
     * Creates or recovers an EncryptedSharedPreferences instance using an AES-256-GCM MasterKey.
     * Automatically handles corrupted Keystore states (e.g. post-OTA upgrade key invalidation).
     */
    fun create(context: Context): SharedPreferences {
        return try {
            instantiateEncryptedPrefs(context)
        } catch (e: Exception) {
            when (e) {
                is GeneralSecurityException, is IOException -> {
                    Log.w(TAG, "Keystore key invalidation or corruption detected. Purging and recovering.", e)
                    recoverFromCorruption(context)
                    instantiateEncryptedPrefs(context)
                }
                else -> throw e
            }
        }
    }

    private fun instantiateEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun recoverFromCorruption(context: Context) {
        // 1. Delete XML preference file
        try {
            val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$PREFS_FILE_NAME.xml")
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to delete corrupted prefs file", t)
        }

        // 2. Remove invalidated alias from AndroidKeyStore
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to remove alias from AndroidKeyStore", t)
        }
    }
}
