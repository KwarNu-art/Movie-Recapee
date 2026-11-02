package com.movierecapee.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val sourceLang: String,
    val targetLang: String,
    val displayMode: String,
    val styleJson: String,
    val segmentsJson: String,
    val dubPath: String?,
    val outputUri: String?,
    val thumbPath: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
}
