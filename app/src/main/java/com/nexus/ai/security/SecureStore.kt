package com.nexus.ai.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed encrypted storage for the user's own API key. */
class SecureStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "nexus_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun apiKey(): String? = prefs.getString("gemini_key", null)
    fun saveApiKey(k: String) = prefs.edit().putString("gemini_key", k.trim()).apply()
    fun clearApiKey() = prefs.edit().remove("gemini_key").apply()
}
