package de.crunchymate.ui.intro

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.core.SettingsStore
import de.crunchymate.ui.theme.Dimens

private data class IntroSection(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val isWarning: Boolean = false,
)

private val INTRO_SECTIONS: List<IntroSection> =
    listOf(
        IntroSection(R.drawable.ic_intro_disclaimer, R.string.intro_disclaimer_title, R.string.intro_disclaimer, isWarning = true),
        IntroSection(R.drawable.ic_nav_search, R.string.intro_purpose_title, R.string.intro_purpose),
        IntroSection(R.drawable.ic_intro_license, R.string.intro_free_title, R.string.intro_free),
        IntroSection(R.drawable.ic_sync_start, R.string.intro_first_step_title, R.string.intro_first_step),
        IntroSection(R.drawable.ic_intro_privacy, R.string.intro_privacy_title, R.string.intro_privacy),
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
        // Wegtippen neben den Dialog zählt nicht; nur Zurück verlässt den Dialog, ohne die Info als gelesen zu markieren
        onDismissRequest = onExit,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { IntroHeader() },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                INTRO_SECTIONS.forEach { IntroSectionRow(it) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intro_confirm)) }
        },
    )
}

@Composable
private fun IntroHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
        // Das adaptive Launcher-Icon lässt sich nicht als Painter laden; Ebenen daher einzeln übereinander legen
        Box(Modifier.size(Dimens.IntroAppIcon).clip(CircleShape)) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, Modifier.size(Dimens.IntroAppIcon))
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(Dimens.IntroAppIcon))
        }
        Text(stringResource(R.string.intro_title), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun IntroSectionRow(section: IntroSection) {
    val tint = if (section.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.IntroSectionCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Dimens.SpaceSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Icon(painterResource(section.icon), contentDescription = null, Modifier.size(Dimens.IntroIcon), tint = tint)
        Column {
            Text(stringResource(section.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(section.body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
