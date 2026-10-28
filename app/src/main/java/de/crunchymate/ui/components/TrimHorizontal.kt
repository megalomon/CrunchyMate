package de.crunchymate.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

/**
 * Verkleinert die belegte Breite um [inset] je Seite, ohne den Inhalt zu beschneiden. Der FilterChip von M3 setzt
 * seinen Innenabstand fest; negatives Padding lässt Compose nicht zu, daher wird die Breite im Layout gekürzt.
 */
fun Modifier.trimHorizontal(inset: Dp): Modifier =
    layout { measurable, constraints ->
        val insetPx = inset.roundToPx()
        // Der Inhalt darf die gekürzten Ränder mitnutzen; sonst kürzt eine knappe Elternbreite den Text mit Auslassung
        val relaxed =
            if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + 2 * insetPx) else constraints
        val placeable = measurable.measure(relaxed)
        val width = constraints.constrainWidth(placeable.width - 2 * insetPx)
        layout(width, placeable.height) { placeable.placeRelative(-insetPx, 0) }
    }
