package de.crunchymate.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.ui.components.FilterDropdown
import de.crunchymate.ui.components.SyncCard
import de.crunchymate.ui.intro.IntroDialog
import de.crunchymate.ui.theme.Dimens
import java.util.IllformedLocaleException
import java.util.Locale

private const val STAT_COLUMNS = 2

@Composable
fun SettingsDestination(
    viewModel: SettingsViewModel,
    onOpenLicenses: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onStartSync = viewModel::onStartSync,
        onCancelSync = viewModel::onCancelSync,
        onHideWatchedChange = viewModel::onHideWatchedChange,
        onContentLocaleChange = viewModel::onContentLocaleChange,
        onOledModeChange = viewModel::onOledModeChange,
        onShowAdultChange = viewModel::onShowAdultChange,
        onShowAgeRatingChange = viewModel::onShowAgeRatingChange,
        onCheckUpdatesChange = viewModel::onCheckUpdatesChange,
        onOpenLicenses = onOpenLicenses,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStartSync: (SyncKind) -> Unit,
    onCancelSync: (SyncKind) -> Unit,
    onHideWatchedChange: (Boolean) -> Unit,
    onContentLocaleChange: (String) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onShowAdultChange: (Boolean) -> Unit,
    onShowAgeRatingChange: (Boolean) -> Unit,
    onCheckUpdatesChange: (Boolean) -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startWithPermission = rememberNotificationPermissionStart(onStartSync)
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        SyncCard(
            title = stringResource(R.string.settings_sync_catalog_title),
            kind = SyncKind.CATALOG,
            state = state.catalog,
            onStart = { startWithPermission(SyncKind.CATALOG) },
            onCancel = { onCancelSync(SyncKind.CATALOG) },
        )
        Text(
            state.catalogCountry?.let { stringResource(R.string.settings_catalog_country, countryName(it)) }
                ?: stringResource(R.string.settings_catalog_country_unknown),
            style = MaterialTheme.typography.bodyMedium,
        )
        SyncCard(
            title = stringResource(R.string.settings_sync_metadata_title),
            kind = SyncKind.METADATA,
            state = state.metadata,
            onStart = { startWithPermission(SyncKind.METADATA) },
            onCancel = { onCancelSync(SyncKind.METADATA) },
        )
        DisplaySettings(
            state,
            DisplayActions(onHideWatchedChange, onContentLocaleChange, onOledModeChange, onShowAdultChange, onShowAgeRatingChange),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Dimens.SpaceMd)) {
                SwitchRow(stringResource(R.string.settings_check_updates), state.checkUpdates, onCheckUpdatesChange)
            }
        }
        state.stats?.let { StatsSection(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            OutlinedButton(onClick = onOpenLicenses, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_licenses))
            }
            OutlinedButton(onClick = { showInfo = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_info))
            }
        }
        // Erneutes Anzeigen berührt das Flag intro_seen nicht; Bestätigen und Zurück schließen nur
        if (showInfo) IntroDialog(onConfirm = { showInfo = false }, onExit = { showInfo = false })
    }
}

