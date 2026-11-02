package com.movierecapee.ui.settings

import androidx.lifecycle.ViewModel
import com.movierecapee.data.local.SecureSettings
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsViewModel(private val settings: SecureSettings) : ViewModel() {
    val groqKey = MutableStateFlow(settings.groqKey)
    val geminiKey = MutableStateFlow(settings.geminiKey)
    val groqAsr = MutableStateFlow(settings.groqAsrModel)
    val groqChat = MutableStateFlow(settings.groqChatModel)
    val gemini = MutableStateFlow(settings.geminiModel)
    val geminiTts = MutableStateFlow(settings.geminiTtsModel)
    val geminiVoice = MutableStateFlow(settings.geminiVoice)

    // Every change is saved at once, so keys are never lost.
    fun setGroqKey(v: String) { groqKey.value = v; settings.groqKey = v }
    fun setGeminiKey(v: String) { geminiKey.value = v; settings.geminiKey = v }
    fun setGroqAsr(v: String) { groqAsr.value = v; settings.groqAsrModel = v }
    fun setGroqChat(v: String) { groqChat.value = v; settings.groqChatModel = v }
    fun setGemini(v: String) { gemini.value = v; settings.geminiModel = v }
    fun setGeminiTts(v: String) { geminiTts.value = v; settings.geminiTtsModel = v }
    fun setGeminiVoice(v: String) { geminiVoice.value = v; settings.geminiVoice = v }

    fun resetModels() {
        settings.resetModels()
        groqAsr.value = settings.groqAsrModel; groqChat.value = settings.groqChatModel
        gemini.value = settings.geminiModel; geminiTts.value = settings.geminiTtsModel; geminiVoice.value = settings.geminiVoice
    }
}
