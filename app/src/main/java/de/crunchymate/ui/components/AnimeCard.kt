package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.ui.theme.Dimens
import de.crunchymate.ui.theme.PosterOverlayBackground
import de.crunchymate.ui.theme.PosterOverlayContent

private const val TITLE_MAX_LINES = 2

/** Kennzeichnungen für Tests, die die sichtbare Geometrie der Overlays prüfen. */
const val SCORE_BADGE_TAG = "score_badge"
const val WATCHED_CIRCLE_TAG = "watched_circle"
const val MEH_CIRCLE_TAG = "meh_circle"

@Composable
private fun OverlayBadge(
    @DrawableRes icon: Int,
    text: String,
    description: String? = null,
    tag: String? = null,
) {
    // Gleiche Höhe wie der sichtbare Kreis der Schalter, damit beide Kanten fluchten
    Row(
        modifier =
            (if (tag != null) Modifier.testTag(tag) else Modifier)
                .height(Dimens.OverlayButton)
                .clip(RoundedCornerShape(Dimens.SpaceXs))
                .background(PosterOverlayBackground)
                .padding(horizontal = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = PosterOverlayContent,
            modifier = Modifier.size(Dimens.OverlayBadgeIcon),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = PosterOverlayContent)
    }
}

@Composable
fun AnimeCard(
    anime: AnimeSummary,
    contentLocale: String,
    onOpen: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleMeh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes =
        if (anime.episodeCount > 0) {
            stringResource(R.string.card_episodes, anime.episodeCount)
        } else {
            stringResource(R.string.card_series)
        }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Box {
            LocalImage(
                path = anime.posterPath,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(Dimens.POSTER_ASPECT_RATIO),
            )
            Column(
                Modifier.align(Alignment.TopStart).padding(Dimens.OverlayInset),
                verticalArrangement = Arrangement.spacedBy(Dimens.OverlayGap),
            ) {
                if (anime.anilistScore > 0) {
                    OverlayBadge(
                        R.drawable.ic_badge_score,
                        stringResource(R.string.badge_score, anime.anilistScore),
                        stringResource(R.string.badge_score_description),
                        SCORE_BADGE_TAG,
                    )
                }
                if (anime.preferMatches > 0) {
                    OverlayBadge(
                        R.drawable.ic_badge_boost,
                        pluralStringResource(R.plurals.badge_boost_matches, anime.preferMatches, anime.preferMatches),
                    )
                }
            }
            OverlayToggle(
                icon = if (anime.watched) R.drawable.ic_watched_on else R.drawable.ic_watched_off,
                description = stringResource(R.string.action_toggle_watched),
                state = stringResource(if (anime.watched) R.string.state_marked else R.string.state_not_marked),
                row = 0,
                circleTag = WATCHED_CIRCLE_TAG,
                onClick = onToggleWatched,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            OverlayToggle(
                icon = if (anime.isMeh) R.drawable.ic_meh_on else R.drawable.ic_meh_off,
                description = stringResource(R.string.action_toggle_meh),
                state = stringResource(if (anime.isMeh) R.string.state_marked else R.string.state_not_marked),
                row = 1,
                circleTag = MEH_CIRCLE_TAG,
                onClick = onToggleMeh,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Column(Modifier.padding(Dimens.CardTextPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(
                anime.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                minLines = TITLE_MAX_LINES,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    episodes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                LanguageFlag(contentLocale, flagState(anime.hasTargetDub, anime.hasTargetSub))
            }
        }
    }
}

/**
 * Kleiner runder Schalter auf dem Poster. Die 48 dp große Berührfläche umgibt den sichtbaren Kreis und wird
 * so verschoben, dass der Kreis exakt mit Abstand [Dimens.OverlayInset] von der Ecke sitzt; [row] stapelt
 * die Kreise mit [Dimens.OverlayGap] Abstand. Berührflächen benachbarter Zeilen überlappen dabei leicht.
 */
@Composable
private fun OverlayToggle(
    @DrawableRes icon: Int,
    description: String,
    state: String,
    row: Int,
    circleTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val touchPadding = (Dimens.MinTouchTarget - Dimens.OverlayButton) / 2
    val rowStep = Dimens.OverlayButton + Dimens.OverlayGap
    Box(
        modifier =
            modifier
                .offset(x = touchPadding - Dimens.OverlayInset, y = Dimens.OverlayInset - touchPadding + rowStep * row)
                .size(Dimens.MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    stateDescription = state
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Dimens.OverlayButton)
                .clip(CircleShape)
                .background(PosterOverlayBackground)
                .testTag(circleTag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = PosterOverlayContent,
                modifier = Modifier.size(Dimens.OverlayIcon),
            )
        }
    }
}
