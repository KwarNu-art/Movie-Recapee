package com.movierecapee.ui.editor

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movierecapee.domain.AudioMode
import com.movierecapee.domain.CaptionAlign
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.Lang
import com.movierecapee.domain.Project
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.domain.TtsEngine
import com.movierecapee.domain.VoiceSettings
import com.movierecapee.ui.ColorSwatches
import com.movierecapee.ui.LabeledSlider
import com.movierecapee.ui.Panel
import com.movierecapee.ui.SwatchColors
import com.movierecapee.ui.theme.Adobe
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

private val TABS = listOf("AI", "Subtitles", "Style", "Voice", "Export")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(projectId: Long, onBack: () -> Unit, vm: EditorViewModel = koinViewModel(parameters = { parametersOf(projectId) })) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    // Keep the screen awake while a long job runs.
    val busy = ui?.busy != null
    DisposableEffect(busy) {
        val window = (ctx as? android.app.Activity)?.window
        if (busy) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val state = ui
    if (state == null) {
        Scaffold(containerColor = Adobe.Bg) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
                Text("Loading project…", color = Adobe.TextDim)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
        return
    }
    val project = state.project
    val player = rememberPlayerState(project.videoUri)

    val srtSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.srtText().toByteArray(Charsets.UTF_8)) }
        }.onFailure { vm.fail("Could not save the subtitle file.") }
    }

    Scaffold(
        containerColor = Adobe.Bg,
        topBar = {
            TopAppBar(
                title = { Text(project.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Adobe.Panel)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            VideoPreview(project, player)
            Transport(project.durationMs, player)
            Timeline(project.durationMs, player.positionMs, project.segments, project.dubPath != null, player::seek)

            if (state.busy != null) {
                Column(Modifier.fillMaxWidth().background(Adobe.Panel2).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.busy, Modifier.weight(1f), color = Adobe.Orange, fontSize = 13.sp)
                        TextButton(onClick = vm::cancel) { Text("Cancel", color = Adobe.Red) }
                    }
                    val p = state.progress
                    if (p != null && p > 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            state.error?.let {
                Text(it, color = Adobe.Red, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Adobe.Red.copy(alpha = 0.12f)).padding(12.dp))
            }
            state.message?.let {
                Text(it, color = Adobe.BlueLight, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Adobe.Blue.copy(alpha = 0.18f)).padding(12.dp))
            }

            ScrollableTabRow(selectedTabIndex = tab, containerColor = Adobe.Panel, edgePadding = 0.dp) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }

            val idle = state.busy == null
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tab) {
                    0 -> item { AiPanel(project, idle, vm) }
                    1 -> {
                        item { SubtitleToolbar(project, player, vm) }
                        items(project.segments, key = { it.id }) { seg ->
                            SegmentRow(seg, project.sourceLang, project.targetLang, onSeek = { player.seek(seg.startMs) }, vm = vm)
                        }
                        if (project.segments.isEmpty()) item { Text("No subtitle lines yet. Use the AI tab, or add a line at the playhead.", color = Adobe.TextDim) }
                    }
                    2 -> item { StylePanel(project, player.positionMs, vm) }
                    3 -> item { VoicePanel(project, voice, idle, vm) }
                    else -> item {
                        ExportPanel(project, voice, state.exportedUri, idle, vm, onSaveSrt = { srtSaver.launch(project.title + ".srt") })
                    }
                }
            }
        }
    }
}

@Composable
private fun LangChips(label: String, selected: Lang, onPick: (Lang) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Adobe.TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Lang.values().forEach { l -> FilterChip(selected = l == selected, onClick = { onPick(l) }, label = { Text(l.label) }) }
        }
    }
}

@Composable
private fun AiPanel(project: Project, idle: Boolean, vm: EditorViewModel) {
    Panel("Languages") {
        LangChips("Spoken language in the video", project.sourceLang) { vm.setLanguages(it, project.targetLang) }
        LangChips("Translate subtitles and voice into", project.targetLang) { vm.setLanguages(project.sourceLang, it) }
    }
    Spacer10()
    Panel("AI pipeline") {
        if (!vm.hasKeys) Text("Add a free Groq or Gemini key in Settings (top of the home screen) to use the AI steps.", color = Adobe.Orange, fontSize = 13.sp)
        Button(onClick = vm::runAll, enabled = idle, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Adobe.Blue)) {
            Icon(Icons.Filled.PlayArrow, null); Text("  Run all steps")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::transcribe, enabled = idle, modifier = Modifier.weight(1f)) { Text("1 Listen") }
            OutlinedButton(onClick = vm::translate, enabled = idle && project.segments.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("2 Translate") }
            OutlinedButton(onClick = vm::generateVoice, enabled = idle && project.segments.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("3 Voice") }
        }
        Text(
            "Step 1 turns the speech into timed subtitle lines. Step 2 translates them. Step 3 reads them aloud and builds a voice track. " +
                "If a free service is busy, the next one is tried automatically.",
            color = Adobe.TextDim, fontSize = 12.sp
        )
        Text(
            "Status: ${project.segments.size} lines · ${project.segments.count { it.isTranslated() }} translated · voice ${if (project.dubPath != null) "ready" else "not made"}",
            color = Adobe.TextDim, fontSize = 12.sp
        )
    }
}

