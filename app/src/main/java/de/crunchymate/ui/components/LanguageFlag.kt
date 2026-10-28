package de.crunchymate.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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

/** Ecken des Dreiecks oben links der Diagonale von unten links nach oben rechts; dieser Teil der Flagge wird grau. */
internal fun greyTrianglePoints(size: Size): List<Offset> = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height))

@Composable
fun LanguageFlag(
    contentLocale: String,
    state: FlagState,
    modifier: Modifier = Modifier,
    width: Dp = Dimens.FlagWidth,
    height: Dp = Dimens.FlagHeight,
) {
    val flag = flagDrawable(contentLocale) ?: return
    val description = stringResource(state.description)
    Box(modifier.size(width, height).semantics { contentDescription = description }) {
        when (state) {
            FlagState.DUB -> FlagImage(flag, width = width, height = height)
            FlagState.NONE -> FlagImage(flag, GreyFilter, width = width, height = height)
            FlagState.SUB_ONLY -> {
                FlagImage(flag, width = width, height = height)
                FlagImage(
                    flag,
                    GreyFilter,
                    width,
                    height,
                    Modifier.drawWithContent { clipPath(greyTrianglePath(size)) { this@drawWithContent.drawContent() } },
                )
            }
        }
    }
}

private fun greyTrianglePath(size: Size): Path =
    Path().apply {
        val (first, second, third) = greyTrianglePoints(size)
        moveTo(first.x, first.y)
        lineTo(second.x, second.y)
        lineTo(third.x, third.y)
        close()
    }

@Composable
private fun FlagImage(
    @DrawableRes flag: Int,
    colorFilter: ColorFilter? = null,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(flag),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = modifier.size(width, height),
    )
}
