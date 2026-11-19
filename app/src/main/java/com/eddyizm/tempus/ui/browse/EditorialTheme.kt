package com.eddyizm.tempus.ui.browse

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.repository.ServerRepository
import com.eddyizm.tempus.subsonic.models.MusicFolder
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.MusicFolderUtil
import com.eddyizm.tempus.util.Preferences
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.core.graphics.drawable.toDrawable
import com.eddyizm.tempus.viewmodel.LibraryViewModel
import com.eddyizm.tempus.viewmodel.MainViewModel
import com.eddyizm.tempus.weather.WeatherMood
import com.eddyizm.tempus.weather.WeatherRepository
import com.google.android.material.navigationrail.NavigationRailView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot


/**
 * Editorial Warm Dark ("唱片店夜色 · 暖黑机械") design system & shared building blocks
 * for Home, Discover + Sync Center, External Playlist Import, SearchBrowse, and Library.
 */

@Immutable
data class EditorialColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val innerHighlight: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val moss: Color,
    val terracotta: Color,
    val brass: Color,
    val vinylDisc: Color,
    val vinylGroove: Color,
    val heroWashFallback: Color,
)

internal val DarkEditorialColors = EditorialColors(
    isDark = true,
    bg = Color(0xFF0E0D0C),
    surface1 = Color(0xFF161412),
    surface2 = Color(0xFF1E1B18),
    surface3 = Color(0xFF28241F),
    hairline = Color(0x14FFF0DC),
    innerHighlight = Color(0x0DFFFFFF),
    primary = Color(0xFFCFA46A),
    onPrimary = Color(0xFF1D160B),
    secondaryAccent = Color(0xFFC9BFA9),
    textPrimary = Color(0xFFF2ECE3),
    textSecondary = Color(0xA3F2ECE3),
    textTertiary = Color(0x66F2ECE3),
    moss = Color(0xFF8DB596),
    terracotta = Color(0xFFD9785F),
    brass = Color(0xFFCFA46A),
    vinylDisc = Color(0xFF221E1A),
    vinylGroove = Color(0x26FFF0DC),
    heroWashFallback = Color(0xFF2B2117),
)

internal val AmoledEditorialColors = DarkEditorialColors.copy(
    bg = Color(0xFF000000),
    surface1 = Color(0xFF100F0E),
    surface2 = Color(0xFF181614),
    surface3 = Color(0xFF221F1B),
)

internal val LightEditorialColors = EditorialColors(
    isDark = false,
    bg = Color(0xFFF5F1EA),
    surface1 = Color(0xFFFFFDF9),
    surface2 = Color(0xFFEFE9DF),
    surface3 = Color(0xFFE5DDD0),
    hairline = Color(0x1F1D1A17),
    innerHighlight = Color(0x66FFFFFF),
    primary = Color(0xFF9A7136),
    onPrimary = Color(0xFFFFFFFF),
    secondaryAccent = Color(0xFF655D52),
    textPrimary = Color(0xFF1D1A17),
    textSecondary = Color(0xBD1D1A17), // 74% ink for strong readability on warm paper
    textTertiary = Color(0x991D1A17),  // 60% ink (>4.6:1 contrast on #FFFDF9 / #F5F1EA)
    moss = Color(0xFF3B6644),
    terracotta = Color(0xFFA8432B),
    brass = Color(0xFF9A7136),
    vinylDisc = Color(0xFF2B2621),
    vinylGroove = Color(0x26FFF0DC),
    heroWashFallback = Color(0xFFEBE1D1),
)

val LocalEditorialColors = staticCompositionLocalOf { DarkEditorialColors }

object EditorialTheme {
    val colors: EditorialColors
        @Composable get() = LocalEditorialColors.current
}

/** Shared Inter sans-serif font family matching XML `@font/inter`. */
val EditorialSansFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

