package com.streetblocks.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val blocksJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long? = null,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val durationSec: Int,
    val completed: Boolean,
    val logsJson: String,
)

@Entity(tableName = "bands")
data class BandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val minKg: Int,
    val maxKg: Int,
    val usages: String,
    val note: String = "",
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Insert
    suspend fun insert(e: WorkoutEntity): Long

    @Update
    suspend fun update(e: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE workouts SET lastPerformedAt = :time WHERE id = :id")
    suspend fun markPerformed(id: Long, time: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(e: SessionEntity): Long

    @Query("UPDATE sessions SET logsJson = :json WHERE id = :id")
    suspend fun updateLogs(id: Long, json: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BandDao {
    @Query("SELECT * FROM bands ORDER BY maxKg ASC, minKg ASC")
    fun observeAll(): Flow<List<BandEntity>>

    @Query("SELECT * FROM bands ORDER BY maxKg ASC, minKg ASC")
    suspend fun getAll(): List<BandEntity>

    @Query("SELECT COUNT(*) FROM bands")
    suspend fun count(): Int

    @Insert
    suspend fun insert(e: BandEntity): Long

    @Update
    suspend fun update(e: BandEntity)

    @Query("DELETE FROM bands WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [WorkoutEntity::class, SessionEntity::class, BandEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun bandDao(): BandDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "streetblocks.db")
                    .build()
                    .also { instance = it }
            }
    }
}
