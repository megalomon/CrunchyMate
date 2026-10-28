package de.crunchymate.sync

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.SyncKind
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

    fun start(kind: SyncKind) {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SyncWorker.KEY_KIND to kind.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        workManager.enqueueUniqueWork(uniqueName(kind), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(kind: SyncKind) {
        workManager.cancelUniqueWork(uniqueName(kind))
    }

    fun observe(kind: SyncKind): Flow<SyncUiState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(uniqueName(kind)), changesWithStart) { infos, _ -> infos }
            .mapLatest { infos -> toUiState(infos.firstOrNull { it.isActive() }, status(kind)) }
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

    private fun toUiState(
        active: WorkInfo?,
        status: SyncStatus?,
    ): SyncUiState {
        val progressDone = active?.progress?.getLong(SyncWorker.KEY_DONE, NO_PROGRESS) ?: NO_PROGRESS
        val progressTotal = active?.progress?.getLong(SyncWorker.KEY_TOTAL, NO_PROGRESS) ?: NO_PROGRESS
        val hasProgress = progressDone != NO_PROGRESS && progressTotal != NO_PROGRESS
        return SyncUiState(
            running = active != null,
            done = if (hasProgress) progressDone else status?.done ?: 0L,
            total = if (hasProgress) progressTotal else status?.total ?: 0L,
            lastFinishedAt = status?.lastFinishedAt,
            // Der Kern speichert auch beim Abbruch einen Fehlercode; angezeigt wird er nur bei echtem Fehlschlag
            lastError = status?.lastError?.takeIf { status.state == SyncRunState.FAILED },
            cancelled = active == null && status?.state == SyncRunState.CANCELLED,
        )
    }

    private fun WorkInfo.isActive(): Boolean = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED

    private companion object {
        fun uniqueName(kind: SyncKind) = "sync-${kind.name}"
    }
}
