package de.crunchymate.ui.intro

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

/** Zeigt die Erststart-Info, bis sie bestätigt wurde; das Flag liegt in den Einstellungen und damit im Backup. */
@Composable
fun IntroDialogHost(settingsStore: SettingsStore) {
    val settings by settingsStore.settings.collectAsStateWithLifecycle()
    if (!settings.introSeen) {
        IntroDialog(onConfirm = { settingsStore.update { it.copy(introSeen = true) } })
    }
}

@Composable
fun IntroDialog(onConfirm: () -> Unit) {
    AlertDialog(
        // Kein Wegtippen: die Info gilt erst mit der Bestätigung als gelesen
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
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
