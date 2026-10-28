package de.crunchymate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import de.crunchymate.ui.theme.Dimens

/**
 * Niedrigere Variante der M3-Navigationsleiste: Deren Höhe von 80 dp ist fest und lässt sich nicht
 * verkleinern. Die Systemleisten-Abstände werden wie bei der Standardleiste außerhalb der Höhe angewendet.
 * Die Animation des M3-Indikators und die Ripple-Konfiguration der Standardleiste gelten bewusst nicht;
 * der Indikator wechselt ohne Übergang.
 */
@Composable
fun CompactNavigationBar(content: @Composable RowScope.() -> Unit) {
    Surface(color = NavigationBarDefaults.containerColor, tonalElevation = NavigationBarDefaults.Elevation) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(Dimens.NavigationBarHeight)
                .selectableGroup(),
            content = content,
        )
    }
}

@Composable
fun RowScope.CompactNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant) {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(if (selected) scheme.secondaryContainer else Color.Transparent)
                    .padding(horizontal = Dimens.NavIndicatorHorizontalPadding, vertical = Dimens.NavIndicatorVerticalPadding),
            ) { icon() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
