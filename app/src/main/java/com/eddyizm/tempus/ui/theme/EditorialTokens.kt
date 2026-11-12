package com.eddyizm.tempus.ui.theme

import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.color.MaterialColors

/**
 * Canonical "Editorial Warm Dark · 唱片店夜色" design tokens, Inter typography,
 * 5-tier shape system, and unified Material 3 theme for Compose surfaces.
 */
object EditorialTokens {
    // Warm Dark Surfaces & Borders
    val DarkBg = Color(0xFF0E0D0C)
    val DarkSurface1 = Color(0xFF161412)
    val DarkSurface2 = Color(0xFF1E1B18)
    val DarkSurface3 = Color(0xFF28241F)
    val DarkHairline = Color(0x14FFF0DC)
    val DarkInnerHighlight = Color(0x0DFFFFFF)

    // Warm Dark Accents & Text
    val StylusBrass = Color(0xFFCFA46A)
    val OnStylusBrass = Color(0xFF1D160B)
    val DarkPrimaryContainer = Color(0xFF382B18)
    val DarkOnPrimaryContainer = Color(0xFFF5DFBB)
    val DarkSecondary = Color(0xFFC9BFA9)
    val DarkOnSecondary = Color(0xFF2B251D)
    val DarkSecondaryContainer = Color(0xFF28241F)
    val DarkOnSecondaryContainer = Color(0xFFEAE0D0)
    val VinylMoss = Color(0xFF8DB596)
    val OnVinylMoss = Color(0xFF132619)
    val DarkTertiaryContainer = Color(0xFF1F3124)
    val DarkOnTertiaryContainer = Color(0xFFCFE6D4)
    val Terracotta = Color(0xFFD9785F)
    val OnTerracotta = Color(0xFF3B140A)
    val DarkErrorContainer = Color(0xFF3B1F19)
    val DarkOnErrorContainer = Color(0xFFF6CFC5)
    val IvoryText = Color(0xFFF2ECE3)
    val DarkTextSecondary = Color(0xFFA39C93)
    val DarkTextTertiary = Color(0x66F2ECE3)
    val DarkOutline = Color(0xFF4A433B)
    val DarkOutlineVariant = Color(0xFF2A2520)

    // AMOLED Surfaces (Stylus Brass accents preserved)
    val AmoledBg = Color(0xFF000000)
    val AmoledSheetLow = Color(0xFF0A0908)
    val AmoledSurface1 = Color(0xFF100F0E)
    val AmoledSurface2 = Color(0xFF181614)
    val AmoledSurface3 = Color(0xFF221F1B)
    val AmoledOutline = Color(0xFF3A342E)
    val AmoledOutlineVariant = Color(0xFF1F1C18)

