package com.movierecapee.data.repo

import android.content.Context
import android.net.Uri
import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.media.DubMixer
import com.movierecapee.data.media.MediaTools
import com.movierecapee.data.media.TransformRunner
import com.movierecapee.data.media.WavUtil
import com.movierecapee.domain.Project
import com.movierecapee.domain.RawSegment
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.util.UserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/** The three AI steps: speech recognition, translation and voice. */
class ProcessingPipeline(
    private val context: Context,
    private val settings: SecureSettings,
    private val asr: AsrRepository,
    private val translator: TranslationRepository,
    private val tts: TtsRepository,
    private val runner: TransformRunner,
    private val projects: com.movierecapee.data.local.ProjectRepository
) {
    class Transcript(val segments: List<SubtitleSegment>, val providers: Set<String>)

    suspend fun transcribe(project: Project, onProgress: (Float) -> Unit): Transcript {
        val uri = Uri.parse(project.videoUri)
        val hasAudio = withContext(Dispatchers.IO) { runCatching { MediaTools.hasAudio(context, uri) }.getOrDefault(true) }
        if (!hasAudio) throw UserException("This video has no audio track to transcribe.")
        val chunks = ceil(project.durationMs / CHUNK_MS.toDouble()).toInt().coerceAtLeast(1)
        val raw = ArrayList<RawSegment>()
        val providers = LinkedHashSet<String>()
        val dir = File(context.cacheDir, "asr").apply { mkdirs() }
        for (i in 0 until chunks) {
            onProgress(i / chunks.toFloat())
            val start = i * CHUNK_MS
            val end = min(project.durationMs, start + CHUNK_MS)
            val file = File(dir, "p${project.id}_$i.m4a")
            try {
                runner.extractAudio(uri, start, end, file)
                val r = asr.transcribe(file, start, project.sourceLang)
                raw += r.segments
                providers += r.provider
            } finally {
                file.delete()
            }
        }
        onProgress(1f)
        val segments = SegmentUtil.normalize(raw, project.durationMs)
        if (segments.isEmpty()) throw UserException("No speech was found in this video.")
        return Transcript(segments, providers)
    }

    suspend fun translate(project: Project, onProgress: (Float) -> Unit): TranslationRepository.Result {
        if (project.segments.isEmpty()) throw UserException("Transcribe the video first (or add subtitle lines by hand).")
        return translator.translate(project.segments, project.sourceLang, project.targetLang) { done, total ->
            onProgress(if (total == 0) 1f else done / total.toFloat())
        }
    }

    /** Reads every subtitle line aloud and builds one voice track the length of the video. */
    suspend fun generateVoice(project: Project, onProgress: (Float) -> Unit): File {
        val lines = project.segments.filter { it.spokenText().isNotEmpty() }
        if (lines.isEmpty()) throw UserException("There are no subtitle lines to read.")
        try {
            val placed = ArrayList<DubMixer.Placed>()
            lines.forEachIndexed { i, s ->
                onProgress(i / lines.size.toFloat())
                val lang = if (s.isTranslated()) project.targetLang else project.sourceLang
                val clip = tts.synthesize(s.spokenText(), lang)
                placed += DubMixer.Placed(s.startMs, s.durationMs, clip.samples, clip.rate)
            }
            val mixed = withContext(Dispatchers.Default) { DubMixer.mix(project.durationMs, placed) }
            val out = File(projects.projectDir(project.id), "voice.wav")
            withContext(Dispatchers.IO) { WavUtil.write(out, mixed, DubMixer.RATE) }
            onProgress(1f)
            return out
        } finally {
            tts.release()
        }
    }

    companion object { const val CHUNK_MS = 10 * 60_000L }
}
