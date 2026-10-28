package de.crunchymate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import java.io.File

/** Kennzeichnung des neutralen Platzhalters, wenn kein Bild vorliegt. */
const val IMAGE_PLACEHOLDER_TAG = "image_placeholder"

/** Coil-Modell für eine lokale Bilddatei; ohne Pfad (noch nicht geladen) gibt es kein Modell. */
fun localImageModel(path: String): File? = path.takeIf { it.isNotEmpty() }?.let(::File)

/**
 * Zeigt eine lokale Bilddatei. Fehlt der Pfad oder lässt sich die Datei nicht lesen, erscheint eine
 * neutrale Fläche statt des Fehlersymbols; Bilder werden nie über das Netz geladen.
 */
@Composable
fun LocalImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val model = localImageModel(path)
    if (model == null) {
        Box(modifier.background(neutral).testTag(IMAGE_PLACEHOLDER_TAG))
        return
    }
    val placeholder = ColorPainter(neutral)
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        modifier = modifier,
    )
}
