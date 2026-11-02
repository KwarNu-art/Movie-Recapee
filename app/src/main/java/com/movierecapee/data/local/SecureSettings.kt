package com.movierecapee.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.movierecapee.domain.AudioMode
import com.movierecapee.domain.TtsEngine
import com.movierecapee.domain.VoiceSettings

/**
 * API keys and preferences, encrypted on the phone with a key from the Android Keystore.
 * Everything is written immediately (commit) so nothing is lost when the app is closed.
 */
class SecureSettings(context: Context) {
    private val prefs: SharedPreferences = open(context.applicationContext)

    private fun open(ctx: Context): SharedPreferences = try {
        create(ctx)
    } catch (e: Exception) {
        // The Keystore key was lost (for example after a restore). Start a clean encrypted file.
        ctx.deleteSharedPreferences(FILE)
        create(ctx)
    }

    private fun create(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun str(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun put(key: String, v: String) { prefs.edit().putString(key, v).commit() }

    var groqKey: String
        get() = str("groq_key", "")
        set(v) = put("groq_key", v.trim())
    var geminiKey: String
        get() = str("gemini_key", "")
        set(v) = put("gemini_key", v.trim())
    var groqAsrModel: String
        get() = str("groq_asr", DEFAULT_GROQ_ASR).ifBlank { DEFAULT_GROQ_ASR }
        set(v) = put("groq_asr", v.trim())
    var groqChatModel: String
        get() = str("groq_chat", DEFAULT_GROQ_CHAT).ifBlank { DEFAULT_GROQ_CHAT }
        set(v) = put("groq_chat", v.trim())
    var geminiModel: String
        get() = str("gemini_model", DEFAULT_GEMINI).ifBlank { DEFAULT_GEMINI }
        set(v) = put("gemini_model", v.trim())
    var geminiTtsModel: String
        get() = str("gemini_tts", DEFAULT_GEMINI_TTS).ifBlank { DEFAULT_GEMINI_TTS }
        set(v) = put("gemini_tts", v.trim())
    var geminiVoice: String
        get() = str("gemini_voice", DEFAULT_VOICE).ifBlank { DEFAULT_VOICE }
        set(v) = put("gemini_voice", v.trim())

    var voice: VoiceSettings
        get() = VoiceSettings(
            engine = runCatching { TtsEngine.valueOf(str("v_engine", TtsEngine.DEVICE.name)) }.getOrDefault(TtsEngine.DEVICE),
            speechRate = prefs.getFloat("v_rate", 1.0f),
            pitch = prefs.getFloat("v_pitch", 1.0f),
            audioMode = runCatching { AudioMode.valueOf(str("v_mode", AudioMode.REPLACE.name)) }.getOrDefault(AudioMode.REPLACE),
            originalVolume = prefs.getFloat("v_orig", 0.25f),
            burnCaptions = prefs.getBoolean("v_burn", true),
            outHeight = prefs.getInt("v_height", 720)
        )
        set(v) {
            prefs.edit()
                .putString("v_engine", v.engine.name).putFloat("v_rate", v.speechRate).putFloat("v_pitch", v.pitch)
                .putString("v_mode", v.audioMode.name).putFloat("v_orig", v.originalVolume)
                .putBoolean("v_burn", v.burnCaptions).putInt("v_height", v.outHeight).commit()
        }

    fun resetModels() {
        groqAsrModel = DEFAULT_GROQ_ASR; groqChatModel = DEFAULT_GROQ_CHAT
        geminiModel = DEFAULT_GEMINI; geminiTtsModel = DEFAULT_GEMINI_TTS; geminiVoice = DEFAULT_VOICE
    }

    companion object {
        private const val FILE = "movie_recapee_secure"
        const val DEFAULT_GROQ_ASR = "whisper-large-v3-turbo"
        const val DEFAULT_GROQ_CHAT = "llama-3.3-70b-versatile"
        const val DEFAULT_GEMINI = "gemini-2.5-flash"
        const val DEFAULT_GEMINI_TTS = "gemini-2.5-flash-preview-tts"
        const val DEFAULT_VOICE = "Kore"
    }
}
