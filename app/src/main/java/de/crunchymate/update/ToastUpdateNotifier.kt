package de.crunchymate.update

import android.content.Context
import android.widget.Toast
import de.crunchymate.R
import de.crunchymate.core.ffi.UpdateInfo

/** Zeigt den Hinweis als Toast; kein Download, keine Benachrichtigung. Aufruf auf dem Main-Thread. */
class ToastUpdateNotifier(
    private val context: Context,
) : UpdateNotifier {
    override fun onUpdateAvailable(info: UpdateInfo) {
        val text =
            when (info) {
                is UpdateInfo.Nightly -> context.getString(R.string.update_available_nightly, info.date, info.build)
                is UpdateInfo.Release -> context.getString(R.string.update_available_release, info.version)
            }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
