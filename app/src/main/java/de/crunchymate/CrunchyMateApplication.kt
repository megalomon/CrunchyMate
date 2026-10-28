package de.crunchymate

import android.app.Application

class CrunchyMateApplication : Application() {
    // Genau ein Container pro Prozess: Core::new setzt verwaiste Sync-Läufe zurück
    // und darf deshalb nicht neben einer bereits laufenden Instanz erneut entstehen.
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
