package de.crunchymate

import android.content.Context
import androidx.work.WorkManager
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.Core
import de.crunchymate.core.ffi.CoreInterface
import de.crunchymate.sync.SyncScheduler
import de.crunchymate.ui.licenses.AssetNoticesSource
import de.crunchymate.ui.licenses.NoticesSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(
    context: Context,
    // Eigene Namen erlauben den Geräte-Tests, die echten Nutzerdaten unberührt zu lassen
    private val databaseName: String = DEFAULT_DB_NAME,
    private val userDatabaseName: String = DEFAULT_USER_DB_NAME,
    private val imageDirName: String = DEFAULT_IMAGE_DIR,
    private val coreFactory: (databasePath: String, userDatabasePath: String, imageDirPath: String) -> CoreInterface = ::Core,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    private val lazyCore =
        lazy {
            val dbFile = appContext.getDatabasePath(databaseName)
            // Das Verzeichnis "databases" legt Android erst beim ersten SQLiteOpenHelper an
            dbFile.parentFile?.mkdirs()
            val userDbFile = appContext.getDatabasePath(userDatabaseName)
            coreFactory(dbFile.path, userDbFile.path, File(appContext.filesDir, imageDirName).absolutePath)
        }

    val core: CoreInterface by lazyCore

    /** Öffnet den Kern abseits des Main-Threads (Migration, TLS-Aufbau); wirft, wenn er nicht entsteht. */
    suspend fun initializeCore() {
        withContext(io) { core }
    }

    /** Speichert gedrosselte Einstellungen; ohne geöffneten Kern gibt es nichts zu sichern. */
    suspend fun flushSettings() {
        if (lazyCore.isInitialized()) settingsStore.flush()
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val gateway by lazy { CoreGateway(core, io) }

    val settingsStore by lazy { SettingsStore(gateway, applicationScope, io) }

    val noticesSource: NoticesSource by lazy { AssetNoticesSource(appContext.assets, io) }

    val workManager by lazy { WorkManager.getInstance(appContext) }

    val syncScheduler by lazy { SyncScheduler(workManager, gateway) }

    internal companion object {
        const val DEFAULT_DB_NAME = "crunchymate.db"

        // Einzige Datei im Backup (data_extraction_rules.xml); eine Umbenennung muss dort nachgezogen werden
        const val DEFAULT_USER_DB_NAME = "crunchymate-user.db"
        const val DEFAULT_IMAGE_DIR = "images"
    }
}
