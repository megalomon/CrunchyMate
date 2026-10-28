package de.crunchymate.sync

import de.crunchymate.core.ffi.ErrorCode

/** Darstellungszustand der Synchronisation; Zeitpunkte als Epoch-Millisekunden. */
data class SyncUiState(
    val running: Boolean,
    val done: Long,
    val total: Long,
    val lastFinishedAt: Long?,
    val lastError: ErrorCode?,
) {
    companion object {
        val Idle = SyncUiState(running = false, done = 0, total = 0, lastFinishedAt = null, lastError = null)
    }
}
