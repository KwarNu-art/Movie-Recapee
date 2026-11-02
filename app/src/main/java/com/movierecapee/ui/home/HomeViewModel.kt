package com.movierecapee.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movierecapee.data.local.ProjectRepository
import com.movierecapee.data.media.MediaTools
import com.movierecapee.domain.Project
import com.movierecapee.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeViewModel(private val context: Context, private val repo: ProjectRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val importing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val projects: StateFlow<List<Project>> = combine(repo.observeAll(), query) { all, q ->
        if (q.isBlank()) all else all.filter { it.title.contains(q.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun import(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            importing.value = true
            message.value = null
            try {
                val info = withContext(Dispatchers.IO) { MediaTools.readInfo(context, uri) }
                val id = repo.save(
                    Project(
                        title = info.title, videoUri = uri.toString(), durationMs = info.durationMs,
                        width = info.width, height = info.height, rotation = info.rotation
                    )
                )
                info.thumb?.let { bmp ->
                    val f = File(context.filesDir, "thumbs/$id.jpg")
                    withContext(Dispatchers.IO) { MediaTools.saveJpeg(bmp, f) }
                    repo.get(id)?.let { repo.save(it.copy(thumbPath = f.absolutePath)) }
                }
                onDone(id)
            } catch (e: Exception) {
                message.value = "Could not import this video: ${e.userMessage()}"
            } finally {
                importing.value = false
            }
        }
    }

    fun delete(p: Project) {
        viewModelScope.launch { repo.delete(p.id) }
    }

    fun clearMessage() { message.value = null }
}
