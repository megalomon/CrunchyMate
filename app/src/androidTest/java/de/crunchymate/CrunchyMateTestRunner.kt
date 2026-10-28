package de.crunchymate

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

class CrunchyMateTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, TestCrunchyMateApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle?) {
        // Läuft vor Application.onCreate; der Kern öffnet die Datei erst beim ersten Zugriff
        installFixtureDatabase()
        super.onCreate(arguments)
    }

    private fun installFixtureDatabase() {
        TestStorage.requireTestDatabase(TestStorage.DB_NAME)
        val database = targetContext.getDatabasePath(TestStorage.DB_NAME)
        database.parentFile?.mkdirs()
        // WAL-Reste eines früheren Laufs würden zur neuen Datei nicht passen
        listOf("", "-wal", "-shm").forEach { suffix -> database.resolveSibling(database.name + suffix).delete() }
        context.assets.open(TestStorage.FIXTURE_ASSET).use { input ->
            database.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