@Composable
private fun Spacer10() = androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))

@Composable
private fun SubtitleToolbar(project: Project, player: PlayerState, vm: EditorViewModel) {
    Panel("Caption language") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayMode.values().forEach { m ->
                FilterChip(
                    selected = project.displayMode == m, onClick = { vm.setDisplayMode(m) },
                    label = { Text(if (m == DisplayMode.DUAL) "Both (translation on top)" else m.label) }
                )
            }
        }
        OutlinedButton(onClick = { vm.addSegmentAt(player.positionMs) }) { Icon(Icons.Filled.Add, null); Text("  Add line at playhead") }
    }
}

@Composable
private fun SegmentRow(seg: SubtitleSegment, source: Lang, target: Lang, onSeek: () -> Unit, vm: EditorViewModel) {
    Column(Modifier.fillMaxWidth().background(Adobe.Panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${seg.id}", color = Adobe.Orange, fontSize = 12.sp, modifier = Modifier.width(36.dp))
            TimeField("In", seg.startMs, Modifier.weight(1f)) { ms -> vm.updateSegment(seg.id) { it.copy(startMs = ms.coerceAtMost(it.endMs - 100)) } }
            Text("  ")
            TimeField("Out", seg.endMs, Modifier.weight(1f)) { ms -> vm.updateSegment(seg.id) { it.copy(endMs = ms.coerceAtLeast(it.startMs + 100)) } }
            IconButton(onClick = onSeek) { Icon(Icons.Filled.PlayArrow, "Go to this line", tint = Adobe.BlueLight) }
            IconButton(onClick = { vm.deleteSegment(seg.id) }) { Icon(Icons.Filled.Delete, "Delete line", tint = Adobe.TextDim) }
        }
        OutlinedTextField(
            value = seg.source, onValueChange = { v -> vm.updateSegment(seg.id) { it.copy(source = v) } },
            label = { Text(source.label) }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = seg.translated, onValueChange = { v -> vm.updateSegment(seg.id) { it.copy(translated = v) } },
            label = { Text(target.label) }, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TimeField(label: String, ms: Long, modifier: Modifier, onChange: (Long) -> Unit) {
    fun fmt(v: Long) = String.format(Locale.US, "%.1f", v / 1000.0)
    var text by remember { mutableStateOf(fmt(ms)) }
    // Only rewrite the box when the value changed from outside (for example after a new transcription).
    LaunchedEffect(ms) { if (SegmentUtil.parseSeconds(text) != ms) text = fmt(ms) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            SegmentUtil.parseSeconds(t)?.let(onChange)
        },
        label = { Text("$label (s)") }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun StylePanel(project: Project, positionMs: Long, vm: EditorViewModel) {
    val s = project.style
    Panel("Caption preview") {
        Text(
            "The preview above shows the caption at the playhead. Move the playhead onto a subtitle line to see changes live.",
            color = Adobe.TextDim, fontSize = 12.sp
        )
    }
    Spacer10()
    Panel("Text") {
        LabeledSlider("Font size", s.fontSize, 0.025f..0.10f, "${(s.fontSize * 100).toInt()}%") { vm.setStyle(s.copy(fontSize = it)) }
        Text("Text colour", color = Adobe.TextDim, fontSize = 12.sp)
        ColorSwatches(s.textColor, SwatchColors) { vm.setStyle(s.copy(textColor = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bold", Modifier.weight(1f))
            Switch(checked = s.bold, onCheckedChange = { vm.setStyle(s.copy(bold = it)) })
        }
        Text("Alignment", color = Adobe.TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptionAlign.values().forEach { a -> FilterChip(selected = s.align == a, onClick = { vm.setStyle(s.copy(align = a)) }, label = { Text(a.label) }) }
        }
    }
    Spacer10()
    Panel("Outline and box") {
        LabeledSlider("Outline width", s.strokeWidth, 0f..0.3f, "${(s.strokeWidth * 100).toInt()}%") { vm.setStyle(s.copy(strokeWidth = it)) }
        Text("Outline colour", color = Adobe.TextDim, fontSize = 12.sp)
        ColorSwatches(s.strokeColor, SwatchColors) { vm.setStyle(s.copy(strokeColor = it)) }
        LabeledSlider("Box opacity", s.boxOpacity, 0f..1f, "${(s.boxOpacity * 100).toInt()}%") { vm.setStyle(s.copy(boxOpacity = it)) }
        Text("Box colour", color = Adobe.TextDim, fontSize = 12.sp)
        ColorSwatches(s.boxColor, SwatchColors) { vm.setStyle(s.copy(boxColor = it)) }
        LabeledSlider("Box padding", s.boxPadding, 0f..0.8f, "${(s.boxPadding * 100).toInt()}%") { vm.setStyle(s.copy(boxPadding = it)) }
    }
    Spacer10()
    Panel("Position") {
        LabeledSlider("Height from bottom", s.offsetY, 0f..0.6f, "${(s.offsetY * 100).toInt()}%") { vm.setStyle(s.copy(offsetY = it)) }
        Text("Position now: ${SegmentUtil.timecode(positionMs)}", color = Adobe.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun VoicePanel(project: Project, v: VoiceSettings, idle: Boolean, vm: EditorViewModel) {
    Panel("Voice") {
        Text("Voice engine", color = Adobe.TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TtsEngine.values().forEach { e -> FilterChip(selected = v.engine == e, onClick = { vm.setVoice(v.copy(engine = e)) }, label = { Text(e.label) }) }
        }
        Text(
            "The phone voice is free and works offline, but needs a ${project.targetLang.label} voice installed in the phone's Text-to-speech settings. " +
                "Gemini voice needs a free Gemini key. The other engine is tried automatically if one fails.",
            color = Adobe.TextDim, fontSize = 12.sp
        )
        LabeledSlider("Voice speed", v.speechRate, 0.6f..1.6f, String.format(Locale.US, "%.2fx", v.speechRate)) { vm.setVoice(v.copy(speechRate = it)) }
        LabeledSlider("Voice pitch", v.pitch, 0.7f..1.4f, String.format(Locale.US, "%.2f", v.pitch)) { vm.setVoice(v.copy(pitch = it)) }
    }
    Spacer10()
    Panel("Mixing") {
        Text("Original sound", color = Adobe.TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioMode.values().forEach { m -> FilterChip(selected = v.audioMode == m, onClick = { vm.setVoice(v.copy(audioMode = m)) }, label = { Text(m.label, fontSize = 12.sp) }) }
        }
        if (v.audioMode == AudioMode.MIX) {
            LabeledSlider("Original volume under the voice", v.originalVolume, 0f..1f, "${(v.originalVolume * 100).toInt()}%") { vm.setVoice(v.copy(originalVolume = it)) }
        }
    }
    Spacer10()
    Button(onClick = vm::generateVoice, enabled = idle && project.segments.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Adobe.Blue)) {
        Text(if (project.dubPath == null) "Generate voice track" else "Generate voice track again")
    }
    if (project.dubPath != null) Text("A voice track is saved with this project.", color = Adobe.Green, fontSize = 12.sp)
}

@Composable
private fun ExportPanel(project: Project, v: VoiceSettings, exported: String?, idle: Boolean, vm: EditorViewModel, onSaveSrt: () -> Unit) {
    val ctx = LocalContext.current
    Panel("Export video") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Burn captions into the video", Modifier.weight(1f))
            Switch(checked = v.burnCaptions, onCheckedChange = { vm.setVoice(v.copy(burnCaptions = it)) })
        }
        Text("Resolution", color = Adobe.TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 0 to "Original").forEach { (h, name) ->
                FilterChip(selected = v.outHeight == h, onClick = { vm.setVoice(v.copy(outHeight = h)) }, label = { Text(name) })
            }
        }
        Text(
            "Audio: ${v.audioMode.label}. Captions: ${project.displayMode.label}. Keep the app open while exporting; the screen stays on.",
            color = Adobe.TextDim, fontSize = 12.sp
        )
        Button(onClick = vm::export, enabled = idle, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Adobe.Orange, contentColor = androidx.compose.ui.graphics.Color.Black)) {
            Icon(Icons.Filled.Download, null); Text("  Export video")
        }
    }
    Spacer10()
    if (exported != null) {
        Panel("Finished video") {
            Text("Saved in your gallery (Movies/Movie-Recapee).", color = Adobe.Green, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(exported), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Filled.PlayArrow, null); Text("  Open") }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, Uri.parse(exported)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Filled.Share, null); Text("  Share") }
            }
        }
        Spacer10()
    }
    Panel("Subtitle file") {
        Text("Save the captions as an .srt file that works in other editors.", color = Adobe.TextDim, fontSize = 12.sp)
        OutlinedButton(onClick = onSaveSrt, enabled = project.segments.isNotEmpty()) { Text("Save .srt") }
    }
}
