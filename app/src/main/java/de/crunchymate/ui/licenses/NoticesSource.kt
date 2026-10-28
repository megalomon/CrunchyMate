package de.crunchymate.ui.licenses

import android.content.res.AssetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

fun interface NoticesSource {
    suspend fun load(): OpenSourceNotices
}

/** Liest die Hinweise aus den App-Assets; das Asset ist über hundert Kilobyte groß und wird deshalb abseits des Main-Threads gelesen. */
class AssetNoticesSource(
    private val assets: AssetManager,
    private val io: CoroutineDispatcher,
) : NoticesSource {
    override suspend fun load(): OpenSourceNotices =
        withContext(io) {
            OpenSourceNotices.parse(assets.open(NOTICES_ASSET_NAME).bufferedReader().use { it.readText() })
        }
}
