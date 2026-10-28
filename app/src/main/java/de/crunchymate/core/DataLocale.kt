package de.crunchymate.core

import android.util.Log
import de.crunchymate.core.ffi.CoreException
import de.crunchymate.core.ffi.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private const val LOG_TAG = "CrunchyMate"

/** Inhaltssprache des letzten vollständigen Katalog-Syncs laut Kern; null vor dem ersten. */
fun CoreGateway.syncedContentLocale(): Flow<String?> =
    changes
        .onStart { emit(Unit) }
        .map {
            try {
                syncedContentLocaleNow()
            } catch (e: CoreException) {
                Log.w(LOG_TAG, "Sync-Sprache konnte nicht gelesen werden: ${e::class.simpleName}")
                null
            }
        }.distinctUntilChanged()

/**
 * Sprache, für die die gespeicherten Sub/Dub-Merkmale gelten und deren Flagge daher angezeigt wird.
 * Nach einem Sprachwechsel bleibt das die alte Sprache, bis ein Katalog-Sync vollständig gelaufen ist;
 * vor dem ersten Sync gilt die eingestellte.
 */
fun CoreGateway.dataLocale(settings: Flow<Settings>): Flow<String> =
    combine(syncedContentLocale(), settings.map { it.contentLocale }.distinctUntilChanged()) { synced, configured ->
        synced ?: configured
    }.distinctUntilChanged()

/** Ein Sync ist nötig, wenn die vorhandenen Daten in einer anderen Sprache geladen wurden. */
fun contentLocaleSyncPending(
    synced: String?,
    configured: String,
): Boolean = synced != null && synced != configured
