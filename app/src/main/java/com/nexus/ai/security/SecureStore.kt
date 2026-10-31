package com.nexus.ai.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keystore-backed encrypted storage: API key and user settings. */
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
    fun model(): String = prefs.getString("model", "") ?: ""
    fun saveModel(m: String) = prefs.edit().putString("model", m.trim()).apply()
    fun userName(): String = prefs.getString("name", "") ?: ""
    fun saveName(n: String) = prefs.edit().putString("name", n.trim()).apply()
    fun voice(): String = prefs.getString("voice", "Aoede") ?: "Aoede"
    fun saveVoice(v: String) = prefs.edit().putString("voice", v).apply()
    fun angel(): Int = prefs.getInt("angel", 70)
    fun saveAngel(a: Int) = prefs.edit().putInt("angel", a.coerceIn(0, 100)).apply()
}
