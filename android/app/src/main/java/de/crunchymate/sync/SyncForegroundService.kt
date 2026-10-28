package de.crunchymate.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.crunchymate.db.AndroidDatabaseDelegate
import de.crunchymate.db.AnimeRepository
import de.crunchymate.db.AppDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

class SyncForegroundService : Service() {

    fun interface SyncProgressListener {
        fun onProgress(phase: String, synced: Int, total: Int, isRunning: Boolean, error: String?)
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var _notificationManager: NotificationManager? = null
    var notificationManager: NotificationManager?
        get() {
            if (_notificationManager == null) {
                try {
                    _notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                } catch (_: Exception) {
                    // Tolerate headless test environment or unattached context
                }
            }
            return _notificationManager
        }
        set(value) {
            _notificationManager = value
        }

    @Volatile internal var isForegroundActive = false
    internal var startForegroundCount = 0
        private set

    private var _syncManager: SyncManager? = null
    var syncManager: SyncManager
        get() {
            return _syncManager ?: synchronized(this) {
                _syncManager ?: getSyncManager(this).also { _syncManager = it }
            }
        }
        set(value) {
            _syncManager = value
        }

    inner class LocalBinder : Binder() {
        fun getService(): SyncForegroundService = this@SyncForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
        } catch (_: Exception) {
            // Tolerate headless test environment
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            val limit = intent.getIntExtra(EXTRA_LIMIT, 0)
            handleAction(action, limit)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isForegroundActive = false
    }

    fun handleAction(action: String?, limit: Int = 0): Boolean {
        when (action) {
            ACTION_START_CR_SYNC -> {
                startForegroundServiceIfNeeded("Crunchyroll-Katalog")
                try {
                    val job = syncManager.startCrunchyrollSync(limit = limit) { synced, total ->
                        val text = formatCrunchyrollProgress(synced, total)
                        updateNotification("CrunchyMate: Synchronisiere...", text, synced, total, total <= 0)
                        broadcastProgress("crunchyroll", synced, total, true, null)
                    }
                    serviceScope.launch {
                        try {
                            job.join()
                        } finally {
                            broadcastCrStatus()
                            checkAndStopIfIdle()
                        }
                    }
                } catch (e: Exception) {
                    val crStatus = try { syncManager.getCrSyncStatus() } catch (_: Exception) { null }
                    val isRunning = crStatus?.optBoolean("is_running", false) ?: false
                    val synced = crStatus?.optInt("synced_count", 0) ?: 0
                    val total = crStatus?.optInt("total_items", 0) ?: 0
                    broadcastProgress("crunchyroll", synced, total, isRunning, e.message)
                    checkAndStopIfIdle()
                }
                return true
            }

            ACTION_START_ANI_SYNC -> {
                startForegroundServiceIfNeeded("AniList-Metadaten")
                try {
                    val job = syncManager.startAniListSync { done, total ->
                        val text = formatAniListProgress(done, total)
                        updateNotification("CrunchyMate: Synchronisiere...", text, done, total, total <= 0)
                        broadcastProgress("anilist", done, total, true, null)
                    }
                    serviceScope.launch {
                        try {
                            job.join()
                        } finally {
                            broadcastAniListStatus()
                            checkAndStopIfIdle()
                        }
                    }
                } catch (e: Exception) {
                    val aniStatus = try { syncManager.getAniListSyncStatus() } catch (_: Exception) { null }
                    val isRunning = aniStatus?.optBoolean("is_running", false) ?: false
                    val synced = aniStatus?.optInt("already_done", 0) ?: 0
                    val total = aniStatus?.optInt("total_in_db", 0) ?: 0
                    broadcastProgress("anilist", synced, total, isRunning, e.message)
                    checkAndStopIfIdle()
                }
                return true
            }

            ACTION_CANCEL_SYNC -> {
                val crWasRunning = try { syncManager.isCrSyncRunning() } catch (_: Exception) { false }
                val aniWasRunning = try { syncManager.isAniListSyncRunning() } catch (_: Exception) { false }
                if (crWasRunning) {
                    syncManager.cancelCrunchyrollSync()
                } else {
                    broadcastCrStatus()
                }
                if (aniWasRunning) {
                    syncManager.cancelAniListSync()
                } else {
                    broadcastAniListStatus()
                }
                checkAndStopIfIdle()
                return true
            }

            ACTION_CANCEL_CR -> {
                val crWasRunning = try { syncManager.isCrSyncRunning() } catch (_: Exception) { false }
                if (crWasRunning) {
                    syncManager.cancelCrunchyrollSync()
                } else {
                    broadcastCrStatus()
                }
                checkAndStopIfIdle()
                return true
            }

            ACTION_CANCEL_ANI -> {
                val aniWasRunning = try { syncManager.isAniListSyncRunning() } catch (_: Exception) { false }
                if (aniWasRunning) {
                    syncManager.cancelAniListSync()
                } else {
                    broadcastAniListStatus()
                }
                checkAndStopIfIdle()
                return true
            }

            else -> return false
        }
    }

    internal fun startForegroundServiceIfNeeded(initialPhase: String) {
        startForegroundCount++
        val initialText = "$initialPhase wird vorbereitet..."
        try {
            createNotificationChannel()
            val notification = buildNotification(
                title = "CrunchyMate: Synchronisiere...",
                content = initialText,
                progress = 0,
                max = 0,
                indeterminate = true
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (_: Exception) {
            // Tolerate unit test environment where Service.startForeground is stubbed
            isForegroundActive = true
        }
    }

    private fun broadcastCrStatus() {
        try {
            val crStatus = syncManager.getCrSyncStatus()
            val err = crStatus.optString("last_error", "").ifEmpty { null }
            broadcastProgress(
                "crunchyroll",
                crStatus.optInt("synced_count", 0),
                crStatus.optInt("total_items", 0),
                crStatus.optBoolean("is_running", false),
                err
            )
        } catch (_: Exception) {
            // Tolerate test or unattached context
        }
    }

    private fun broadcastAniListStatus() {
        try {
            val aniStatus = syncManager.getAniListSyncStatus()
            val err = aniStatus.optString("last_error", "").ifEmpty { null }
            broadcastProgress(
                "anilist",
                aniStatus.optInt("already_done", 0),
                aniStatus.optInt("total_in_db", 0),
                aniStatus.optBoolean("is_running", false),
                err
            )
        } catch (_: Exception) {
            // Tolerate test or unattached context
        }
    }

    private fun updateNotification(
        title: String,
        content: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = false
    ) {
        try {
            val notification = buildNotification(title, content, progress, max, indeterminate)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Tolerate unit test environment
        }
    }

    fun buildNotification(
        title: String,
        content: String,
        progress: Int = 0,
        max: Int = 0,
        indeterminate: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (max > 0 || indeterminate) {
            builder.setProgress(max, progress, indeterminate)
        }

        try {
            val launchIntent = packageManager?.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }
        } catch (_: Exception) {
            // Ignore in unit tests
        }

        try {
            val cancelIntent = Intent(this, SyncForegroundService::class.java).apply {
                action = ACTION_CANCEL_SYNC
            }
            val cancelPendingIntent = PendingIntent.getService(
                this,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Abbrechen",
                cancelPendingIntent
            )
        } catch (_: Exception) {
            // Ignore in unit tests
        }

        return builder.build()
    }

    private fun broadcastProgress(
        phase: String,
        synced: Int,
        total: Int,
        isRunning: Boolean,
        error: String?
    ) {
        notifyProgressListeners(phase, synced, total, isRunning, error)

        try {
            val intent = Intent(ACTION_SYNC_PROGRESS).apply {
                setPackage(packageName)
                putExtra(EXTRA_PHASE, phase)
                putExtra(EXTRA_SYNCED, synced)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_IS_RUNNING, isRunning)
                if (error != null) {
                    putExtra(EXTRA_ERROR, error)
                }
            }
            sendBroadcast(intent)
        } catch (_: Exception) {
            // Ignore in unit tests
        }
    }

    private fun checkAndStopIfIdle() {
        val crRunning = try { syncManager.isCrSyncRunning() } catch (_: Exception) { false }
        val aniRunning = try { syncManager.isAniListSyncRunning() } catch (_: Exception) { false }
        if (!crRunning && !aniRunning) {
            isForegroundActive = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                notificationManager?.cancel(NOTIFICATION_ID)
                stopSelf()
            } catch (_: Exception) {
                // Ignore in unit tests
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CrunchyMate Hintergrund-Synchronisierung"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "crunchymate_sync"
        const val CHANNEL_NAME = "CrunchyMate Sync"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_CR_SYNC = "de.crunchymate.sync.action.START_CR_SYNC"
        const val ACTION_START_ANI_SYNC = "de.crunchymate.sync.action.START_ANI_SYNC"
        const val ACTION_CANCEL_SYNC = "de.crunchymate.sync.action.CANCEL_SYNC"
        const val ACTION_CANCEL_CR = "de.crunchymate.sync.action.CANCEL_CR"
        const val ACTION_CANCEL_ANI = "de.crunchymate.sync.action.CANCEL_ANI"

        const val EXTRA_LIMIT = "de.crunchymate.sync.extra.LIMIT"

        const val ACTION_SYNC_PROGRESS = "de.crunchymate.sync.action.SYNC_PROGRESS"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_SYNCED = "synced"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_ERROR = "error"

        private val listeners = CopyOnWriteArraySet<SyncProgressListener>()

        fun addProgressListener(listener: SyncProgressListener) {
            listeners.add(listener)
        }

        fun removeProgressListener(listener: SyncProgressListener) {
            listeners.remove(listener)
        }

        fun clearProgressListeners() {
            listeners.clear()
        }

        fun notifyProgressListeners(
            phase: String,
            synced: Int,
            total: Int,
            isRunning: Boolean,
            error: String?
        ) {
            for (listener in listeners) {
                try {
                    listener.onProgress(phase, synced, total, isRunning, error)
                } catch (_: Exception) {
                    // Safe execution: prevent failing listener from crashing loop or service
                }
            }
        }

        fun formatCrunchyrollProgress(synced: Int, total: Int): String {
            return if (total > 0) {
                "Crunchyroll: $synced / $total Serien"
            } else {
                "Crunchyroll: $synced Serien geladen"
            }
        }

        fun formatAniListProgress(done: Int, total: Int): String {
            return if (total > 0) {
                "AniList: $done / $total angereichert"
            } else {
                "AniList: $done angereichert"
            }
        }

        @Volatile
        var syncManagerProvider: ((Context) -> SyncManager)? = null

        @Volatile
        private var syncManagerInstance: SyncManager? = null

        fun getSyncManager(context: Context): SyncManager {
            syncManagerInstance?.let { return it }
            return synchronized(this) {
                syncManagerInstance ?: syncManagerProvider?.invoke(context) ?: run {
                    val dbHelper = AppDatabaseHelper.getInstance(context.applicationContext)
                    val repository = AnimeRepository(AndroidDatabaseDelegate(dbHelper))
                    val crClient = CrunchyrollClient()
                    val aniClient = AniListClient()
                    SyncManager(repository, crClient, aniClient).also {
                        syncManagerInstance = it
                    }
                }
            }
        }

        fun setSyncManagerForTesting(manager: SyncManager?) {
            syncManagerInstance = manager
        }

        fun startCrunchyrollSync(context: Context, limit: Int = 0) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START_CR_SYNC
                putExtra(EXTRA_LIMIT, limit)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startAniListSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_START_ANI_SYNC
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_CANCEL_SYNC
            }
            context.startService(intent)
        }

        fun cancelCrunchyrollSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_CANCEL_CR
            }
            context.startService(intent)
        }

        fun cancelAniListSync(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                action = ACTION_CANCEL_ANI
            }
            context.startService(intent)
        }
    }
}
