package com.movierecapee.data.repo

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.media.DubMixer
import com.movierecapee.data.media.PcmClip
import com.movierecapee.data.media.WavUtil
import com.movierecapee.data.remote.GContent
import com.movierecapee.data.remote.GPart
import com.movierecapee.data.remote.GenConfig
import com.movierecapee.data.remote.GeminiApi
import com.movierecapee.data.remote.GeminiRequest
import com.movierecapee.data.remote.PrebuiltVoice
import com.movierecapee.data.remote.SpeechConfig
import com.movierecapee.data.remote.VoiceConfig
import com.movierecapee.domain.Lang
import com.movierecapee.domain.TtsEngine
import com.movierecapee.util.userMessage
import com.movierecapee.util.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Text to speech. The phone's own voice works offline and is free; Gemini voice is the online fallback. */
class TtsRepository(
    private val context: Context,
    private val settings: SecureSettings,
    private val gemini: GeminiApi
) {
    private var engine: TextToSpeech? = null
    private val dir = File(context.cacheDir, "tts").apply { mkdirs() }

    suspend fun synthesize(text: String, lang: Lang): PcmClip = withContext(Dispatchers.IO) {
        val voice = settings.voice
        val order = if (voice.engine == TtsEngine.GEMINI) listOf(TtsEngine.GEMINI, TtsEngine.DEVICE) else listOf(TtsEngine.DEVICE, TtsEngine.GEMINI)
        val errors = ArrayList<String>()
        for (e in order) {
            if (e == TtsEngine.GEMINI && settings.geminiKey.isBlank()) continue
            try {
                return@withContext if (e == TtsEngine.DEVICE) viaDevice(text, lang, voice.speechRate, voice.pitch) else viaGemini(text)
            } catch (c: CancellationException) {
                throw c
            } catch (ex: Exception) {
                errors += "${e.label}: ${ex.userMessage()}"
            }
        }
        throw IOException(errors.joinToString("  |  ").ifBlank { "No voice engine is available." })
    }

    fun release() {
        engine?.shutdown()
        engine = null
    }

    private suspend fun ensureEngine(): TextToSpeech {
        engine?.let { return it }
        val created = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<TextToSpeech> { cont ->
                var holder: TextToSpeech? = null
                holder = TextToSpeech(context.applicationContext) { status ->
                    val t = holder
                    if (!cont.isActive) return@TextToSpeech
                    if (status == TextToSpeech.SUCCESS && t != null) cont.resume(t)
                    else cont.resumeWithException(IOException("The phone's text-to-speech engine could not start."))
                }
            }
        }
        engine = created
        return created
    }

    private suspend fun viaDevice(text: String, lang: Lang, rate: Float, pitch: Float): PcmClip {
        val tts = ensureEngine()
        val r = tts.setLanguage(lang.locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw IOException("No ${lang.label} voice on this phone. Install it in Settings > Text-to-speech (Google Speech Services), or add a Gemini key.")
        }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val out = File(dir, "u_${System.nanoTime()}.wav")
        try {
            val ok = speakToFile(tts, text, out)
            if (!ok || !out.exists() || out.length() < 100) throw IOException("The phone voice could not read this line.")
            return WavUtil.read(out)
        } finally {
            out.delete()
        }
    }

    private suspend fun speakToFile(tts: TextToSpeech, text: String, out: File): Boolean =
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id && cont.isActive) cont.resume(false) }
            })
            val code = tts.synthesizeToFile(text, Bundle(), out, id)
            if (code != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
        }

    private suspend fun viaGemini(text: String): PcmClip {
        val request = GeminiRequest(
            listOf(GContent(parts = listOf(GPart(text = "Read this aloud clearly in a natural narrator voice: $text")))),
            GenConfig(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(VoiceConfig(PrebuiltVoice(settings.geminiVoice)))
            )
        )
        val res = withRetry(3) { gemini.generate(settings.geminiTtsModel, settings.geminiKey, request) }
        val data = res.candidates?.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.inlineData?.data }
            ?: throw IOException("Gemini returned no audio.")
        return PcmClip(WavUtil.pcm16le(Base64.decode(data, Base64.DEFAULT)), 24000)
    }

    private val TtsEngine.label get() = if (this == TtsEngine.DEVICE) "Phone voice" else "Gemini voice"

    @Suppress("unused")
    private val rate = DubMixer.RATE
}
