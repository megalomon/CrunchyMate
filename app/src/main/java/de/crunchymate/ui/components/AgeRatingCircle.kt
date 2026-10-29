package de.crunchymate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.crunchymate.ui.theme.ageRatingColors

/** Schriftgröße der Kachel: so gewählt, dass auch zweistellige Werte im 28-dp-Kreis ohne Umbruch Platz haben. */
val AGE_CIRCLE_CARD_FONT_SIZE: TextUnit = 11.sp

/** Schriftgröße im Detail: Text wie „16+“ passt in den größeren Kreis. */
val AGE_CIRCLE_DETAIL_FONT_SIZE: TextUnit = 12.sp

/** Kreis fester Größe unabhängig von der Ziffernzahl; Farben der USK-Stufe. */
@Composable
fun AgeRatingCircle(
    age: Int,
    text: String,
    diameter: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val colors = ageRatingColors(age)
    Box(
        modifier = modifier.size(diameter).clip(CircleShape).background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
            color = colors.content,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}
