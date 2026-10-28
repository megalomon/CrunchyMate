package de.crunchymate.sync

import androidx.annotation.StringRes
import de.crunchymate.R
import de.crunchymate.core.ffi.ErrorCode
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase

/** Darstellungszustand der Synchronisation; Zeitpunkte als Unix-Sekunden (wie im Rust-Kern). lastError nur bei fehlgeschlagenem Lauf. */
data class SyncUiState(
    val running: Boolean,
    val done: Long,
    val total: Long,
    val lastFinishedAt: Long?,
    val lastError: ErrorCode?,
    /** Der letzte Lauf wurde abgebrochen (vom Nutzer oder durch Ende des Prozesses); kein Fehler. */
    val cancelled: Boolean = false,
    /** Phase des laufenden Laufs; Done und Total beziehen sich auf diese Phase. */
    val phase: SyncPhase = SyncPhase.ITEMS,
    /** Zähler des letzten beendeten Laufs; während eines Laufs 0. */
    val failedImages: Int = 0,
    val skippedImages: Int = 0,
    val failedBatches: Int = 0,
) {
    companion object {
        val Idle = SyncUiState(running = false, done = 0, total = 0, lastFinishedAt = null, lastError = null)
    }
}

/** Kurzbezeichnung der Phase; die Eintragsphase heißt je nach Art Katalog oder Anreicherung. */
@StringRes
fun syncPhaseLabel(
    kind: SyncKind,
    phase: SyncPhase,
): Int =
    when (phase) {
        SyncPhase.IMAGES -> R.string.sync_phase_images
        SyncPhase.ITEMS ->
            when (kind) {
                SyncKind.CATALOG -> R.string.sync_phase_catalog
                SyncKind.METADATA -> R.string.sync_phase_enrichment
            }
    }
