package com.abhilash.matinee.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SessionState { SignedOut, Guest, SignedIn }

// Only Stremio's auth key is kept, encrypted with a key that never leaves the Android Keystore. Never the password.
object Session {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(SessionState.SignedOut)
    val state: StateFlow<SessionState> = _state

    var authKey: String? = null
        private set
    val email: String? get() = prefs.getString("email", null)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        authKey = prefs.getString("authKey", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        _state.value = when {
            authKey != null -> SessionState.SignedIn
            prefs.getBoolean("guest", false) -> SessionState.Guest
            else -> SessionState.SignedOut
        }
    }

    fun signIn(account: Account) {
        authKey = account.authKey
        prefs.edit().putString("authKey", encrypt(account.authKey)).putString("email", account.email).putBoolean("guest", false).apply()
        _state.value = SessionState.SignedIn
    }

    fun browseAsGuest() {
        prefs.edit().putBoolean("guest", true).apply()
        _state.value = SessionState.Guest
    }

    fun signOut() {
        authKey = null
        prefs.edit().clear().apply()
        _state.value = SessionState.SignedOut
    }

    private const val KEY_ALIAS = "matinee-session"
    private const val IV_BYTES = 12

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }
}

object Settings {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var matchFrameRate: Boolean
        get() = prefs.getBoolean("matchFrameRate", true)
        set(v) = prefs.edit().putBoolean("matchFrameRate", v).apply()

    var tunneling: Boolean
        get() = prefs.getBoolean("tunneling", false)
        set(v) = prefs.edit().putBoolean("tunneling", v).apply()

    // ISO 639 code, or null for "the stream's default"
    var audioLanguage: String?
        get() = prefs.getString("audioLanguage", null)
        set(v) = prefs.edit().putString("audioLanguage", v).apply()

    // ISO 639 code, or null for "off"
    var subtitleLanguage: String?
        get() = prefs.getString("subtitleLanguage", null)
        set(v) = prefs.edit().putString("subtitleLanguage", v).apply()

    var subtitleScale: Float
        get() = prefs.getFloat("subtitleScale", 1f)
        set(v) = prefs.edit().putFloat("subtitleScale", v).apply()

    // Audio outputs where passthrough failed or was silent, so Matinee decodes to PCM there
    fun forcePcm(output: String) = output in prefs.getStringSet("forcePcm", emptySet())!!

    fun setForcePcm(output: String, force: Boolean) {
        val set = prefs.getStringSet("forcePcm", emptySet())!!.toMutableSet()
        if (force) set += output else set -= output
        prefs.edit().putStringSet("forcePcm", set).apply()
    }

    fun clearForcePcm() = prefs.edit().remove("forcePcm").apply()
}
