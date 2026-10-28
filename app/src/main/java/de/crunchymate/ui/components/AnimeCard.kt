package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.crunchymate.R
import de.crunchymate.core.ffi.AnimeSummary
import de.crunchymate.ui.theme.Dimens
import de.crunchymate.ui.theme.PosterOverlayBackground
import de.crunchymate.ui.theme.PosterOverlayContent
import de.crunchymate.ui.theme.scoreColor

private const val TITLE_MAX_LINES = 2

/** Kennzeichnungen für Tests, die die sichtbare Geometrie der Overlays prüfen. */
const val SCORE_BADGE_TAG = "score_badge"
const val WATCHED_CIRCLE_TAG = "watched_circle"
const val MEH_CIRCLE_TAG = "meh_circle"
const val AGE_BADGE_TAG = "age_badge"

/** Gemeinsamer Rahmen der Overlay-Kästchen auf dem Poster. */
@Composable
private fun OverlayBadgeFrame(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // Gleiche Höhe wie der sichtbare Kreis der Schalter, damit beide Kanten fluchten
    Row(
        modifier =
            modifier
                .height(Dimens.OverlayButton)
                .clip(RoundedCornerShape(Dimens.SpaceXs))
                .background(PosterOverlayBackground)
                .padding(horizontal = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        content = content,
    )
}

/** Altersstufe als reine Zahl; die Beschreibung steht für TalkBack in den Semantics. */
@Composable
private fun AgeRatingBadge(
    age: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.badge_age_rating_description, age)
    OverlayBadgeFrame(modifier.testTag(AGE_BADGE_TAG).semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.badge_age_rating, age),
            style = MaterialTheme.typography.labelSmall,
            color = PosterOverlayContent,
        )
    }
}

@Composable
internal fun OverlayBadge(
    @DrawableRes icon: Int,
    text: String,
    description: String? = null,
    tag: String? = null,
    iconTint: Color = PosterOverlayContent,
) {
    OverlayBadgeFrame(if (tag != null) Modifier.testTag(tag) else Modifier) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = iconTint,
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
    showAgeRating: Boolean = true,
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
            if (anime.anilistScore > 0) {
                Box(Modifier.align(Alignment.TopStart).padding(Dimens.OverlayInset)) {
                    OverlayBadge(
                        R.drawable.ic_badge_score,
                        stringResource(R.string.badge_score, anime.anilistScore),
                        stringResource(R.string.badge_score_description),
                        SCORE_BADGE_TAG,
                        scoreColor(anime.anilistScore),
                    )
                }
            }
            anime.ageRating?.takeIf { showAgeRating }?.let { age ->
                // Unten rechts, damit die Tippflächen der Schalter oben rechts frei bleiben
                AgeRatingBadge(age, Modifier.align(Alignment.BottomEnd).padding(Dimens.OverlayInset))
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
 * Kleiner runder Schalter auf dem Poster. Die Berührfläche liegt vollständig im Poster, weil das Poster
 * Kindflächen am Rand abschneidet. Benachbarte Zeilen teilen sich die Lücke zwischen den Kreisen je zur Hälfte, damit
 * sich die Flächen nie überlappen. Die erste Zeile beginnt deshalb am Posterrand und ist niedriger als
 * [Dimens.MinTouchTarget]; die Breite beträgt [Dimens.MinTouchTarget] mit dem Kreis am rechten Rand im Abstand
 * [Dimens.OverlayInset]. [row] stapelt die Kreise mit [Dimens.OverlayGap] Abstand.
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
    val rowStep = Dimens.OverlayButton + Dimens.OverlayGap
    val gapShare = Dimens.OverlayGap / 2
    val circleTop = Dimens.OverlayInset + rowStep * row
    val isFirstRow = row == 0
    val areaTop = if (isFirstRow) 0.dp else circleTop - gapShare
    val areaHeight = if (isFirstRow) circleTop + Dimens.OverlayButton + gapShare else Dimens.MinTouchTarget
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .offset(y = areaTop)
                .size(width = Dimens.MinTouchTarget, height = areaHeight)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    stateDescription = state
                },
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = circleTop - areaTop, end = Dimens.OverlayInset)
                .size(Dimens.OverlayButton)
                .clip(CircleShape)
                .background(PosterOverlayBackground)
                .indication(interactionSource, ripple())
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
