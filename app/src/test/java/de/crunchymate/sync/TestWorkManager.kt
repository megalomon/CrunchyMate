package de.crunchymate.sync

import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import de.crunchymate.core.CoreGateway

/** Erzeugt SyncWorker mit dem Test-Gateway statt mit dem Container der Anwendung. */
class TestSyncWorkerFactory(
    private val gateway: CoreGateway,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker = SyncWorker(appContext, workerParameters) { gateway }
}

fun initTestWorkManager(
    context: Context,
    gateway: CoreGateway,
): WorkManager {
    val config =
        Configuration
            .Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(TestSyncWorkerFactory(gateway))
            .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    return WorkManager.getInstance(context)
}
