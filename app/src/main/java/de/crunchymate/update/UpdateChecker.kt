package de.crunchymate.update

import android.util.Log
import de.crunchymate.core.CoreGateway
import de.crunchymate.core.SettingsStore
import de.crunchymate.core.ffi.UpdateChannel
import de.crunchymate.core.ffi.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Mindestabstand zwischen zwei erfolgreichen Prüfungen: höchstens eine Anfrage pro Tag. */
const val UPDATE_CHECK_INTERVAL_SECONDS = 24L * 60 * 60

private const val MILLIS_PER_SECOND = 1000L
private const val LOG_TAG = "CrunchyMate"

/** Kanal aus dem Build ("nightly", "release"); lokale Builds ("dev") kennen keinen und prüfen nie. */
fun updateChannelOf(name: String): UpdateChannel? =
    when (name) {
        "nightly" -> UpdateChannel.NIGHTLY
        "release" -> UpdateChannel.RELEASE
        else -> null
    }

fun interface UpdateNotifier {
    fun onUpdateAvailable(info: UpdateInfo)
}

/**
 * Sucht beim App-Start höchstens einmal pro [UPDATE_CHECK_INTERVAL_SECONDS] nach einem neueren Build des eigenen
 * Kanals und meldet ihn über [notifier]. Nur wenn der Nutzer es eingeschaltet hat; es wird nichts geladen.
 */
class UpdateChecker(
    private val gateway: CoreGateway,
    private val settingsStore: SettingsStore,
    private val channel: UpdateChannel?,
    private val currentBuild: String,
    private val notifier: UpdateNotifier,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    // Zwei gleichzeitige Starts (etwa nach Drehung) würden sonst beide vor dem Speichern des Zeitpunkts prüfen
    private val lock = Mutex()

    suspend fun checkIfDue() {
        if (channel == null) return
        lock.withLock {
            // Vor dem Laden gäbe es nur Standardwerte, die Schalter wäre scheinbar aus
            settingsStore.loaded.first { it }
            val settings = settingsStore.settings.value
            val now = nowSeconds()
            if (!settings.checkUpdates || now - settings.lastUpdateCheck < UPDATE_CHECK_INTERVAL_SECONDS) return
            val update =
                try {
                    gateway.checkForUpdate(channel, currentBuild)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline oder Rate-Limit: still bleiben. Ohne gespeicherten Zeitpunkt folgt der nächste Versuch
                    // beim nächsten Start, ein Fehlschlag sperrt die Prüfung also nicht für einen ganzen Tag.
                    Log.i(LOG_TAG, "Update-Prüfung übersprungen: ${e::class.simpleName}")
                    return
                }
            settingsStore.update { it.copy(lastUpdateCheck = now) }
            update?.let(notifier::onUpdateAvailable)
        }
    }
}
