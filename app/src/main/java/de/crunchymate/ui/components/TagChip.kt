package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import de.crunchymate.R
import de.crunchymate.core.ffi.TagState
import de.crunchymate.ui.theme.CrunchyMateColors
import de.crunchymate.ui.theme.Dimens
import de.crunchymate.ui.theme.LocalCrunchyMateColors

private const val SELECTED_CONTAINER_ALPHA = 0.25f

@StringRes
private fun TagState?.descriptionRes(): Int =
    when (this) {
        null -> R.string.tag_state_none
        TagState.PREFER -> R.string.tag_state_prefer
        TagState.MUST -> R.string.tag_state_must
        TagState.EXCLUDE -> R.string.tag_state_exclude
    }

@DrawableRes
private fun TagState.iconRes(): Int =
    when (this) {
        TagState.PREFER -> R.drawable.ic_tag_prefer
        TagState.MUST -> R.drawable.ic_tag_must
        TagState.EXCLUDE -> R.drawable.ic_tag_exclude
    }

private fun TagState.color(colors: CrunchyMateColors): Color =
    when (this) {
        TagState.PREFER -> colors.tagPrefer
        TagState.MUST -> colors.tagMust
        TagState.EXCLUDE -> colors.tagExclude
    }

@Composable
fun TagChip(
    name: String,
    count: Long?,
    state: TagState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    label: String = if (count == null) name else stringResource(R.string.tag_label_with_count, name, count),
) {
    val description = stringResource(state.descriptionRes())
    val stateColor = state?.color(LocalCrunchyMateColors.current)
    // Ohne Mindest-Touchfläche bleiben die Chips so niedrig wie in der Web-Ansicht
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        TagFilterChip(label, state, stateColor, description, onClick, onRemove, modifier)
    }
}

@Composable
private fun TagFilterChip(
    label: String,
    state: TagState?,
    stateColor: Color?,
    description: String,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier,
) {
    FilterChip(
        selected = state != null,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.semantics { stateDescription = description },
        leadingIcon =
            state?.let {
                { Icon(painterResource(it.iconRes()), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
            },
        trailingIcon =
            onRemove?.let {
                {
                    Icon(
                        painterResource(R.drawable.ic_clear),
                        contentDescription = stringResource(R.string.tags_remove, label),
                        modifier = Modifier.size(Dimens.TagRemoveIcon).clickable(onClick = it),
                    )
                }
            },
        colors =
            if (stateColor == null) {
                FilterChipDefaults.filterChipColors()
            } else {
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = stateColor.copy(alpha = SELECTED_CONTAINER_ALPHA),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    selectedLeadingIconColor = stateColor,
                )
            },
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = state != null,
                selectedBorderColor = stateColor ?: Color.Transparent,
                selectedBorderWidth = Dimens.BorderWidth,
            ),
    )
}
