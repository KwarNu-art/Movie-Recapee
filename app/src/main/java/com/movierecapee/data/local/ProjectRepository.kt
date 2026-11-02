package com.movierecapee.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.movierecapee.domain.CaptionStyle
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.Lang
import com.movierecapee.domain.Project
import com.movierecapee.domain.SubtitleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class ProjectRepository(private val context: Context, private val dao: ProjectDao) {
    private val gson = Gson()
    private val segType = object : TypeToken<List<SubtitleSegment>>() {}.type

    fun observeAll(): Flow<List<Project>> = dao.observeAll().map { list -> list.map(::toDomain) }

    suspend fun get(id: Long): Project? = withContext(Dispatchers.IO) { dao.get(id)?.let(::toDomain) }

    suspend fun save(p: Project): Long = withContext(Dispatchers.IO) {
        dao.upsert(toEntity(p.copy(updatedAt = System.currentTimeMillis())))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.get(id)?.thumbPath?.let { File(it).delete() }
        File(context.filesDir, "projects/$id").deleteRecursively()
        dao.delete(id)
    }

    fun projectDir(id: Long): File = File(context.filesDir, "projects/$id").apply { mkdirs() }

    private fun toEntity(p: Project) = ProjectEntity(
        id = p.id, title = p.title, videoUri = p.videoUri, durationMs = p.durationMs,
        width = p.width, height = p.height, rotation = p.rotation,
        sourceLang = p.sourceLang.code, targetLang = p.targetLang.code, displayMode = p.displayMode.name,
        styleJson = gson.toJson(p.style), segmentsJson = gson.toJson(p.segments),
        dubPath = p.dubPath, outputUri = p.outputUri, thumbPath = p.thumbPath,
        createdAt = p.createdAt, updatedAt = p.updatedAt
    )

    private fun toDomain(e: ProjectEntity) = Project(
        id = e.id, title = e.title, videoUri = e.videoUri, durationMs = e.durationMs,
        width = e.width, height = e.height, rotation = e.rotation,
        sourceLang = Lang.fromCode(e.sourceLang), targetLang = Lang.fromCode(e.targetLang),
        displayMode = runCatching { DisplayMode.valueOf(e.displayMode) }.getOrDefault(DisplayMode.TARGET),
        style = runCatching { gson.fromJson(e.styleJson, CaptionStyle::class.java) }.getOrNull() ?: CaptionStyle(),
        segments = runCatching { gson.fromJson<List<SubtitleSegment>>(e.segmentsJson, segType) }.getOrNull() ?: emptyList(),
        dubPath = e.dubPath, outputUri = e.outputUri, thumbPath = e.thumbPath,
        createdAt = e.createdAt, updatedAt = e.updatedAt
    )
}
