package de.crunchymate

/** Namen der Test-Dateien; sie dürfen nie mit den Produktionsnamen zusammenfallen. */
object TestStorage {
    const val DB_NAME = "crunchymate-test.db"
    const val USER_DB_NAME = "crunchymate-user-test.db"
    const val IMAGE_DIR = "images-test"
    const val FIXTURE_ASSET = "fixture.db"

    // Absichtlich als Literale: ein Verweis auf die Produktionskonstanten würde eine Umbenennung dort mitziehen
    // und den Schutz damit unwirksam machen.
    private val productionDatabaseNames = setOf("crunchymate.db", "crunchymate-user.db")
    private val productionImageDirs = setOf("images")

    init {
        require(DB_NAME !in productionDatabaseNames) { "Test-Datenbank darf nicht die Produktionsdatenbank sein: $DB_NAME" }
        require(IMAGE_DIR !in productionImageDirs) { "Test-Bilderordner darf nicht der Produktionsordner sein: $IMAGE_DIR" }
    }

    /** Vom Runner vor dem Löschen und Überschreiben aufgerufen, damit nie eine Produktionsdatei betroffen ist. */
    fun requireTestDatabase(name: String) {
        require(name !in productionDatabaseNames) { "Zugriff auf Produktionsdatenbank im Test verweigert: $name" }
    }
}
