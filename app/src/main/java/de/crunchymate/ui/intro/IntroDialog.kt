package de.crunchymate.ui.intro

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.core.SettingsStore
import de.crunchymate.ui.theme.Dimens

private val INTRO_PARAGRAPHS: List<Int> =
    listOf(
        R.string.intro_disclaimer,
        R.string.intro_purpose,
        R.string.intro_free,
        R.string.intro_first_step,
        R.string.intro_privacy,
    )

/**
 * Zeigt die Erststart-Info, bis sie bestätigt wurde; das Flag liegt in den Einstellungen und damit im Backup.
 * Zurück beendet die App, ohne das Flag zu setzen: der Dialog erscheint dann beim nächsten Start wieder.
 */
@Composable
fun IntroDialogHost(settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    if (!settings.introSeen) {
        IntroDialog(
            onConfirm = { settingsStore.update { it.copy(introSeen = true) } },
            onExit = { activity?.finish() },
        )
    }
}

@Composable
fun IntroDialog(
    onConfirm: () -> Unit,
    onExit: () -> Unit,
) {
    AlertDialog(
        // Wegtippen neben den Dialog zählt nicht; nur Zurück verlässt die App, ohne die Info als gelesen zu markieren
        onDismissRequest = onExit,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.intro_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                INTRO_PARAGRAPHS.forEach { Text(stringResource(it)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.intro_confirm)) } },
    )
}
