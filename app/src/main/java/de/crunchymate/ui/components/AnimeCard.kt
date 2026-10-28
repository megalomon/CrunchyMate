package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary

private const val POSTER_ASPECT_RATIO = 2f / 3f
private val BadgeIconSize = 16.dp

@Composable
private fun Badge(
    @DrawableRes icon: Int,
    text: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(BadgeIconSize))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AnimeCard(
    anime: AnimeSummary,
    onOpen: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleMeh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = anime.posterUrl.ifEmpty { null },
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT_RATIO),
        )
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(anime.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (anime.anilistScore > 0) {
                    Badge(
                        R.drawable.ic_badge_score,
                        stringResource(R.string.badge_score, anime.anilistScore),
                        stringResource(R.string.badge_score_description),
                    )
                }
                if (anime.hasTargetDub) {
                    Badge(R.drawable.ic_badge_dub, stringResource(R.string.badge_dub), stringResource(R.string.badge_dub_description))
                }
                if (anime.hasTargetSub) {
                    Badge(R.drawable.ic_badge_sub, stringResource(R.string.badge_sub), stringResource(R.string.badge_sub_description))
                }
            }
            if (anime.preferMatches > 0) {
                Row {
                    Badge(
                        R.drawable.ic_badge_boost,
                        pluralStringResource(R.plurals.badge_boost_matches, anime.preferMatches, anime.preferMatches),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ToggleButton(
                    icon = if (anime.watched) R.drawable.ic_watched_on else R.drawable.ic_watched_off,
                    description = stringResource(R.string.action_toggle_watched),
                    state = stringResource(if (anime.watched) R.string.state_watched else R.string.state_not_watched),
                    active = anime.watched,
                    onClick = onToggleWatched,
                )
                ToggleButton(
                    icon = if (anime.isMeh) R.drawable.ic_meh_on else R.drawable.ic_meh_off,
                    description = stringResource(R.string.action_toggle_meh),
                    state = stringResource(if (anime.isMeh) R.string.state_meh else R.string.state_not_meh),
                    active = anime.isMeh,
                    onClick = onToggleMeh,
                )
            }
        }
    }
}

@Composable
private fun ToggleButton(
    @DrawableRes icon: Int,
    description: String,
    state: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = state }) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
