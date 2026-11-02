package com.movierecapee.data.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs Media3 Transformer (Google's free, on-device video engine) as a coroutine. */
class TransformRunner(private val context: Context) {

    /** Cuts a time range of the video's audio into a small AAC file for speech recognition. */
    suspend fun extractAudio(uri: Uri, startMs: Long, endMs: Long, out: File) {
        out.parentFile?.mkdirs()
        out.delete()
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
        val item = EditedMediaItem.Builder(MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build())
            .setRemoveVideo(true)
            .build()
        run(out, { setAudioMimeType(MimeTypes.AUDIO_AAC) }, { it.start(item, out.absolutePath) }, {})
        if (!out.exists() || out.length() == 0L) throw IOException("Could not read the audio of this video.")
    }

    suspend fun export(composition: Composition, out: File, onProgress: (Float) -> Unit) {
        out.parentFile?.mkdirs()
        out.delete()
        run(out, {
            setVideoMimeType(MimeTypes.VIDEO_H264)
            setAudioMimeType(MimeTypes.AUDIO_AAC)
        }, { it.start(composition, out.absolutePath) }, onProgress)
    }

    private suspend fun run(
        out: File,
        configure: Transformer.Builder.() -> Unit,
        start: (Transformer) -> Unit,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val transformer = Transformer.Builder(context)
                .apply(configure)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacksAndMessages(null)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacksAndMessages(null)
                        if (cont.isActive) cont.resumeWithException(IOException("Video engine error: ${exportException.errorCodeName} (${exportException.message})"))
                    }
                })
                .build()
            val poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onProgress(holder.progress / 100f)
                    handler.postDelayed(this, 400)
                }
            }
            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacksAndMessages(null)
                    transformer.cancel()
                    out.delete()
                }
            }
            try {
                start(transformer)
                handler.postDelayed(poll, 400)
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }
}
