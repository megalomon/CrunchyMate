package de.crunchymate.sync

import de.crunchymate.core.ffi.ErrorCode

/** Darstellungszustand der Synchronisation; Zeitpunkte als Unix-Sekunden (wie im Rust-Kern). lastError nur bei fehlgeschlagenem Lauf. */
data class SyncUiState(
    val running: Boolean,
    val done: Long,
    val total: Long,
    val lastFinishedAt: Long?,
    val lastError: ErrorCode?,
    /** Der letzte Lauf wurde abgebrochen (vom Nutzer oder durch Ende des Prozesses); kein Fehler. */
    val cancelled: Boolean = false,
) {
    companion object {
        val Idle = SyncUiState(running = false, done = 0, total = 0, lastFinishedAt = null, lastError = null)
    }
}
