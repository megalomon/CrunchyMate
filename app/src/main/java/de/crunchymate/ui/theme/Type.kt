package de.crunchymate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

val CrunchyMateTypography =
    Typography(
        titleMedium = Base.titleMedium.merge(TextStyle(fontWeight = FontWeight.SemiBold)),
        titleLarge = Base.titleLarge.merge(TextStyle(fontWeight = FontWeight.Bold)),
        labelSmall = Base.labelSmall.merge(TextStyle(fontSize = 11.sp)),
    )