    // Warm Paper Light Surfaces, Accents & Text
    val LightBg = Color(0xFFF5F1EA)
    val LightSurfaceLowest = Color(0xFFFFFFFF)
    val LightSurface1 = Color(0xFFFFFDF9)
    val LightSurface2 = Color(0xFFEFE9DF)
    val LightSurface3 = Color(0xFFE5DDD0)
    val LightHairline = Color(0x1A1D1A17)
    val LightInnerHighlight = Color(0x66FFFFFF)
    val PaperBrass = Color(0xFF9A7136)
    val OnPaperBrass = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFF3E2C3)
    val LightOnPrimaryContainer = Color(0xFF2E1F06)
    val LightSecondary = Color(0xFF655D52)
    val LightSecondaryContainer = Color(0xFFEAE2D6)
    val LightOnSecondaryContainer = Color(0xFF211B13)
    val PaperMoss = Color(0xFF456B4E)
    val LightTertiaryContainer = Color(0xFFD3EBD8)
    val LightOnTertiaryContainer = Color(0xFF0C2412)
    val PaperTerracotta = Color(0xFFB85039)
    val LightErrorContainer = Color(0xFFFADBD4)
    val LightOnErrorContainer = Color(0xFF3D0D04)
    val InkText = Color(0xFF1D1A17)
    val LightTextSecondary = Color(0xFF665F56)
    val LightTextTertiary = Color(0x941D1A17) // ~58% ink for WCAG contrast on warm paper
    val LightOutline = Color(0xFFCFC6B9)
    val LightOutlineVariant = Color(0xFFE3DBD0)

    // Strict 5-Tier Corner Radii (6dp / 10dp / 16dp / 28dp / Pill)
    val RadiusBadge: Dp = 6.dp
    val RadiusListCover: Dp = 10.dp
    val RadiusCard: Dp = 16.dp
    val RadiusHero: Dp = 28.dp

    val ShapeBadge: Shape = RoundedCornerShape(RadiusBadge)
    val ShapeListCover: Shape = RoundedCornerShape(RadiusListCover)
    val ShapeCard: Shape = RoundedCornerShape(RadiusCard)
    val ShapeHero: Shape = RoundedCornerShape(RadiusHero)
    val SheetTopShape: Shape = RoundedCornerShape(topStart = RadiusHero, topEnd = RadiusHero)
    val ShapePill: Shape = CircleShape

    /** Snaps user-configured artwork corner sizes onto the strict 5-tier Editorial scale. */
    fun snapCoverRadius(rawDp: Int): Dp = when {
        rawDp <= 0 -> 0.dp
        rawDp <= 7 -> RadiusBadge
        rawDp <= 13 -> RadiusListCover
        else -> RadiusCard
    }

    const val TNUM_FEATURE = "tnum"

    val InterFontFamily = FontFamily(
        Font(R.font.inter_thin, FontWeight.Thin),
        Font(R.font.inter_extra_light, FontWeight.ExtraLight),
        Font(R.font.inter_light, FontWeight.Light),
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semi_bold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extra_bold, FontWeight.ExtraBold),
        Font(R.font.inter_black, FontWeight.Black),
    )

    // Editorial Type Scale: 30 / 20 / 16 / 15 / 13 / 11 sp with Inter
    val Typography = Typography(
        headlineLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp,
        ),
    )

    val TnumTypography = Typography.copy(
        headlineLarge = Typography.headlineLarge.copy(fontFeatureSettings = TNUM_FEATURE),
        headlineMedium = Typography.headlineMedium.copy(fontFeatureSettings = TNUM_FEATURE),
        titleLarge = Typography.titleLarge.copy(fontFeatureSettings = TNUM_FEATURE),
        titleMedium = Typography.titleMedium.copy(fontFeatureSettings = TNUM_FEATURE),
        titleSmall = Typography.titleSmall.copy(fontFeatureSettings = TNUM_FEATURE),
        bodyLarge = Typography.bodyLarge.copy(fontFeatureSettings = TNUM_FEATURE),
        bodyMedium = Typography.bodyMedium.copy(fontFeatureSettings = TNUM_FEATURE),
        bodySmall = Typography.bodySmall.copy(fontFeatureSettings = TNUM_FEATURE),
        labelLarge = Typography.labelLarge.copy(fontFeatureSettings = TNUM_FEATURE),
        labelMedium = Typography.labelMedium.copy(fontFeatureSettings = TNUM_FEATURE),
        labelSmall = Typography.labelSmall.copy(fontFeatureSettings = TNUM_FEATURE),
    )

    val WarmDarkColorScheme: ColorScheme = darkColorScheme(
        primary = StylusBrass,
        onPrimary = OnStylusBrass,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        tertiary = VinylMoss,
        onTertiary = OnVinylMoss,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        error = Terracotta,
        onError = OnTerracotta,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkBg,
        onBackground = IvoryText,
        surface = DarkBg,
        onSurface = IvoryText,
        surfaceVariant = DarkSurface3,
        onSurfaceVariant = DarkTextSecondary,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        inverseSurface = IvoryText,
        inverseOnSurface = DarkSurface2,
        inversePrimary = PaperBrass,
        surfaceDim = DarkBg,
        surfaceBright = Color(0xFF332E28),
        surfaceContainerLowest = DarkBg,
        surfaceContainerLow = DarkSurface1,
        surfaceContainer = DarkSurface1,
        surfaceContainerHigh = DarkSurface2,
        surfaceContainerHighest = DarkSurface3,
    )

    val WarmLightColorScheme: ColorScheme = lightColorScheme(
        primary = PaperBrass,
        onPrimary = OnPaperBrass,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        secondary = LightSecondary,
        onSecondary = Color.White,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        tertiary = PaperMoss,
        onTertiary = Color.White,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        error = PaperTerracotta,
        onError = Color.White,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        background = LightBg,
        onBackground = InkText,
        surface = LightBg,
        onSurface = InkText,
        surfaceVariant = LightSurface3,
        onSurfaceVariant = LightTextSecondary,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        inverseSurface = Color(0xFF2C2722),
        inverseOnSurface = LightBg,
        inversePrimary = StylusBrass,
        surfaceDim = LightSurface3,
        surfaceBright = LightSurface1,
        surfaceContainerLowest = LightSurfaceLowest,
        surfaceContainerLow = LightSurface1,
        surfaceContainer = LightSurface1,
        surfaceContainerHigh = LightSurface2,
        surfaceContainerHighest = LightSurface3,
    )
}

