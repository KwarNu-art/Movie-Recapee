package com.movierecapee.data.repo

import android.util.Base64
import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.remote.GContent
import com.movierecapee.data.remote.GPart
import com.movierecapee.data.remote.GenConfig
import com.movierecapee.data.remote.GeminiApi
import com.movierecapee.data.remote.GeminiRequest
import com.movierecapee.data.remote.GroqApi
import com.movierecapee.data.remote.InlineData
import com.movierecapee.domain.Lang
import com.movierecapee.domain.RawSegment
import com.movierecapee.util.JsonExtract
import com.movierecapee.util.UserException
import com.movierecapee.util.userMessage
import com.movierecapee.util.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Speech to text with automatic fallback: Groq Whisper first, then Gemini. */
class AsrRepository(
    private val settings: SecureSettings,
    private val groq: GroqApi,
    private val gemini: GeminiApi
) {
    class Result(val segments: List<RawSegment>, val provider: String)

    suspend fun transcribe(audio: File, offsetMs: Long, lang: Lang?): Result = withContext(Dispatchers.IO) {
        val providers = ArrayList<Pair<String, suspend () -> List<RawSegment>>>()
        if (settings.groqKey.isNotBlank()) providers += "Groq Whisper" to { viaGroq(audio, lang) }
        if (settings.geminiKey.isNotBlank()) providers += "Gemini" to { viaGemini(audio, lang) }
        if (providers.isEmpty()) throw UserException("Add a free Groq or Gemini API key in Settings first.")

        val errors = ArrayList<String>()
        for ((name, call) in providers) {
            try {
                val segs = withRetry { call() }
                return@withContext Result(segs.map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "$name: ${e.userMessage()}"
            }
        }
        throw IOException(errors.joinToString("  |  "))
    }

    private suspend fun viaGroq(audio: File, lang: Lang?): List<RawSegment> {
        val part = MultipartBody.Part.createFormData("file", audio.name, audio.asRequestBody("audio/mp4".toMediaType()))
        val text = "text/plain".toMediaType()
        val res = groq.transcribe(
            "Bearer ${settings.groqKey}", part,
            settings.groqAsrModel.toRequestBody(text), "verbose_json".toRequestBody(text),
            lang?.code?.toRequestBody(text)
        )
        return res.segments.orEmpty().mapNotNull { s ->
            val t = s.text?.trim().orEmpty()
            if (t.isEmpty()) null else RawSegment(((s.start ?: 0.0) * 1000).toLong(), ((s.end ?: 0.0) * 1000).toLong(), t)
        }
    }

    private suspend fun viaGemini(audio: File, lang: Lang?): List<RawSegment> {
        val b64 = Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)
        val language = lang?.promptName ?: "the spoken language"
        val prompt = "Transcribe this audio. The language is $language. Return ONLY a JSON array, no other text: " +
            "[{\"start\": seconds, \"end\": seconds, \"text\": \"...\"}]. Start and end are seconds from the start of this audio. " +
            "Make each item one natural subtitle line of about 2 to 8 seconds. Do not translate."
        val res = gemini.generate(
            settings.geminiModel, settings.geminiKey,
            GeminiRequest(
                listOf(GContent(parts = listOf(GPart(inlineData = InlineData("audio/mp4", b64)), GPart(text = prompt)))),
                GenConfig(responseMimeType = "application/json", temperature = 0.0)
            )
        )
        val answer = res.candidates?.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text }
            ?: throw IOException("Gemini returned no text.")
        return JsonExtract.segments(answer) ?: throw IOException("Gemini answer could not be read.")
    }
}
