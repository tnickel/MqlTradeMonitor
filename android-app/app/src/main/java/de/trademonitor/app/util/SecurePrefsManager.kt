package de.trademonitor.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecurePrefsManager {
    private const val TAG = "SecurePrefsManager"
    private const val PREFS_NAME = "TradeMonitorPrefs"
    private const val SECURE_PREFS_NAME = "TradeMonitorPrefsSecure"

    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_REMEMBER = "remember_credentials"
    private const val DEFAULT_SERVER_URL = "https://monitor.tnickel-ki.de"

    private fun getStandardPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences, resetting secure prefs", e)
            try {
                // Recover from corrupted key state
                context.deleteSharedPreferences(SECURE_PREFS_NAME)
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback to standard SharedPreferences for credentials", e2)
                context.getSharedPreferences("${PREFS_NAME}_Fallback", Context.MODE_PRIVATE)
            }
        }
    }

    fun getServerUrl(context: Context): String {
        val prefs = getStandardPrefs(context)
        val saved = prefs.getString(KEY_SERVER_URL, "") ?: ""
        return if (saved.isEmpty()) DEFAULT_SERVER_URL else saved
    }

    fun saveServerUrl(context: Context, url: String) {
        val formatted = if (url.isNotBlank() && !url.endsWith("/")) "$url/" else url
        getStandardPrefs(context).edit().putString(KEY_SERVER_URL, formatted).apply()
    }

    fun isRememberEnabled(context: Context): Boolean {
        return getStandardPrefs(context).getBoolean(KEY_REMEMBER, true)
    }

    fun setRememberEnabled(context: Context, remember: Boolean) {
        getStandardPrefs(context).edit().putBoolean(KEY_REMEMBER, remember).apply()
        if (!remember) {
            clearSavedCredentials(context)
        }
    }

    fun getCredentials(context: Context): Pair<String, String> {
        val securePrefs = getSecurePrefs(context)
        val username = securePrefs.getString(KEY_USERNAME, "") ?: ""
        val password = securePrefs.getString(KEY_PASSWORD, "") ?: ""
        return Pair(username, password)
    }

    fun saveCredentials(context: Context, username: String, password: String, remember: Boolean) {
        setRememberEnabled(context, remember)
        if (remember) {
            getSecurePrefs(context).edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply()
        } else {
            clearSavedCredentials(context)
        }
    }

    fun clearSavedCredentials(context: Context) {
        try {
            getSecurePrefs(context).edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing saved credentials", e)
        }
    }
}