@Immutable
data class EditorialPalette(
    val isDark: Boolean,
    val isAmoled: Boolean,
    val isDynamic: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val sheetContainer: Color,
    val hairline: Color,
    val innerHighlight: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val moss: Color,
    val terracotta: Color,
    val brass: Color,
)

private val DefaultDarkPalette = EditorialPalette(
    isDark = true,
    isAmoled = false,
    isDynamic = false,
    bg = EditorialTokens.DarkBg,
    surface1 = EditorialTokens.DarkSurface1,
    surface2 = EditorialTokens.DarkSurface2,
    surface3 = EditorialTokens.DarkSurface3,
    sheetContainer = EditorialTokens.DarkSurface2,
    hairline = EditorialTokens.DarkHairline,
    innerHighlight = EditorialTokens.DarkInnerHighlight,
    primary = EditorialTokens.StylusBrass,
    onPrimary = EditorialTokens.OnStylusBrass,
    textPrimary = EditorialTokens.IvoryText,
    textSecondary = EditorialTokens.DarkTextSecondary,
    textTertiary = EditorialTokens.DarkTextTertiary,
    moss = EditorialTokens.VinylMoss,
    terracotta = EditorialTokens.Terracotta,
    brass = EditorialTokens.StylusBrass,
)

val LocalEditorialPalette = staticCompositionLocalOf { DefaultDarkPalette }

@Composable
fun rememberEditorialColorScheme(): ColorScheme {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }
    val isDynamic = Preferences.isDynamicColorAccent()
    val isAmoled = isDark && Preferences.isDarkThemeBlack()

    val defaultScheme = when {
        isDynamic -> if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> EditorialTokens.WarmDarkColorScheme
        else -> EditorialTokens.WarmLightColorScheme
    }

    return remember(context, isDark, isAmoled, isDynamic) {
        fun resolveColor(@AttrRes attr: Int, fallback: Color): Color {
            val resolved = Color(
                MaterialColors.getColor(context, attr, fallback.toArgb())
            )
            return if (resolved.alpha == 0f) fallback else resolved
        }

        val primary = resolveColor(com.google.android.material.R.attr.colorPrimary, defaultScheme.primary)
        val onPrimary = resolveColor(com.google.android.material.R.attr.colorOnPrimary, defaultScheme.onPrimary)
        val primaryContainer = resolveColor(com.google.android.material.R.attr.colorPrimaryContainer, defaultScheme.primaryContainer)
        val onPrimaryContainer = resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer, defaultScheme.onPrimaryContainer)
        val secondary = resolveColor(com.google.android.material.R.attr.colorSecondary, defaultScheme.secondary)
        val secondaryContainer = resolveColor(com.google.android.material.R.attr.colorSecondaryContainer, defaultScheme.secondaryContainer)
        val onSecondaryContainer = resolveColor(com.google.android.material.R.attr.colorOnSecondaryContainer, defaultScheme.onSecondaryContainer)
        val tertiary = resolveColor(com.google.android.material.R.attr.colorTertiary, defaultScheme.tertiary)
        val tertiaryContainer = resolveColor(com.google.android.material.R.attr.colorTertiaryContainer, defaultScheme.tertiaryContainer)
        val onTertiaryContainer = resolveColor(com.google.android.material.R.attr.colorOnTertiaryContainer, defaultScheme.onTertiaryContainer)
        val surface = resolveColor(com.google.android.material.R.attr.colorSurface, defaultScheme.surface)
        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface, defaultScheme.onSurface)
        val onSurfaceVariant = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant, defaultScheme.onSurfaceVariant)
        val surfaceContainerLowest = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerLowest, defaultScheme.surfaceContainerLowest)
        val surfaceContainerLow = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerLow, defaultScheme.surfaceContainerLow)
        val surfaceContainer = resolveColor(com.google.android.material.R.attr.colorSurfaceContainer, defaultScheme.surfaceContainer)
        val surfaceContainerHigh = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh, defaultScheme.surfaceContainerHigh)
        val surfaceContainerHighest = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHighest, defaultScheme.surfaceContainerHighest)
        val outline = resolveColor(com.google.android.material.R.attr.colorOutline, defaultScheme.outline)
        val outlineVariant = resolveColor(com.google.android.material.R.attr.colorOutlineVariant, defaultScheme.outlineVariant)
        val error = resolveColor(com.google.android.material.R.attr.colorError, defaultScheme.error)
        val onError = resolveColor(com.google.android.material.R.attr.colorOnError, defaultScheme.onError)
        val errorContainer = resolveColor(com.google.android.material.R.attr.colorErrorContainer, defaultScheme.errorContainer)
        val onErrorContainer = resolveColor(com.google.android.material.R.attr.colorOnErrorContainer, defaultScheme.onErrorContainer)

        defaultScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            secondaryContainer = if (isAmoled) EditorialTokens.AmoledSurface3 else secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = if (isAmoled) EditorialTokens.AmoledBg else surface,
            onBackground = onSurface,
            surface = if (isAmoled) EditorialTokens.AmoledBg else surface,
            surfaceVariant = if (isAmoled) EditorialTokens.AmoledSurface1 else surfaceContainerHighest,
            surfaceContainerLowest = if (isAmoled) EditorialTokens.AmoledBg else surfaceContainerLowest,
            surfaceContainerLow = if (isAmoled) EditorialTokens.AmoledSheetLow else surfaceContainerLow,
            surfaceContainer = if (isAmoled) EditorialTokens.AmoledSurface1 else surfaceContainer,
            surfaceContainerHigh = if (isAmoled) EditorialTokens.AmoledSurface2 else surfaceContainerHigh,
            surfaceContainerHighest = if (isAmoled) EditorialTokens.AmoledSurface3 else surfaceContainerHighest,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            outline = if (isAmoled) EditorialTokens.AmoledOutline else outline,
            outlineVariant = if (isAmoled) EditorialTokens.AmoledOutlineVariant else outlineVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
        )
    }
}

