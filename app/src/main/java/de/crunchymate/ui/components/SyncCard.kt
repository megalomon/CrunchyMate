package de.crunchymate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.crunchymate.R
import de.crunchymate.core.messageRes
import de.crunchymate.sync.SyncUiState
import de.crunchymate.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun formatTimestamp(epochSeconds: Long): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))

@Composable
fun SyncCard(
    title: String,
    state: SyncUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.running) {
                    IconButton(onClick = onCancel) {
                        Icon(painterResource(R.drawable.ic_sync_cancel), stringResource(R.string.sync_cancel))
                    }
                } else {
                    IconButton(onClick = onStart) {
                        Icon(painterResource(R.drawable.ic_sync_start), stringResource(R.string.sync_start))
                    }
                }
            }
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
            } else if (state.running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(
                    when {
                        state.running -> R.string.sync_status_running
                        state.cancelled -> R.string.sync_status_cancelled
                        else -> R.string.sync_status_idle
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.total > 0) {
                Text(stringResource(R.string.sync_progress, state.done, state.total), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                state.lastFinishedAt?.let { stringResource(R.string.sync_last_finished, formatTimestamp(it)) }
                    ?: stringResource(R.string.sync_never_finished),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.lastError?.let {
                Text(
                    stringResource(R.string.sync_last_error, stringResource(it.messageRes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
