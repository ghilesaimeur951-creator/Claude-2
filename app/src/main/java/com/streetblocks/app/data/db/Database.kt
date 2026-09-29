package com.streetblocks.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Séance planifiée d'un programme à l'origine de cette séance (v2) */
    val plannedId: Long? = null,
)

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val goal: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    /** Jours d'entraînement, numéros ISO (1 = lundi) séparés par des virgules */
    val trainingDays: String,
    val createdAt: Long,
)

@Entity(
    tableName = "planned_sessions",
    indices = [Index("programId"), Index("dateEpochDay")],
)
data class PlannedSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val seriesId: String,
    val dateEpochDay: Long,
    val timeMinutes: Int? = null,
    val name: String,
    val blocksJson: String,
    val sourceWorkoutId: Long? = null,
    val status: String = "PLANNED",
    val sessionId: Long? = null,
    val completedAt: Long? = null,
    val note: String = "",
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

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY startEpochDay DESC, id DESC")
    fun observePrograms(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observeProgram(id: Long): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getProgram(id: Long): ProgramEntity?

    @Insert
    suspend fun insertProgram(e: ProgramEntity): Long

    @Update
    suspend fun updateProgram(e: ProgramEntity)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("SELECT * FROM planned_sessions ORDER BY dateEpochDay, timeMinutes")
    fun observeAllPlanned(): Flow<List<PlannedSessionEntity>>

    @Query("SELECT * FROM planned_sessions WHERE programId = :programId ORDER BY dateEpochDay, timeMinutes")
    fun observePlanned(programId: Long): Flow<List<PlannedSessionEntity>>

    @Query("SELECT * FROM planned_sessions WHERE programId = :programId ORDER BY dateEpochDay, timeMinutes")
    suspend fun plannedOf(programId: Long): List<PlannedSessionEntity>

    @Query("SELECT * FROM planned_sessions WHERE id = :id")
    suspend fun getPlanned(id: Long): PlannedSessionEntity?

    @Insert
    suspend fun insertPlanned(list: List<PlannedSessionEntity>): List<Long>

    @Update
    suspend fun updatePlanned(list: List<PlannedSessionEntity>)

    @Query("DELETE FROM planned_sessions WHERE id IN (:ids)")
    suspend fun deletePlanned(ids: List<Long>)

    @Query("DELETE FROM planned_sessions WHERE programId = :programId")
    suspend fun deletePlannedOfProgram(programId: Long)
}

@Database(
    entities = [WorkoutEntity::class, SessionEntity::class, BandEntity::class, ProgramEntity::class, PlannedSessionEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun sessionDao(): SessionDao
    abstract fun bandDao(): BandDao
    abstract fun programDao(): ProgramDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2 : ajout des programmes. Aucune donnée existante n'est touchée. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `goal` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, " +
                        "`endEpochDay` INTEGER NOT NULL, `trainingDays` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `planned_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`programId` INTEGER NOT NULL, `seriesId` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                        "`timeMinutes` INTEGER, `name` TEXT NOT NULL, `blocksJson` TEXT NOT NULL, `sourceWorkoutId` INTEGER, " +
                        "`status` TEXT NOT NULL, `sessionId` INTEGER, `completedAt` INTEGER, `note` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_programId` ON `planned_sessions` (`programId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_sessions_dateEpochDay` ON `planned_sessions` (`dateEpochDay`)")
                db.execSQL("ALTER TABLE `sessions` ADD COLUMN `plannedId` INTEGER")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "streetblocks.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