@Composable
fun rememberEditorialPalette(
    colorScheme: ColorScheme = rememberEditorialColorScheme(),
): EditorialPalette {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }
    val isAmoled = isDark && Preferences.isDarkThemeBlack()
    val isDynamic = Preferences.isDynamicColorAccent()

    return remember(colorScheme, isDark, isAmoled, isDynamic) {
        val sheetColor = when {
            isAmoled -> EditorialTokens.AmoledSheetLow
            isDynamic -> if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
            isDark -> EditorialTokens.DarkSurface2
            else -> EditorialTokens.LightSurface1
        }
        EditorialPalette(
            isDark = isDark,
            isAmoled = isAmoled,
            isDynamic = isDynamic,
            bg = colorScheme.surface,
            surface1 = colorScheme.surfaceContainer,
            surface2 = colorScheme.surfaceContainerHigh,
            surface3 = colorScheme.surfaceContainerHighest,
            sheetContainer = sheetColor,
            hairline = if (isDark) EditorialTokens.DarkHairline else EditorialTokens.LightHairline,
            innerHighlight = if (isDark) EditorialTokens.DarkInnerHighlight else EditorialTokens.LightInnerHighlight,
            primary = colorScheme.primary,
            onPrimary = colorScheme.onPrimary,
            textPrimary = colorScheme.onSurface,
            textSecondary = colorScheme.onSurfaceVariant,
            textTertiary = if (isDark) EditorialTokens.DarkTextTertiary else EditorialTokens.LightTextTertiary,
            moss = if (isDark) EditorialTokens.VinylMoss else EditorialTokens.PaperMoss,
            terracotta = if (isDark) EditorialTokens.Terracotta else EditorialTokens.PaperTerracotta,
            brass = if (isDark) EditorialTokens.StylusBrass else EditorialTokens.PaperBrass,
        )
    }
}

@Composable
fun EditorialTheme(
    useTabularNumerals: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberEditorialColorScheme()
    val palette = rememberEditorialPalette(colorScheme)
    val typography = if (useTabularNumerals) {
        EditorialTokens.TnumTypography
    } else {
        EditorialTokens.Typography
    }
    CompositionLocalProvider(LocalEditorialPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
fun EditorialPageTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        style = EditorialTokens.Typography.headlineLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
