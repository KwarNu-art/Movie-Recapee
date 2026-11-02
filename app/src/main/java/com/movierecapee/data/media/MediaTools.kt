package com.movierecapee.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class VideoInfo(
    val title: String, val durationMs: Long, val width: Int, val height: Int,
    val rotation: Int, val thumb: Bitmap?
)

/** Small helpers around the Android media APIs. */
object MediaTools {
    fun readInfo(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0 || duration <= 0) throw IOException("This file does not look like a playable video.")
            val frame = r.getFrameAtTime(minOf(1_000_000L, duration * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val thumb = frame?.let { Bitmap.createScaledBitmap(it, 320, (320f * it.height / it.width).toInt().coerceAtLeast(1), true) }
            return VideoInfo(displayName(context, uri) ?: "Video", duration, w, h, rot, thumb)
        } finally {
            r.release()
        }
    }

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    } catch (_: Exception) { null }

    fun hasAudio(context: Context, uri: Uri): Boolean {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return true
            }
            return false
        } finally {
            ex.release()
        }
    }

    fun saveJpeg(bmp: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }

    /** Copies a finished video to the phone's gallery (Movies/Movie-Recapee). Returns a link that can be opened and shared. */
    fun publishVideo(context: Context, file: File, displayName: String): Uri {
        val name = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "recap" } + ".mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Movie-Recapee")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the video in the gallery.")
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not write the video.")
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Movie-Recapee").apply { mkdirs() }
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }
}
