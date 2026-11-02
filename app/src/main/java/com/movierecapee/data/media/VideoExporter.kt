package com.movierecapee.data.media

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import com.movierecapee.domain.AudioMode
import com.movierecapee.domain.CaptionStyle
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.util.UserException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

class ExportRequest(
    val videoUri: Uri,
    val displayWidth: Int,
    val displayHeight: Int,
    val segments: List<SubtitleSegment>,
    val style: CaptionStyle,
    val mode: DisplayMode,
    val burnCaptions: Boolean,
    val dubWav: File?,
    val audioMode: AudioMode,
    val originalVolume: Float,
    val outHeight: Int,
    val outFile: File
)

/** Multiplies 16-bit audio by a fixed gain (used to lower the original sound under the voice). */
class GainAudioProcessor(private val gain: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        while (input.remaining() >= 2) {
            val v = (input.short * gain).roundToInt().coerceIn(-32768, 32767)
            out.putShort(v.toShort())
        }
        out.flip()
    }
}

/** A transparent picture the size of the video that shows the current subtitle line. */
class CaptionOverlay(
    private val w: Int,
    private val h: Int,
    private val segments: List<SubtitleSegment>,
    private val mode: DisplayMode,
    private val style: CaptionStyle
) : BitmapOverlay() {
    private val empty: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private var currentIndex = -2
    private var current: Bitmap = empty

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val idx = segments.indexOfFirst { ms >= it.startMs && ms < it.endMs }
        if (idx != currentIndex) {
            currentIndex = idx
            current = if (idx < 0) empty else CaptionRenderer.render(w, h, segments[idx].textFor(mode), style)
        }
        return current
    }
}

class VideoExporter(private val runner: TransformRunner) {

    suspend fun export(req: ExportRequest, onProgress: (Float) -> Unit) {
        val srcW = req.displayWidth
        val srcH = req.displayHeight
        if (srcW <= 0 || srcH <= 0) throw UserException("This video has no picture size. Pick another file.")
        val outH = (if (req.outHeight > 0) min(srcH, req.outHeight) else srcH).let { it - it % 2 }
        val outW = ((srcW.toDouble() * outH / srcH).roundToInt()).let { it - it % 2 }

        val videoEffects = ArrayList<Effect>()
        if (outH != srcH) videoEffects += Presentation.createForHeight(outH)
        val captions = req.segments.filter { it.textFor(req.mode).isNotEmpty() }
        if (req.burnCaptions && captions.isNotEmpty()) {
            videoEffects += OverlayEffect(ImmutableList.of(CaptionOverlay(outW, outH, captions.sortedBy { it.startMs }, req.mode, req.style)))
        }

        val useDub = req.audioMode != AudioMode.KEEP_ORIGINAL && req.dubWav != null && req.dubWav.exists()
        if (req.audioMode != AudioMode.KEEP_ORIGINAL && !useDub) throw UserException("Generate the voice track first, or choose \"Keep original\".")

        val audioProcessors = ArrayList<AudioProcessor>()
        if (useDub && req.audioMode == AudioMode.MIX && req.originalVolume < 0.999f) audioProcessors += GainAudioProcessor(req.originalVolume)

        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(req.videoUri))
            .setEffects(Effects(audioProcessors, videoEffects))
            .setRemoveAudio(useDub && req.audioMode == AudioMode.REPLACE)
            .build()
        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences += EditedMediaItemSequence(listOf(videoItem))
        if (useDub) {
            val dubItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(req.dubWav))).build()
            sequences += EditedMediaItemSequence(listOf(dubItem))
        }
        runner.export(Composition.Builder(sequences).build(), req.outFile, onProgress)
    }
}