/** Fragt vor dem Start einmalig nach der Benachrichtigungsberechtigung; gestartet wird unabhängig von der Antwort. */
@Composable
private fun rememberNotificationPermissionStart(onStartSync: (SyncKind) -> Unit): (SyncKind) -> Unit {
    val context = LocalContext.current
    var pendingKind by rememberSaveable { mutableStateOf<SyncKind?>(null) }
    val launcher =
        rememberLauncherForActivityResult(RequestPermission()) { _ ->
            pendingKind?.let(onStartSync)
            pendingKind = null
        }
    return { kind ->
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            onStartSync(kind)
        } else {
            pendingKind = kind
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private class DisplayActions(
    val onHideWatchedChange: (Boolean) -> Unit,
    val onContentLocaleChange: (String) -> Unit,
    val onOledModeChange: (Boolean) -> Unit,
    val onShowAdultChange: (Boolean) -> Unit,
    val onShowAgeRatingChange: (Boolean) -> Unit,
)

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DisplaySettings(
    state: SettingsUiState,
    actions: DisplayActions,
) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(context.currentAppLanguage()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Text(stringResource(R.string.settings_display_heading), style = MaterialTheme.typography.titleMedium)
            FilterDropdown(
                label = stringResource(R.string.settings_content_locale_label),
                options = state.availableLocales,
                selected = state.contentLocale,
                optionLabel = { localeName(it) },
                onSelect = actions.onContentLocaleChange,
            )
            if (state.contentLocaleChangePending) {
                Text(
                    stringResource(R.string.settings_content_locale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SwitchRow(stringResource(R.string.settings_hide_watched), state.hideWatched, actions.onHideWatchedChange)
            SwitchRow(stringResource(R.string.settings_oled_mode), state.oledMode, actions.onOledModeChange)
            SwitchRow(stringResource(R.string.settings_show_adult), state.showAdult, actions.onShowAdultChange)
            SwitchRow(stringResource(R.string.settings_show_age_rating), state.showAgeRating, actions.onShowAgeRatingChange)
            FilterDropdown(
                label = stringResource(R.string.settings_app_language),
                options = AppLanguage.entries,
                selected = selectedLanguage,
                optionLabel = { stringResource(it.nameRes) },
                optionIcon = { language -> language.flagRes?.let { LanguageFlagIcon(it) } },
                onSelect = {
                    selectedLanguage = it
                    context.applyAppLanguage(it)
                },
            )
        }
    }
}

private data class StatItem(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val value: String,
)

/** Zwei Spalten gleicher Breite; bei ungerader Anzahl bleibt die Zelle rechts der letzten Zeile leer. */
@Composable
private fun StatsSection(stats: Stats) {
    val context = LocalContext.current
    val items =
        listOf(
            StatItem(R.drawable.ic_stat_total, R.string.stat_total, stats.total.toString()),
            StatItem(R.drawable.ic_stat_watched, R.string.stat_watched, stats.watched.toString()),
            StatItem(R.drawable.ic_stat_meh, R.string.stat_meh, stats.meh.toString()),
            StatItem(R.drawable.ic_stat_enriched, R.string.stat_enriched, stats.enriched.toString()),
            StatItem(R.drawable.ic_stat_tags, R.string.stat_tags, stats.tags.toString()),
            StatItem(R.drawable.ic_stat_images, R.string.stat_images, stats.imageCount.toString()),
            StatItem(
                R.drawable.ic_stat_storage,
                R.string.stat_image_storage,
                Formatter.formatShortFileSize(context, stats.imageBytes),
            ),
        )
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(stringResource(R.string.settings_stats_heading), style = MaterialTheme.typography.titleMedium)
        items.chunked(STAT_COLUMNS).forEach { rowItems ->
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                rowItems.forEach { StatTile(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(STAT_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(
    item: StatItem,
    modifier: Modifier = Modifier,
) {
    Card(modifier.testTag(statTileTag(item.label))) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Icon(painterResource(item.icon), contentDescription = null, Modifier.size(Dimens.StatTileIcon))
            Text(item.value, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(item.label), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun statTileTag(
    @StringRes label: Int,
): String = "stat_tile_$label"

private fun localeName(tag: String): String = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())

// Locale.Builder statt des auf neuen API-Stufen veralteten Konstruktors Locale("", country)
private fun countryName(country: String): String =
    try {
        Locale
            .Builder()
            .setRegion(country)
            .build()
            .getDisplayCountry(Locale.getDefault())
    } catch (e: IllformedLocaleException) {
        country
    }

@Composable
private fun LanguageFlagIcon(
    @DrawableRes flag: Int,
) {
    Image(painterResource(flag), contentDescription = null, Modifier.size(Dimens.LanguageFlagWidth, Dimens.LanguageFlagHeight))
}
