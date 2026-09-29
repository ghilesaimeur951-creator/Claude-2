package com.streetblocks.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.streetblocks.app.data.db.AppDatabase
import com.streetblocks.app.data.db.PlannedSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Une base v1 existante (version 1.0 de l'appli) doit passer en v2 sans perte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    @Test
    fun v1DatabaseMigratesWithoutLosingData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val file = ctx.getDatabasePath("migration-test.db")
        file.parentFile?.mkdirs()
        file.delete()

        // Schéma exact de la v1 (généré par Room pour la version 1.0)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `blocksJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `lastPerformedAt` INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, `workoutName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationSec` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `logsJson` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `bands` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `minKg` INTEGER NOT NULL, `maxKg` INTEGER NOT NULL, `usages` TEXT NOT NULL, `note` TEXT NOT NULL)")
            db.execSQL("INSERT INTO workouts (name, description, blocksJson, createdAt, updatedAt) VALUES ('Ma séance', '', '[]', 1, 1)")
            db.execSQL("INSERT INTO sessions (workoutId, workoutName, startedAt, durationSec, completed, logsJson) VALUES (1, 'Ma séance', 5, 600, 1, '[]')")
            db.execSQL("INSERT INTO bands (name, colorHex, minKg, maxKg, usages, note) VALUES ('Noir', '#000000', 15, 25, 'TRACTION', '')")
            db.version = 1
        }

        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, "migration-test.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        // Données v1 conservées
        assertEquals("Ma séance", room.workoutDao().get(1)?.name)
        val sessions = room.sessionDao().observeAll().first()
        assertEquals(1, sessions.size)
        assertEquals(null, sessions.first().plannedId)
        assertEquals(1, room.bandDao().count())

        // Nouvelles tables fonctionnelles
        val pid = room.programDao().insertProgram(
            com.streetblocks.app.data.db.ProgramEntity(name = "P", goal = "Force", startEpochDay = 0, endEpochDay = 6, trainingDays = "1,3,5", createdAt = 0)
        )
        room.programDao().insertPlanned(listOf(PlannedSessionEntity(programId = pid, seriesId = "s", dateEpochDay = 0, name = "S", blocksJson = "[]")))
        assertEquals(1, room.programDao().plannedOf(pid).size)
        room.close()
    }
}