internal val EditorialTypography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = EditorialSansFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = EditorialSansFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = EditorialSansFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = EditorialSansFontFamily, fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = EditorialSansFontFamily, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = EditorialSansFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = EditorialSansFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = EditorialSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = EditorialSansFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = EditorialSansFontFamily, fontSize = 15.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = EditorialSansFontFamily, fontSize = 13.sp),
        bodySmall = base.bodySmall.copy(fontFamily = EditorialSansFontFamily, fontSize = 11.sp),
        labelLarge = base.labelLarge.copy(fontFamily = EditorialSansFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = EditorialSansFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = EditorialSansFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

/** Strict 5-tier corner radii system across all browse/home/library surfaces. */
object EditorialRadii {
    /** 6dp — badges & tiny stacked covers */
    val Badge = RoundedCornerShape(6.dp)
    /** 10dp — 48dp list covers & mosaic boxes */
    val ListCover = RoundedCornerShape(10.dp)
    /** 16dp — standard cards & Record Sleeve cards */
    val Card = RoundedCornerShape(16.dp)
    /** 28dp — Hero cards & Modal Bottom Sheets */
    val Hero = RoundedCornerShape(28.dp)
    val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Pill / CircleShape — 44dp Global Search Pill, status capsules, filter chips */
    val Pill = CircleShape
}

const val TNUM_FEATURE = "tnum"

/** Applies tabular numerals (`tnum`) for counts, percentages, durations, and dates. */
fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = TNUM_FEATURE)

@Composable
fun tnumStyle(
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    color: Color = Color.Unspecified,
    serif: Boolean = false,
): TextStyle = LocalTextStyle.current.copy(
    fontSize = fontSize,
    fontWeight = fontWeight,
    color = color,
    fontFamily = if (serif) FontFamily.Serif else EditorialSansFontFamily,
    fontFeatureSettings = TNUM_FEATURE,
)

/** Muted warm record-spine accent colors (replaces neon/rainbow tiles). */
object BrowsePalette {
    internal val spines = longArrayOf(
        0xFFCFA46A, // Stylus Brass
        0xFF8DB596, // Vinyl Moss
        0xFF7D93A3, // Slate Press
        0xFFC47D68, // Terracotta Clay
        0xFFB58F5C, // Aged Amber
        0xFFA18189, // Dusty Rose-Umber
        0xFF8E745E, // Walnut Sleeve
        0xFF6B8F88, // Patina Sage
    )

    fun colorFor(key: String): Color {
        val index = Math.floorMod(key.lowercase().hashCode(), spines.size)
        return Color(spines[index])
    }

    fun colorAt(index: Int): Color = Color(spines[Math.floorMod(index, spines.size)])
}

@Composable
fun rememberEditorialColors(): EditorialColors {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val themePref = Preferences.getTheme()
    val isDark = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val isAmoled = isDark && Preferences.isDarkThemeBlack()
    val isDynamic = Preferences.isDynamicColorAccent()
    val weatherPref by WeatherRepository.preferenceState
    val weatherLive by WeatherRepository.liveSnapshotState
    val activeWeatherSpec = remember(weatherPref, weatherLive) {
        WeatherRepository.activePaletteOrNull()
    }
    return remember(context, isDark, isAmoled, isDynamic, activeWeatherSpec) {
        val base = when {
            !isDark -> LightEditorialColors
            isAmoled -> AmoledEditorialColors
            else -> DarkEditorialColors
        }
        when {
            isDynamic -> {
                val dyn = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                base.copy(
                    primary = dyn.primary,
                    onPrimary = dyn.onPrimary,
                    secondaryAccent = dyn.secondary,
                    brass = dyn.primary,
                )
            }
            activeWeatherSpec != null -> {
                if (!isDark) {
                    base.copy(
                        bg = activeWeatherSpec.lightBg,
                        surface1 = activeWeatherSpec.lightSurface1,
                        surface2 = activeWeatherSpec.lightSurface2,
                        surface3 = activeWeatherSpec.lightSurface3,
                        hairline = activeWeatherSpec.lightHairline,
                        primary = activeWeatherSpec.lightPrimary,
                        onPrimary = activeWeatherSpec.lightOnPrimary,
                        brass = activeWeatherSpec.lightPrimary,
                        textPrimary = activeWeatherSpec.lightTextPrimary,
                        textSecondary = activeWeatherSpec.lightTextSecondary,
                        heroWashFallback = activeWeatherSpec.lightHeroWash,
                    )
                } else if (isAmoled) {
                    base.copy(
                        bg = Color(0xFF000000),
                        surface1 = activeWeatherSpec.amoledSurface1,
                        surface2 = activeWeatherSpec.amoledSurface2,
                        surface3 = activeWeatherSpec.amoledSurface3,
                        hairline = activeWeatherSpec.darkHairline,
                        primary = activeWeatherSpec.darkPrimary,
                        onPrimary = activeWeatherSpec.darkOnPrimary,
                        brass = activeWeatherSpec.darkPrimary,
                        textPrimary = activeWeatherSpec.darkTextPrimary,
                        textSecondary = activeWeatherSpec.darkTextSecondary,
                        heroWashFallback = activeWeatherSpec.darkHeroWash,
                    )
                } else {
                    base.copy(
                        bg = activeWeatherSpec.darkBg,
                        surface1 = activeWeatherSpec.darkSurface1,
                        surface2 = activeWeatherSpec.darkSurface2,
                        surface3 = activeWeatherSpec.darkSurface3,
                        hairline = activeWeatherSpec.darkHairline,
                        primary = activeWeatherSpec.darkPrimary,
                        onPrimary = activeWeatherSpec.darkOnPrimary,
                        brass = activeWeatherSpec.darkPrimary,
                        textPrimary = activeWeatherSpec.darkTextPrimary,
                        textSecondary = activeWeatherSpec.darkTextSecondary,
                        heroWashFallback = activeWeatherSpec.darkHeroWash,
                    )
                }
            }
            else -> base
        }
    }
}

