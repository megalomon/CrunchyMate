package de.crunchymate

import android.content.Context
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.Core
import de.crunchymate.core.ffi.CoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val core: CoreInterface by lazy {
        val dbFile = appContext.getDatabasePath(DB_NAME)
        // Das Verzeichnis "databases" legt Android erst beim ersten SQLiteOpenHelper an
        dbFile.parentFile?.mkdirs()
        Core(dbFile.path)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val gateway by lazy { CoreGateway(core, Dispatchers.IO) }

    val settingsStore by lazy { SettingsStore(gateway, applicationScope, Dispatchers.IO) }

    private companion object {
        const val DB_NAME = "crunchymate.db"
    }
}
