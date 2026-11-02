package com.movierecapee.domain

import java.util.Locale

enum class Lang(val code: String, val label: String, val tag: String, val promptName: String) {
    ENGLISH("en", "English", "en-US", "English"),
    CHINESE("zh", "Chinese", "zh-CN", "Simplified Chinese"),
    MYANMAR("my", "Myanmar", "my-MM", "Burmese (Myanmar language, standard Unicode script, never Zawgyi)");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromCode(code: String?): Lang = values().firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class DisplayMode(val label: String) {
    SOURCE("Original"),
    TARGET("Translated"),
    DUAL("Both")
}

enum class CaptionAlign(val label: String) { LEFT("Left"), CENTER("Center"), RIGHT("Right") }

enum class AudioMode(val label: String) {
    KEEP_ORIGINAL("Keep original"),
    REPLACE("Replace with voice"),
    MIX("Voice over original")
}

enum class TtsEngine(val label: String) {
    DEVICE("Phone voice (offline)"),
    GEMINI("Gemini voice (online)")
}

data class SubtitleSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val source: String,
    val translated: String = ""
) {
    val durationMs: Long get() = endMs - startMs

    /** Lines to show for a display mode. In "Both" the translation is on top and the original below. */
    fun textFor(mode: DisplayMode): List<String> {
        val lines = when (mode) {
            DisplayMode.SOURCE -> listOf(source)
            DisplayMode.TARGET -> listOf(translated.ifBlank { source })
            DisplayMode.DUAL -> if (translated.isBlank()) listOf(source) else listOf(translated, source)
        }
        return lines.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** The text the voice reads: the translation when there is one. */
    fun spokenText(): String = translated.ifBlank { source }.trim()
    fun isTranslated(): Boolean = translated.isNotBlank()
}

data class CaptionStyle(
    /** Font height as a fraction of the video height. */
    val fontSize: Float = 0.05f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val strokeColor: Int = 0xFF000000.toInt(),
    /** Outline width as a fraction of the font size. 0 = no outline. */
    val strokeWidth: Float = 0.12f,
    val boxColor: Int = 0xFF000000.toInt(),
    val boxOpacity: Float = 0.45f,
    /** Box padding as a fraction of the font size. */
    val boxPadding: Float = 0.25f,
    /** Distance of the caption's bottom edge from the bottom, as a fraction of the video height. */
    val offsetY: Float = 0.08f,
    val align: CaptionAlign = CaptionAlign.CENTER,
    val bold: Boolean = true
)

data class Project(
    val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val sourceLang: Lang = Lang.ENGLISH,
    val targetLang: Lang = Lang.MYANMAR,
    val displayMode: DisplayMode = DisplayMode.TARGET,
    val style: CaptionStyle = CaptionStyle(),
    val segments: List<SubtitleSegment> = emptyList(),
    val dubPath: String? = null,
    val outputUri: String? = null,
    val thumbPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Size of the picture as the viewer sees it (after rotation). */
    val displayWidth: Int get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight: Int get() = if (rotation == 90 || rotation == 270) width else height
    val aspect: Float get() = if (displayWidth > 0 && displayHeight > 0) displayWidth.toFloat() / displayHeight else 16f / 9f
}

data class RawSegment(val startMs: Long, val endMs: Long, val text: String)

data class VoiceSettings(
    val engine: TtsEngine = TtsEngine.DEVICE,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val audioMode: AudioMode = AudioMode.REPLACE,
    val originalVolume: Float = 0.25f,
    val burnCaptions: Boolean = true,
    val outHeight: Int = 720
)
