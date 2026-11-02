package com.movierecapee.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movierecapee.data.local.ProjectRepository
import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.media.ExportRequest
import com.movierecapee.data.media.MediaTools
import com.movierecapee.data.media.VideoExporter
import com.movierecapee.data.repo.ProcessingPipeline
import com.movierecapee.domain.CaptionStyle
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.Lang
import com.movierecapee.domain.Project
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.domain.SrtUtil
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.domain.VoiceSettings
import com.movierecapee.util.UserException
import com.movierecapee.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class EditorUi(
    val project: Project,
    val busy: String? = null,
    val progress: Float? = null,
    val message: String? = null,
    val error: String? = null,
    val exportedUri: String? = null
)

class EditorViewModel(
    private val projectId: Long,
    private val context: Context,
    private val repo: ProjectRepository,
    private val pipeline: ProcessingPipeline,
    private val settings: SecureSettings,
    private val exporter: VideoExporter
) : ViewModel() {

    private val _ui = MutableStateFlow<EditorUi?>(null)
    val ui: StateFlow<EditorUi?> = _ui
    private val _voice = MutableStateFlow(settings.voice)
    val voice: StateFlow<VoiceSettings> = _voice

    private var job: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            repo.get(projectId)?.let { p -> _ui.value = EditorUi(p, exportedUri = p.outputUri) }
        }
    }

    val hasKeys: Boolean get() = settings.groqKey.isNotBlank() || settings.geminiKey.isNotBlank()

    // ----- editing -----

    private fun edit(f: (Project) -> Project) {
        _ui.update { it?.copy(project = f(it.project)) }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            _ui.value?.project?.let { repo.save(it) }
        }
    }

    fun setLanguages(source: Lang, target: Lang) = edit { it.copy(sourceLang = source, targetLang = target) }
    fun setDisplayMode(mode: DisplayMode) = edit { it.copy(displayMode = mode) }
    fun setStyle(style: CaptionStyle) = edit { it.copy(style = style) }

    fun updateSegment(id: Int, f: (SubtitleSegment) -> SubtitleSegment) =
        edit { p -> p.copy(segments = p.segments.map { if (it.id == id) f(it) else it }) }

    fun deleteSegment(id: Int) = edit { p -> p.copy(segments = SegmentUtil.renumber(p.segments.filter { it.id != id })) }

    fun addSegmentAt(ms: Long) = edit { p ->
        val start = ms.coerceIn(0, (p.durationMs - 500).coerceAtLeast(0))
        val end = (start + 2500).coerceAtMost(p.durationMs).coerceAtLeast(start + 100)
        p.copy(segments = SegmentUtil.renumber(p.segments + SubtitleSegment(0, start, end, "New line")))
    }

    fun setVoice(v: VoiceSettings) {
        _voice.value = v
        settings.voice = v
    }

    fun dismissMessages() = _ui.update { it?.copy(message = null, error = null) }

    // ----- AI steps -----

    private fun task(label: String, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it?.copy(busy = label, progress = 0f, error = null, message = null) }
            try {
                block()
            } catch (e: CancellationException) {
                _ui.update { it?.copy(message = "Cancelled.") }
                throw e
            } catch (e: Exception) {
                _ui.update { it?.copy(error = e.userMessage()) }
            } finally {
                _ui.update { it?.copy(busy = null, progress = null) }
                _ui.value?.project?.let { repo.save(it) }
            }
        }
    }

    private fun progress(label: String? = null, p: Float) =
        _ui.update { it?.copy(progress = p.coerceIn(0f, 1f), busy = label ?: it.busy) }

    fun cancel() { job?.cancel() }

    private suspend fun doTranscribe() {
        val p = _ui.value!!.project
        val t = pipeline.transcribe(p) { progress(p = it) }
        edit { it.copy(segments = t.segments, dubPath = null) }
        _ui.update { it?.copy(message = "Found ${t.segments.size} subtitle lines (${t.providers.joinToString()}).") }
    }

    private suspend fun doTranslate() {
        val p = _ui.value!!.project
        val r = pipeline.translate(p) { progress(p = it) }
        edit { it.copy(segments = r.segments, dubPath = null, displayMode = if (it.displayMode == DisplayMode.SOURCE) DisplayMode.TARGET else it.displayMode) }
        _ui.update { it?.copy(message = "Translated ${r.segments.size} lines (${r.providers.joinToString()}).") }
    }

    private suspend fun doVoice() {
        val p = _ui.value!!.project
        val file = pipeline.generateVoice(p) { progress(p = it) }
        edit { it.copy(dubPath = file.absolutePath) }
        _ui.update { it?.copy(message = "Voice track is ready.") }
    }

    fun transcribe() = task("Listening to the video") { doTranscribe() }
    fun translate() = task("Translating") { doTranslate() }
    fun generateVoice() = task("Making the voice track") { doVoice() }

    fun runAll() = task("Transcribing") {
        doTranscribe()
        progress("Translating", 0f)
        doTranslate()
        progress("Making the voice track", 0f)
        doVoice()
        _ui.update { it?.copy(message = "All steps done. Check the lines, then export.") }
    }

    // ----- export -----

    fun export() = task("Exporting video") {
        val p = _ui.value!!.project
        val v = _voice.value
        val dub = p.dubPath?.let { File(it) }?.takeIf { it.exists() }
        val tmp = File(context.cacheDir, "exports/recap_${System.currentTimeMillis()}.mp4")
        try {
            exporter.export(
                ExportRequest(
                    videoUri = Uri.parse(p.videoUri), displayWidth = p.displayWidth, displayHeight = p.displayHeight,
                    segments = p.segments, style = p.style, mode = p.displayMode, burnCaptions = v.burnCaptions,
                    dubWav = dub, audioMode = v.audioMode, originalVolume = v.originalVolume,
                    outHeight = v.outHeight, outFile = tmp
                )
            ) { progress(p = it) }
            progress("Saving to gallery", 1f)
            val uri = withContext(Dispatchers.IO) { MediaTools.publishVideo(context, tmp, p.title + "_recap") }
            edit { it.copy(outputUri = uri.toString()) }
            _ui.update { it?.copy(exportedUri = uri.toString(), message = "Saved to Movies/Movie-Recapee in your gallery.") }
        } finally {
            tmp.delete()
        }
    }

    fun srtText(): String {
        val p = _ui.value?.project ?: return ""
        return SrtUtil.format(p.segments, p.displayMode)
    }

    fun requireKeys() {
        if (!hasKeys) _ui.update { it?.copy(error = "Add a free Groq or Gemini API key in Settings first.") }
    }

    fun fail(text: String) = _ui.update { it?.copy(error = UserException(text).message) }
}
