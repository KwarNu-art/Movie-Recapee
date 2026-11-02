package com.movierecapee.data.repo

import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.remote.ChatMessage
import com.movierecapee.data.remote.ChatRequest
import com.movierecapee.data.remote.GContent
import com.movierecapee.data.remote.GPart
import com.movierecapee.data.remote.GenConfig
import com.movierecapee.data.remote.GeminiApi
import com.movierecapee.data.remote.GeminiRequest
import com.movierecapee.data.remote.GroqApi
import com.movierecapee.data.remote.MyMemoryApi
import com.movierecapee.domain.Lang
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.util.JsonExtract
import com.movierecapee.util.userMessage
import com.movierecapee.util.withRetry
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Translation with fallback: Groq, then Gemini, then the keyless MyMemory service. */
class TranslationRepository(
    private val settings: SecureSettings,
    private val groq: GroqApi,
    private val gemini: GeminiApi,
    private val myMemory: MyMemoryApi
) {
    class Result(val segments: List<SubtitleSegment>, val providers: Set<String>)

    suspend fun translate(
        segments: List<SubtitleSegment>, from: Lang, to: Lang,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        if (from == to) return@withContext Result(segments.map { it.copy(translated = it.source) }, setOf("none needed"))
        val out = ArrayList<SubtitleSegment>(segments.size)
        val used = LinkedHashSet<String>()
        for ((index, batch) in segments.chunked(BATCH).withIndex()) {
            onProgress(index * BATCH, segments.size)
            val (texts, provider) = translateBatch(batch.map { it.source }, from, to)
            used += provider
            batch.forEachIndexed { i, s -> out += s.copy(translated = texts[i].trim()) }
        }
        onProgress(segments.size, segments.size)
        Result(out, used)
    }

    private suspend fun translateBatch(lines: List<String>, from: Lang, to: Lang): Pair<List<String>, String> {
        val providers = ArrayList<Pair<String, suspend () -> List<String>>>()
        if (settings.groqKey.isNotBlank()) providers += "Groq" to { viaGroq(lines, from, to) }
        if (settings.geminiKey.isNotBlank()) providers += "Gemini" to { viaGemini(lines, from, to) }
        providers += "MyMemory" to { viaMyMemory(lines, from, to) }
        val errors = ArrayList<String>()
        for ((name, call) in providers) {
            try {
                val result = withRetry { call() }
                if (result.size == lines.size) return result to name
                errors += "$name: wrong number of lines"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "$name: ${e.userMessage()}"
            }
        }
        throw IOException(errors.joinToString("  |  "))
    }

    private fun prompt(lines: List<String>, from: Lang, to: Lang): String =
        "You are a professional subtitle translator for movie recap videos. Translate each line from ${from.promptName} to ${to.promptName}. " +
            "Keep the meaning, keep names, use natural spoken language and keep each line short. " +
            "Return ONLY JSON like {\"translations\": [\"...\", \"...\"]} with exactly ${lines.size} items in the same order.\n" +
            "Lines (JSON array): " + Gson().toJson(lines)

    private suspend fun viaGroq(lines: List<String>, from: Lang, to: Lang): List<String> {
        val res = groq.chat(
            "Bearer ${settings.groqKey}",
            ChatRequest(settings.groqChatModel, listOf(ChatMessage("user", prompt(lines, from, to))), 0.2, mapOf("type" to "json_object"))
        )
        val text = res.choices?.firstOrNull()?.message?.content ?: throw IOException("Groq returned no text.")
        return JsonExtract.stringList(text) ?: throw IOException("Groq answer could not be read.")
    }

    private suspend fun viaGemini(lines: List<String>, from: Lang, to: Lang): List<String> {
        val res = gemini.generate(
            settings.geminiModel, settings.geminiKey,
            GeminiRequest(listOf(GContent(parts = listOf(GPart(text = prompt(lines, from, to))))), GenConfig(responseMimeType = "application/json", temperature = 0.2))
        )
        val text = res.candidates?.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text } ?: throw IOException("Gemini returned no text.")
        return JsonExtract.stringList(text) ?: throw IOException("Gemini answer could not be read.")
    }

    private suspend fun viaMyMemory(lines: List<String>, from: Lang, to: Lang): List<String> {
        val pair = "${mm(from)}|${mm(to)}"
        return lines.map { line ->
            if (line.isBlank()) "" else {
                val r = myMemory.translate(line.take(480), pair)
                r.responseData?.translatedText?.takeIf { it.isNotBlank() && !it.contains("MYMEMORY WARNING", true) }
                    ?: throw IOException("MyMemory limit reached.")
            }
        }
    }

    private fun mm(l: Lang) = when (l) { Lang.CHINESE -> "zh-CN"; Lang.MYANMAR -> "my"; Lang.ENGLISH -> "en" }

    companion object { const val BATCH = 25 }
}
