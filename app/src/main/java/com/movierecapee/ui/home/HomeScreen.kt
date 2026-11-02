package com.movierecapee.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movierecapee.domain.Project
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.ui.theme.Adobe
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit, vm: HomeViewModel = koinViewModel()) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<Project?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.import(uri, onOpen)
        }
    }

    Scaffold(
        containerColor = Adobe.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Movie, null, tint = Adobe.Orange)
                        Text("  Movie-Recapee", fontWeight = FontWeight.Bold)
                    }
                },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Adobe.Panel)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!importing) picker.launch(arrayOf("video/*")) },
                containerColor = Adobe.Blue, contentColor = androidx.compose.ui.graphics.Color.White,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(if (importing) "Importing…" else "Import video") }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true, placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Clear") } }
            )
            message?.let {
                Text(it, color = Adobe.Red, modifier = Modifier.clickable { vm.clearMessage() })
            }
            if (importing) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            if (projects.isEmpty() && !importing) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Movie, null, tint = Adobe.TextDim, modifier = Modifier.size(64.dp))
                    Text("No projects yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Import a video you own or have a licence for. Movie-Recapee makes subtitles, a translation and a voice track for it, then exports a finished video.",
                        color = Adobe.TextDim, modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(projects, key = { it.id }) { p -> ProjectRow(p, onClick = { onOpen(p.id) }, onDelete = { toDelete = p }) }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete project?") },
            text = { Text("\"${p.title}\" and its subtitles and voice track are removed from this app. Your original video file is not touched.") },
            confirmButton = { TextButton(onClick = { vm.delete(p); toDelete = null }) { Text("Delete", color = Adobe.Red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProjectRow(p: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    val thumb = remember(p.thumbPath) { p.thumbPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Adobe.Panel).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(108.dp).height(62.dp).clip(RoundedCornerShape(6.dp)).background(Adobe.Panel2), contentAlignment = Alignment.Center) {
            if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Movie, null, tint = Adobe.TextDim)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${SegmentUtil.timecode(p.durationMs, false)}  ·  ${p.sourceLang.label} → ${p.targetLang.label}",
                color = Adobe.TextDim, fontSize = 12.sp
            )
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.updatedAt)),
                color = Adobe.TextDim, fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.segments.isNotEmpty()) Tag("Subtitles", Adobe.BlueLight)
                if (p.segments.any { it.isTranslated() }) Tag("Translated", Adobe.Orange)
                if (p.dubPath != null) Tag("Voice", Adobe.Green)
                if (p.outputUri != null) Tag("Exported", Adobe.Green)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete project", tint = Adobe.TextDim) }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