private fun EditorialColors.toMaterialColorScheme(dynamicScheme: ColorScheme?): ColorScheme {
    if (dynamicScheme != null) {
        return if (isDark) {
            dynamicScheme.copy(
                background = bg,
                surface = bg,
                surfaceContainerLowest = bg,
                surfaceContainerLow = surface1,
                surfaceContainer = surface1,
                surfaceContainerHigh = surface2,
                surfaceContainerHighest = surface3,
            )
        } else {
            dynamicScheme
        }
    }
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = Color(0xFF382B18),
            onPrimaryContainer = Color(0xFFF5DFBB),
            secondary = secondaryAccent,
            onSecondary = Color(0xFF2B251D),
            secondaryContainer = surface3,
            onSecondaryContainer = textPrimary,
            tertiary = moss,
            onTertiary = Color(0xFF132619),
            tertiaryContainer = Color(0xFF1F3124),
            onTertiaryContainer = Color(0xFFCFE6D4),
            background = bg,
            onBackground = textPrimary,
            surface = bg,
            onSurface = textPrimary,
            surfaceVariant = surface3,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface1,
            surfaceContainer = surface1,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface3,
            outline = Color(0xFF4A433B),
            outlineVariant = hairline,
            error = terracotta,
            onError = Color(0xFF3B140A),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = Color(0xFFF3E2C3),
            onPrimaryContainer = Color(0xFF2E1F06),
            secondary = secondaryAccent,
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = surface3,
            onSecondaryContainer = textPrimary,
            tertiary = moss,
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFD3EBD8),
            onTertiaryContainer = Color(0xFF0C2412),
            background = bg,
            onBackground = textPrimary,
            surface = bg,
            onSurface = textPrimary,
            surfaceVariant = surface3,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = surface1,
            surfaceContainer = surface1,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface3,
            outline = Color(0xFFCFC6B9),
            outlineVariant = hairline,
            error = terracotta,
            onError = Color(0xFFFFFFFF),
        )
    }
}

@Composable
fun BrowseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val editorial = rememberEditorialColors()
    val isDynamic = Preferences.isDynamicColorAccent()
    val isAmoled = editorial.isDark && Preferences.isDarkThemeBlack()
    val scheme = remember(context, editorial, isDynamic) {
        val dyn = if (isDynamic) {
            if (editorial.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
        editorial.toMaterialColorScheme(dyn)
    }
    @Suppress("DEPRECATION")
    SideEffect {
        val activity = context as? AppCompatActivity ?: return@SideEffect
        if (isAmoled) {
            activity.window.setBackgroundDrawable(android.graphics.Color.BLACK.toDrawable())
            activity.window.statusBarColor = android.graphics.Color.BLACK
            activity.window.navigationBarColor = android.graphics.Color.BLACK
        } else if (!isDynamic) {
            val bgInt = editorial.bg.toArgb()
            val navInt = editorial.surface1.toArgb()
            activity.window.setBackgroundDrawable(bgInt.toDrawable())
            activity.window.statusBarColor = bgInt
            activity.window.navigationBarColor = navInt
        }
    }
    CompositionLocalProvider(LocalEditorialColors provides editorial) {
        MaterialTheme(
            colorScheme = scheme,
            typography = EditorialTypography,
            content = content,
        )
    }
}

