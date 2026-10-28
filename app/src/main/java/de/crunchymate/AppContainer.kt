package de.crunchymate

import android.content.Context
import androidx.work.WorkManager
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.Core
import de.crunchymate.core.ffi.CoreInterface
import de.crunchymate.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class AppContainer(
    context: Context,
    // Eigene Namen erlauben den Geräte-Tests, die echten Nutzerdaten unberührt zu lassen
    private val databaseName: String = DEFAULT_DB_NAME,
    private val userDatabaseName: String = DEFAULT_USER_DB_NAME,
    private val imageDirName: String = DEFAULT_IMAGE_DIR,
) {
    private val appContext = context.applicationContext

    val core: CoreInterface by lazy {
        val dbFile = appContext.getDatabasePath(databaseName)
        // Das Verzeichnis "databases" legt Android erst beim ersten SQLiteOpenHelper an
        dbFile.parentFile?.mkdirs()
        val userDbFile = appContext.getDatabasePath(userDatabaseName)
        Core(dbFile.path, userDbFile.path, File(appContext.filesDir, imageDirName).absolutePath)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val gateway by lazy { CoreGateway(core, Dispatchers.IO) }

    val settingsStore by lazy { SettingsStore(gateway, applicationScope, Dispatchers.IO) }

    val workManager by lazy { WorkManager.getInstance(appContext) }

    val syncScheduler by lazy { SyncScheduler(workManager, gateway) }

    internal companion object {
        const val DEFAULT_DB_NAME = "crunchymate.db"

        // Einzige Datei im Backup (data_extraction_rules.xml); eine Umbenennung muss dort nachgezogen werden
        const val DEFAULT_USER_DB_NAME = "crunchymate-user.db"
        const val DEFAULT_IMAGE_DIR = "images"
    }
}
