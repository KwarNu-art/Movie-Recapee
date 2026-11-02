package com.movierecapee.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- Groq (free tier): Whisper speech recognition and chat for translation ----------

data class GroqSegment(val start: Double?, val end: Double?, val text: String?)
data class GroqTranscription(val text: String?, val segments: List<GroqSegment>?)

data class ChatMessage(val role: String, val content: String)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    @SerializedName("response_format") val responseFormat: Map<String, String>? = null
)
data class ChatChoice(val message: ChatMessage?)
data class ChatResponse(val choices: List<ChatChoice>?)

interface GroqApi {
    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribe(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") format: RequestBody,
        @Part("language") language: RequestBody?
    ): GroqTranscription

    @POST("openai/v1/chat/completions")
    suspend fun chat(@Header("Authorization") auth: String, @Body body: ChatRequest): ChatResponse
}

// ---------- Gemini (free tier): transcription, translation and voice ----------

data class InlineData(@SerializedName("mime_type") val mimeType: String, val data: String)
data class GPart(val text: String? = null, @SerializedName("inline_data") val inlineData: InlineData? = null)
data class GContent(val role: String? = null, val parts: List<GPart>)
data class PrebuiltVoice(val voiceName: String)
data class VoiceConfig(val prebuiltVoiceConfig: PrebuiltVoice)
data class SpeechConfig(val voiceConfig: VoiceConfig)
data class GenConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null,
    val responseModalities: List<String>? = null,
    val speechConfig: SpeechConfig? = null
)
data class GeminiRequest(val contents: List<GContent>, val generationConfig: GenConfig? = null)

data class GRespInline(val mimeType: String?, val data: String?)
data class GRespPart(val text: String?, val inlineData: GRespInline?)
data class GRespContent(val parts: List<GRespPart>?)
data class GCandidate(val content: GRespContent?)
data class GeminiResponse(val candidates: List<GCandidate>?)

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generate(
        @Path("model") model: String,
        @Query("key") key: String,
        @Body body: GeminiRequest
    ): GeminiResponse
}

// ---------- MyMemory (free, no key): last-resort translation ----------

data class MyMemoryData(val translatedText: String?)
data class MyMemoryResponse(val responseData: MyMemoryData?)

interface MyMemoryApi {
    @GET("get")
    suspend fun translate(@Query("q") text: String, @Query("langpair") pair: String): MyMemoryResponse
}
