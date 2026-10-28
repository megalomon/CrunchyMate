package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.crunchymate.R
import de.crunchymate.ui.theme.Dimens

/** Verfügbarkeit der Fassung in der gewählten Inhaltssprache. */
enum class FlagState(
    @StringRes val description: Int,
) {
    DUB(R.string.flag_dub_description),
    SUB_ONLY(R.string.flag_sub_only_description),
    NONE(R.string.flag_none_description),
}

fun flagState(
    hasTargetDub: Boolean,
    hasTargetSub: Boolean,
): FlagState =
    when {
        hasTargetDub -> FlagState.DUB
        hasTargetSub -> FlagState.SUB_ONLY
        else -> FlagState.NONE
    }

/** Flagge der Inhaltssprache; unbekannte Sprachen haben keine Flagge. */
@DrawableRes
fun flagDrawable(contentLocale: String): Int? =
    when (contentLocale) {
        "de-DE" -> R.drawable.ic_flag_de
        "en-US" -> R.drawable.ic_flag_us
        "es-419" -> R.drawable.ic_flag_mx
        "es-ES" -> R.drawable.ic_flag_es
        "fr-FR" -> R.drawable.ic_flag_fr
        "it-IT" -> R.drawable.ic_flag_it
        "pt-BR" -> R.drawable.ic_flag_br
        "ru-RU" -> R.drawable.ic_flag_ru
        "ar-SA" -> R.drawable.ic_flag_sa
        "hi-IN" -> R.drawable.ic_flag_in
        else -> null
    }

private val GreyFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// Strich quer durch die rechte Hälfte, mit etwas Abstand zum Rand wie in der früheren Weboberfläche
private const val STRIKE_INSET = 0.08f

@Composable
fun LanguageFlag(
    contentLocale: String,
    state: FlagState,
    modifier: Modifier = Modifier,
) {
    val flag = flagDrawable(contentLocale) ?: return
    val description = stringResource(state.description)
    val strikeColor = MaterialTheme.colorScheme.error
    val strikeWidth = Dimens.FlagStrokeWidth
    Box(modifier.size(Dimens.FlagWidth, Dimens.FlagHeight).semantics { contentDescription = description }) {
        when (state) {
            FlagState.DUB -> FlagImage(flag)
            FlagState.NONE -> FlagImage(flag, GreyFilter)
            FlagState.SUB_ONLY -> {
                FlagImage(flag, GreyFilter)
                FlagImage(
                    flag,
                    modifier =
                        Modifier.drawWithContent {
                            clipRect(right = size.width / 2) { this@drawWithContent.drawContent() }
                        },
                )
                Box(
                    Modifier
                        .size(Dimens.FlagWidth, Dimens.FlagHeight)
                        .drawWithContent {
                            drawLine(
                                color = strikeColor,
                                start = Offset(size.width / 2 + size.width * STRIKE_INSET, size.height * STRIKE_INSET),
                                end = Offset(size.width - size.width * STRIKE_INSET, size.height - size.height * STRIKE_INSET),
                                strokeWidth = strikeWidth.toPx(),
                                cap = StrokeCap.Round,
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun FlagImage(
    @DrawableRes flag: Int,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(flag),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = modifier.size(Dimens.FlagWidth, Dimens.FlagHeight),
    )
}
