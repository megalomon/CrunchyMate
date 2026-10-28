package de.crunchymate

import android.content.Context
import de.crunchymate.core.ffi.Core
import de.crunchymate.core.ffi.CoreInterface

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

    private companion object {
        const val DB_NAME = "crunchymate.db"
    }
}
