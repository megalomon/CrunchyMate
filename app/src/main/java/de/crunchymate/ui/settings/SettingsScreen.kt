package de.crunchymate.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.core.ffi.Stats
import de.crunchymate.core.ffi.SyncKind
import de.crunchymate.ui.components.FilterDropdown
import de.crunchymate.ui.components.SyncCard
import de.crunchymate.ui.theme.Dimens
import java.util.IllformedLocaleException
import java.util.Locale

private const val LOG_TAG = "CrunchyMate"

@Composable
fun SettingsDestination(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onStartSync = viewModel::onStartSync,
        onCancelSync = viewModel::onCancelSync,
        onHideWatchedChange = viewModel::onHideWatchedChange,
        onContentLocaleChange = viewModel::onContentLocaleChange,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onStartSync: (SyncKind) -> Unit,
    onCancelSync: (SyncKind) -> Unit,
    onHideWatchedChange: (Boolean) -> Unit,
    onContentLocaleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startWithPermission = rememberNotificationPermissionStart(onStartSync)
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
            state = state.metadata,
            onStart = { startWithPermission(SyncKind.METADATA) },
            onCancel = { onCancelSync(SyncKind.METADATA) },
        )
        DisplaySettings(state, onHideWatchedChange, onContentLocaleChange)
        state.stats?.let { StatsSection(it) }
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

@Composable
private fun DisplaySettings(
    state: SettingsUiState,
    onHideWatchedChange: (Boolean) -> Unit,
    onContentLocaleChange: (String) -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Text(stringResource(R.string.settings_display_heading), style = MaterialTheme.typography.titleMedium)
            FilterDropdown(
                label = stringResource(R.string.settings_content_locale_label),
                options = state.availableLocales,
                selected = state.contentLocale,
                optionLabel = { localeName(it) },
                onSelect = onContentLocaleChange,
            )
            if (state.contentLocaleChangePending) {
                Text(
                    stringResource(R.string.settings_content_locale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_hide_watched),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.hideWatched, onCheckedChange = onHideWatchedChange)
            }
            OutlinedButton(onClick = { openAppLanguageSettings(context) }) {
                Text(stringResource(R.string.settings_app_language))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSection(stats: Stats) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(stringResource(R.string.settings_stats_heading), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
        ) {
            StatTile(R.drawable.ic_stat_total, R.string.stat_total, stats.total)
            StatTile(R.drawable.ic_stat_watched, R.string.stat_watched, stats.watched)
            StatTile(R.drawable.ic_stat_meh, R.string.stat_meh, stats.meh)
            StatTile(R.drawable.ic_stat_enriched, R.string.stat_enriched, stats.enriched)
            StatTile(R.drawable.ic_stat_tags, R.string.stat_tags, stats.tags)
        }
    }
}

@Composable
private fun StatTile(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    value: Long,
) {
    Card(Modifier.widthIn(min = Dimens.StatTileMinWidth)) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(Dimens.StatTileIcon))
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
        }
    }
}

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

private fun openAppLanguageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(LOG_TAG, "App-Spracheinstellung nicht verfügbar: ${e::class.simpleName}")
    }
}
