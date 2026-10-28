package de.crunchymate.ui.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.crunchymate.R
import de.crunchymate.core.ffi.AniListInfo
import de.crunchymate.core.ffi.AnimeDetail
import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.ui.components.EmptyState
import de.crunchymate.ui.components.LocalImage
import de.crunchymate.ui.components.TagChip
import de.crunchymate.ui.tags.stateOf
import de.crunchymate.ui.theme.Dimens

private const val POSTER_ASPECT_RATIO = 2f / 3f

@Composable
fun DetailDestination(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DetailScreen(
        state = state,
        onBack = onBack,
        onToggleWatched = viewModel::onToggleWatched,
        onToggleMeh = viewModel::onToggleMeh,
        onTagTapped = viewModel::onTagTapped,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onToggleWatched: () -> Unit,
    onToggleMeh: () -> Unit,
    onTagTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Die äußere App-Hülle berücksichtigt die Systemleisten bereits
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(state.anime?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        val anime = state.anime
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                anime != null -> DetailContent(anime, state, onToggleWatched, onToggleMeh, onTagTapped)
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else ->
                    EmptyState(
                        icon = R.drawable.ic_state_error,
                        title = stringResource(R.string.detail_error_title),
                        subtitle = stringResource(state.error ?: R.string.error_unknown),
                    )
            }
        }
    }
}

@Composable
private fun DetailContent(
    anime: AnimeDetail,
    state: DetailUiState,
    onToggleWatched: () -> Unit,
    onToggleMeh: () -> Unit,
    onTagTapped: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (anime.bannerPath.isNotEmpty()) {
            LocalImage(
                path = anime.bannerPath,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(Dimens.DetailBannerHeight),
            )
        }
        Column(Modifier.padding(Dimens.SpaceMd), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
            Header(anime, onToggleWatched, onToggleMeh)
            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (anime.genres.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
                    anime.genres.forEach { InfoLabel(it) }
                }
            }
            if (anime.description.isNotBlank()) {
                Section(R.string.detail_description) { Text(anime.description, style = MaterialTheme.typography.bodyMedium) }
            }
            if (anime.tags.isNotEmpty()) {
                Section(R.string.detail_tags) { Tags(anime, state.tagStates, onTagTapped) }
            }
            anime.anilist?.let { AniListSection(it) }
        }
    }
}

@Composable
private fun Header(
    anime: AnimeDetail,
    onToggleWatched: () -> Unit,
    onToggleMeh: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)) {
        // Der Titel steht direkt daneben; eine Bildbeschreibung würde ihn doppelt vorlesen
        LocalImage(
            path = anime.posterPath,
            contentDescription = null,
            modifier = Modifier.width(Dimens.DetailPosterWidth).aspectRatio(POSTER_ASPECT_RATIO),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            Text(anime.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            listOf(anime.titleRomaji, anime.titleEnglish)
                .filter { it.isNotBlank() && it != anime.title }
                .distinct()
                .forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
            ) {
                if (anime.launchYear > 0) InfoLabel(anime.launchYear.toString())
                if (anime.episodeCount > 0) {
                    InfoLabel(pluralStringResource(R.plurals.detail_episodes, anime.episodeCount, anime.episodeCount))
                }
                if (anime.hasTargetDub) {
                    InfoLabel(stringResource(R.string.badge_dub), R.drawable.ic_badge_dub, stringResource(R.string.badge_dub_description))
                }
                if (anime.hasTargetSub) {
                    InfoLabel(stringResource(R.string.badge_sub), R.drawable.ic_badge_sub, stringResource(R.string.badge_sub_description))
                }
            }
            ToggleButton(
                active = anime.watched,
                icon = if (anime.watched) R.drawable.ic_watched_on else R.drawable.ic_watched_off,
                text = stringResource(if (anime.watched) R.string.detail_unmark_watched else R.string.action_toggle_watched),
                onClick = onToggleWatched,
            )
            ToggleButton(
                active = anime.isMeh,
                icon = if (anime.isMeh) R.drawable.ic_meh_on else R.drawable.ic_meh_off,
                text = stringResource(if (anime.isMeh) R.string.detail_unmark_meh else R.string.action_toggle_meh),
                onClick = onToggleMeh,
            )
        }
    }
}

@Composable
private fun ToggleButton(
    active: Boolean,
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Icon(painterResource(icon), contentDescription = null)
        Text(text, modifier = Modifier.padding(start = Dimens.SpaceSm))
    }
    if (active) {
        FilledTonalButton(onClick = onClick) { content() }
    } else {
        OutlinedButton(onClick = onClick) { content() }
    }
}

@Composable
private fun Tags(
    anime: AnimeDetail,
    tagStates: List<TagSelection>,
    onTagTapped: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        anime.tags.forEach { tag ->
            TagChip(
                name = tag.name,
                count = null,
                state = tagStates.stateOf(tag.name),
                onClick = { onTagTapped(tag.name) },
                label = stringResource(R.string.detail_tag_rank, tag.name, tag.rank),
            )
        }
    }
}

@Composable
private fun AniListSection(info: AniListInfo) {
    val uriHandler = LocalUriHandler.current
    Section(R.string.detail_anilist) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            if (info.score > 0) InfoLabel(stringResource(R.string.detail_anilist_score, info.score))
            if (info.status.isNotBlank()) InfoLabel(info.status)
        }
        if (info.description.isNotBlank()) {
            Text(AnnotatedString.fromHtml(info.description), style = MaterialTheme.typography.bodyMedium)
        }
        if (info.siteUrl.isNotBlank()) {
            OutlinedButton(onClick = { uriHandler.openUri(info.siteUrl) }) {
                Icon(painterResource(R.drawable.ic_open_external), contentDescription = null)
                Text(stringResource(R.string.detail_anilist_open), modifier = Modifier.padding(start = Dimens.SpaceSm))
            }
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun InfoLabel(
    text: String,
    @DrawableRes icon: Int? = null,
    iconDescription: String? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = iconDescription, modifier = Modifier.size(Dimens.BadgeIcon))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
