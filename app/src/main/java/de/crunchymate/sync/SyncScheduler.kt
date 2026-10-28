package de.crunchymate.sync

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.ErrorCode
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncPhase
import de.crunchymate.core.ffi.SyncRunState
import de.crunchymate.core.ffi.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

private const val LOG_TAG = "CrunchyMate"
private const val NO_PROGRESS = -1L

/**
 * Startet und beobachtet Synchronisationen über WorkManager. Ob ein Lauf aktiv ist, entscheidet
 * allein WorkManager; so bleibt nach einem Prozessende kein veralteter Laufzustand des Kerns sichtbar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncScheduler(
    private val workManager: WorkManager,
    private val gateway: CoreGateway,
) {
    private val changesWithStart = gateway.changes.onStart { emit(Unit) }

    /** Der Rückgabewert erlaubt, das Einreihen abzuwarten. */
    fun start(kind: SyncKind): Operation {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SyncWorker.KEY_KIND to kind.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        return workManager.enqueueUniqueWork(uniqueName(kind), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(kind: SyncKind) {
        workManager.cancelUniqueWork(uniqueName(kind))
    }

    fun observe(kind: SyncKind): Flow<SyncUiState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(uniqueName(kind)), changesWithStart) { infos, _ -> infos }
            .mapLatest { infos -> toUiState(infos.firstOrNull { it.isActive() }, workerError(infos), status(kind)) }
            .distinctUntilChanged()

    val anyRunning: Flow<Boolean> =
        combine(isRunning(SyncKind.CATALOG), isRunning(SyncKind.METADATA)) { catalog, metadata -> catalog || metadata }
            .distinctUntilChanged()

    /** Vom Kern beim Katalog-Sync erkanntes Land (ISO-Code), null vor dem ersten Lauf. */
    val catalogCountry: Flow<String?> =
        changesWithStart.map { status(SyncKind.CATALOG)?.catalogCountry }.distinctUntilChanged()

    private fun isRunning(kind: SyncKind): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueName(kind)).map { infos -> infos.any { it.isActive() } }

    private suspend fun status(kind: SyncKind): SyncStatus? =
        try {
            gateway.syncStatus(kind)
        } catch (e: CoreException) {
            Log.w(LOG_TAG, "Sync-Status konnte nicht gelesen werden: ${e::class.simpleName}")
            null
        }

    // WorkManager entfernt beim erneuten Einreihen die beendete Arbeit; die Liste beschreibt also den letzten Lauf
    private fun workerError(infos: List<WorkInfo>): ErrorCode? =
        infos
            .firstOrNull { it.state == WorkInfo.State.FAILED }
            ?.outputData
            ?.getString(SyncWorker.KEY_ERROR)
            ?.let { name -> ErrorCode.entries.find { it.name == name } }

    private fun toUiState(
        active: WorkInfo?,
        workerError: ErrorCode?,
        status: SyncStatus?,
    ): SyncUiState {
        val progressDone = active?.progress?.getLong(SyncWorker.KEY_DONE, NO_PROGRESS) ?: NO_PROGRESS
        val progressTotal = active?.progress?.getLong(SyncWorker.KEY_TOTAL, NO_PROGRESS) ?: NO_PROGRESS
        val phase = active?.progress?.getString(SyncWorker.KEY_PHASE)?.let { name -> SyncPhase.entries.find { it.name == name } }
        val hasProgress = progressDone != NO_PROGRESS && progressTotal != NO_PROGRESS
        val coreFailed = status?.state == SyncRunState.FAILED
        // Scheitert der Worker an einem Fehler, den der Kern nicht festhalten konnte, verwürfe dieser den Lauf als
        // abgebrochen; maßgeblich ist dann der Fehler des Workers
        val workerFailure = workerError.takeIf { active == null && !coreFailed }
        val summary = status?.lastSummary.takeIf { active == null }
        return SyncUiState(
            running = active != null,
            // Läuft die Arbeit, aber ohne erste Meldung, wären die Zähler des Vorlaufs irreführend
            done = if (hasProgress) progressDone else idleCount(active, status?.done),
            total = if (hasProgress) progressTotal else idleCount(active, status?.total),
            lastFinishedAt = status?.lastFinishedAt,
            // Der Kern speichert auch beim Abbruch einen Fehlercode; angezeigt wird er nur bei echtem Fehlschlag
            lastError = if (coreFailed) status?.lastError else workerFailure,
            cancelled = active == null && status?.state == SyncRunState.CANCELLED && workerFailure == null,
            phase = phase ?: SyncPhase.ITEMS,
            failedImages = summary?.failedImages ?: 0,
            skippedImages = summary?.skippedImages ?: 0,
            failedBatches = summary?.failedBatches ?: 0,
        )
    }

    private fun idleCount(
        active: WorkInfo?,
        stored: Long?,
    ): Long = if (active != null) 0L else stored ?: 0L

    private fun WorkInfo.isActive(): Boolean = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED

    private companion object {
        fun uniqueName(kind: SyncKind) = "sync-${kind.name}"
    }
}
