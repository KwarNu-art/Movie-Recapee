package com.movierecapee.ui.editor

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.movierecapee.data.media.CaptionRenderer
import com.movierecapee.domain.CaptionStyle
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.Project
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.ui.theme.Adobe
import kotlinx.coroutines.delay

/** Holds the player and its current position so panels can seek and read the time. */
class PlayerState(val player: ExoPlayer) {
    var positionMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    fun seek(ms: Long) { player.seekTo(ms.coerceAtLeast(0)); positionMs = ms.coerceAtLeast(0) }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
}

@Composable
fun rememberPlayerState(videoUri: String): PlayerState {
    val ctx = LocalContext.current
    val state = remember { PlayerState(ExoPlayer.Builder(ctx).build()) }
    DisposableEffect(Unit) { onDispose { state.player.release() } }
    LaunchedEffect(videoUri) {
        state.player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
        state.player.prepare()
    }
    LaunchedEffect(state) {
        while (true) {
            state.positionMs = state.player.currentPosition
            state.playing = state.player.isPlaying
            delay(80)
        }
    }
    return state
}

@Composable
fun VideoPreview(project: Project, state: PlayerState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(230.dp).background(Color.Black), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().aspectRatio(project.aspect, matchHeightConstraintsFirst = true)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = state.player
                    }
                },
                modifier = Modifier.matchParentSize()
            )
            CaptionPreview(project.segments, project.displayMode, project.style, state.positionMs)
        }
    }
}

/** The caption exactly as it will be burned into the export (same renderer). */
@Composable
fun CaptionPreview(segments: List<SubtitleSegment>, mode: DisplayMode, style: CaptionStyle, positionMs: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val lines = segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.textFor(mode).orEmpty()
        val bmp = remember(lines, style, w, h) {
            if (lines.isEmpty() || w <= 0 || h <= 0) null else CaptionRenderer.render(w, h, lines, style).asImageBitmap()
        }
        if (bmp != null) Image(bmp, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
    }
}

@Composable
fun Transport(durationMs: Long, state: PlayerState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { state.seek(state.positionMs - 5000) }) { Icon(Icons.Filled.Replay5, "Back 5 seconds") }
        IconButton(onClick = { state.toggle() }) {
            Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.playing) "Pause" else "Play", tint = Adobe.BlueLight)
        }
        IconButton(onClick = { state.seek((state.positionMs + 5000).coerceAtMost(durationMs)) }) { Icon(Icons.Filled.Forward5, "Forward 5 seconds") }
        Text(
            "${SegmentUtil.timecode(state.positionMs)} / ${SegmentUtil.timecode(durationMs)}",
            color = Adobe.Orange, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Multi-track timeline: video, original audio, subtitles and voice. Tap or drag to move the playhead. */
@Composable
fun Timeline(durationMs: Long, positionMs: Long, segments: List<SubtitleSegment>, hasVoice: Boolean, onSeek: (Long) -> Unit) {
    val labels = listOf("V1", "A1", "CC", "A2")
    val lane = 20.dp
    val seek by rememberUpdatedState(onSeek)
    Row(Modifier.fillMaxWidth().background(Adobe.Panel).padding(vertical = 6.dp)) {
        Column(Modifier.width(34.dp)) {
            labels.forEach { Text(it, color = Adobe.TextDim, fontSize = 10.sp, modifier = Modifier.height(lane).padding(start = 6.dp)) }
        }
        Canvas(
            Modifier
                .weight(1f)
                .height(lane * 4)
                .padding(end = 8.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o -> seek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures { change, _ -> seek((change.position.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
                }
        ) {
            val laneH = size.height / 4
            val gap = 3f
            fun bar(row: Int, x: Float, w: Float, color: Color) =
                drawRect(color, Offset(x, row * laneH + gap), Size(w.coerceAtLeast(2f), laneH - gap * 2))
            bar(0, 0f, size.width, Adobe.Blue)
            bar(1, 0f, size.width, Color(0xFF2F6F5B))
            val total = durationMs.coerceAtLeast(1).toFloat()
            segments.forEach { s -> bar(2, s.startMs / total * size.width, (s.endMs - s.startMs) / total * size.width, Adobe.Orange) }
            if (hasVoice) segments.forEach { s -> bar(3, s.startMs / total * size.width, (s.endMs - s.startMs) / total * size.width, Adobe.Green) }
            val x = positionMs / total * size.width
            drawLine(Adobe.Red, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
        }
    }
}
