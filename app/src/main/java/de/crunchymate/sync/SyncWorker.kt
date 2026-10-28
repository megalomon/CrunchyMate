package de.crunchymate.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.crunchymate.CrunchyMateApplication
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.code
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.core.ffi.SyncObserver
import de.crunchymate.core.ffi.SyncProgress
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Höchstens eine Fortschrittsmeldung pro Intervall: Der Kern meldet je Seite bzw. Batch. */
const val PROGRESS_THROTTLE = 1_000L
private const val LOG_TAG = "CrunchyMate"

/**
 * Führt einen Katalog-Sync oder eine AniList-Anreicherung als Vordergrunddienst aus.
 * Ein Abbruch der Arbeit bricht die Coroutine ab; das verwirft die Rust-Future und damit den Lauf.
 */
class SyncWorker internal constructor(
    context: Context,
    params: WorkerParameters,
    private val gatewayProvider: () -> CoreGateway,
) : CoroutineWorker(context, params) {
    constructor(context: Context, params: WorkerParameters) :
        this(context, params, { (context.applicationContext as CrunchyMateApplication).container.gateway })

    private val kind: SyncKind? = inputData.getString(KEY_KIND)?.let { name -> SyncKind.entries.find { it.name == name } }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(kind ?: SyncKind.CATALOG, done = 0, total = 0)

    override suspend fun doWork(): Result {
        val kind = kind ?: return Result.failure()
        val gateway = gatewayProvider()
        SyncNotifications.ensureChannel(applicationContext)
        updateForeground(kind, done = 0, total = 0)
        // Der Kern ruft den Observer aus eigenen Threads; die StateFlow übergibt den jeweils neuesten Stand an die Coroutine
        val latest = MutableStateFlow<SyncProgress?>(null)
        val observer =
            object : SyncObserver {
                override fun onProgress(progress: SyncProgress) {
                    latest.value = progress
                }
            }
        return try {
            coroutineScope {
                val reporter =
                    launch {
                        latest.filterNotNull().collect { progress ->
                            report(gateway, progress)
                            delay(PROGRESS_THROTTLE)
                        }
                    }
                try {
                    when (kind) {
                        SyncKind.CATALOG -> gateway.core.syncCatalog(observer)
                        SyncKind.METADATA -> gateway.core.enrichMetadata(observer)
                    }
                } finally {
                    reporter.cancel()
                }
            }
            Result.success()
        } catch (e: CoreException) {
            Result.failure(workDataOf(KEY_ERROR to e.code.name))
        } finally {
            gateway.notifyChanged()
        }
    }

    private suspend fun report(
        gateway: CoreGateway,
        progress: SyncProgress,
    ) {
        setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        updateForeground(progress.kind, progress.done, progress.total)
        gateway.notifyChanged()
    }

    private suspend fun updateForeground(
        kind: SyncKind,
        done: Long,
        total: Long,
    ) {
        try {
            setForeground(foregroundInfo(kind, done, total))
        } catch (e: IllegalStateException) {
            // Ohne Vordergrunddienst läuft der Sync weiter, nur ohne Benachrichtigung
            Log.w(LOG_TAG, "Vordergrunddienst nicht verfügbar: ${e::class.simpleName}")
        }
    }

    private fun foregroundInfo(
        kind: SyncKind,
        done: Long,
        total: Long,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = SyncNotifications.build(applicationContext, kind, done, total, cancelIntent)
        return ForegroundInfo(
            SyncNotifications.notificationId(kind),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
    }
}
