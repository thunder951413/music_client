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

private val DarkEditorialColors = EditorialColors(
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

private val AmoledEditorialColors = DarkEditorialColors.copy(
    bg = Color(0xFF000000),
    surface1 = Color(0xFF100F0E),
    surface2 = Color(0xFF181614),
    surface3 = Color(0xFF221F1B),
)

private val LightEditorialColors = EditorialColors(
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

private val EditorialTypography: Typography by lazy {
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
    private val spines = longArrayOf(
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

/**
 * Top atmospheric canopy coupled to the active weather palette (plus subtle 108° rain-glass streaks in RAIN).
 */
@Composable
fun WeatherSkyCanopy(modifier: Modifier = Modifier) {
    val colors = EditorialTheme.colors
    val weatherPref by WeatherRepository.preferenceState
    val weatherLive by WeatherRepository.liveSnapshotState
    val spec = remember(weatherPref, weatherLive) { WeatherRepository.activePaletteOrNull() }
        ?: return

    val primaryGlow = if (colors.isDark) spec.darkCanopyStart else spec.lightCanopyStart
    val secondaryGlow = if (colors.isDark) spec.darkCanopySecondary else spec.lightCanopySecondary
    val isRain = spec.mood == WeatherMood.RAIN

    Canvas(modifier = modifier.fillMaxWidth().height(210.dp)) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(size.width * 0.22f, 0f),
                radius = size.width * 0.92f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow, Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.08f),
                radius = size.width * 0.72f,
            ),
        )
        if (isRain) {
            val streakColor = colors.primary.copy(alpha = if (colors.isDark) 0.055f else 0.075f)
            val step = 28.dp.toPx()
            var x = -40f
            while (x < size.width + 60f) {
                drawLine(
                    color = streakColor,
                    start = Offset(x, 0f),
                    end = Offset(x - size.height * 0.28f, size.height * 0.86f),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
            }
        }
    }
}

/**
 * Clean 38dp circular "我的" (`EditorialProfileButton`) replacing the removed `账号名 · 切号` chip.
 */
@Composable
fun EditorialProfileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    val desc = stringResource(R.string.editorial_profile_button_desc)
    Box(
        modifier = modifier
            .size(38.dp)
            .pressable(onClick = onClick)
            .clip(EditorialRadii.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, EditorialRadii.Pill)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        val headColor = colors.textPrimary
        val shoulderColor = colors.primary
        Canvas(modifier = Modifier.size(18.dp)) {
            val stroke = 1.7.dp.toPx()
            drawCircle(
                color = headColor,
                radius = size.minDimension * 0.19f,
                center = Offset(size.width / 2f, size.height * 0.32f),
                style = Stroke(width = stroke),
            )
            drawArc(
                color = shoulderColor,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(size.width * 0.16f, size.height * 0.56f),
                size = Size(size.width * 0.68f, size.height * 0.54f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Back handler compatible without activity-compose dependency. */
@Composable
fun EditorialBackHandler(
    dispatcher: OnBackPressedDispatcher,
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }
    callback.isEnabled = enabled
    DisposableEffect(dispatcher) {
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

/** Tactile squeeze when pressed. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    if (!enabled) return this
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Editorial surface container: warm dark card fill + 1dp hairline border + 1dp top inner highlight.
 */
@Composable
fun Modifier.editorialCard(
    shape: Shape = EditorialRadii.Card,
    backgroundColor: Color = EditorialTheme.colors.surface1,
    borderColor: Color = EditorialTheme.colors.hairline,
): Modifier {
    val highlight = EditorialTheme.colors.innerHighlight
    return this
        .clip(shape)
        .background(backgroundColor)
        .drawWithContent {
            drawContent()
            val strokePx = 1.dp.toPx()
            drawLine(
                color = highlight,
                start = Offset(0f, strokePx * 0.5f),
                end = Offset(size.width, strokePx * 0.5f),
                strokeWidth = strokePx,
            )
        }
        .border(1.dp, borderColor, shape)
}

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
        color = EditorialTheme.colors.textPrimary,
    )
}

@Composable
fun EditorialEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    serif: Boolean = true,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = if (serif) FontFamily.Serif else EditorialSansFontFamily,
        letterSpacing = 1.4.sp,
        color = EditorialTheme.colors.textTertiary,
        style = LocalTextStyle.current.tnum(),
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary,
        )
        if (badge != null) {
            Text(
                text = badge,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(EditorialRadii.Badge)
                    .background(colors.surface2)
                    .border(1.dp, colors.hairline, EditorialRadii.Badge)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                style = LocalTextStyle.current.tnum(),
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else if (action != null && onAction != null) {
            Text(
                text = "$action ›",
                modifier = Modifier
                    .clip(EditorialRadii.Pill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

/**
 * Conditional drawer menu button so users are never trapped when "hide bottom bar in portrait" is on
 * or in landscape if no NavigationRailView is visible.
 */
@OptIn(UnstableApi::class)
@Composable
fun EditorialDrawerButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val activity = context as? MainActivity ?: return
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hidePortraitNav = Preferences.getHideBottomNavbarOnPortrait()

    val showDrawer = remember(activity, isLandscape, hidePortraitNav) {
        val drawer = activity.findViewById<DrawerLayout?>(R.id.drawer_layout)
        if (drawer == null) {
            false
        } else if (hidePortraitNav) {
            true
        } else if (isLandscape && !activity.isNavigationDrawerLocked) {
            val contentRoot = activity.findViewById<ViewGroup?>(android.R.id.content)
            !hasVisibleNavigationRail(contentRoot)
        } else {
            false
        }
    }
    if (!showDrawer) return

    val colors = EditorialTheme.colors
    val desc = stringResource(R.string.editorial_drawer_desc)
    IconButton(
        onClick = {
            activity.findViewById<DrawerLayout?>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        },
        modifier = modifier
            .size(36.dp)
            .semantics { contentDescription = desc },
    ) {
        val strokeColor = colors.textPrimary
        Canvas(modifier = Modifier.size(18.dp)) {
            val stroke = 1.8.dp.toPx()
            val ys = floatArrayOf(size.height * 0.22f, size.height * 0.50f, size.height * 0.78f)
            for (y in ys) {
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun hasVisibleNavigationRail(view: View?): Boolean {
    if (view == null || view.visibility != View.VISIBLE) return false
    if (view is NavigationRailView) return true
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            if (hasVisibleNavigationRail(view.getChildAt(i))) return true
        }
    }
    return false
}

/** Resolves the active Subsonic server display name from Room + Preferences fallback. */
@Composable
fun rememberActiveServerLabel(): String {
    val defaultLabel = stringResource(R.string.editorial_server_default)
    val liveServers = remember { ServerRepository().liveServer }
    val servers by liveServers.observeAsState(emptyList())
    val serverId = Preferences.getServerId()
    val matchedName = servers.firstOrNull { it.serverId == serverId }?.serverName?.takeIf { it.isNotBlank() }
    if (matchedName != null) return matchedName

    val rawUrl = Preferences.getServer()?.takeIf { it.isNotBlank() }
    val host = rawUrl?.let { url ->
        runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
    }?.takeIf { it.isNotBlank() }

    return host ?: Preferences.getUser()?.takeIf { it.isNotBlank() } ?: defaultLabel
}

/**
 * Compact warm-dark server/account pill with user/server avatar monogram (never a fake "online" green dot).
 */
@Composable
fun ServerChip(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val avatarInitial = remember(label) {
        (Preferences.getUser()?.trim()?.firstOrNull() ?: label.trim().firstOrNull() ?: 'S').uppercaseChar().toString()
    }
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(onClick = onClick)
            .clip(EditorialRadii.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, EditorialRadii.Pill)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(EditorialRadii.Pill)
                .background(colors.brass.copy(alpha = 0.18f))
                .border(1.dp, colors.brass.copy(alpha = 0.42f), EditorialRadii.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarInitial,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.brass,
            )
        }
        Text(
            text = if (subtitle.isNullOrBlank()) label else "$label · $subtitle",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_navigate_next),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 44dp Global Search Pill shared across Home, Discover, and Library. */
@Composable
fun GlobalSearchPill(
    placeholder: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(EditorialRadii.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, EditorialRadii.Pill)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = placeholder,
            fontSize = 15.sp,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Extracts a muted, readable record-spine accent color from the lead album cover when available. */
@Composable
fun rememberCoverSpineColor(coverArtId: String?, fallback: Color): Color {
    val context = LocalContext.current
    val isDark = EditorialTheme.colors.isDark
    var resolved by remember(coverArtId, fallback, isDark) { mutableStateOf(fallback) }

    LaunchedEffect(coverArtId, isDark) {
        if (coverArtId.isNullOrBlank()) {
            resolved = fallback
            return@LaunchedEffect
        }
        val sampled = withContext(Dispatchers.IO) {
            runCatching {
                val url = CustomGlideRequest.createUrl(coverArtId, 96)
                val bitmap: Bitmap = Glide.with(context).asBitmap().load(url).submit(32, 32).get()
                sampleCoverSpineTone(bitmap, isDark)
            }.getOrNull()
        }
        if (sampled != null) {
            resolved = sampled
        }
    }
    return resolved
}

private fun sampleCoverSpineTone(bitmap: Bitmap, isDark: Boolean): Color? {
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    val stepX = (w / 5).coerceAtLeast(1)
    val stepY = (h / 5).coerceAtLeast(1)
    var y = stepY / 2
    while (y < h) {
        var x = stepX / 2
        while (x < w) {
            val px = bitmap[x, y]
            rSum += (px shr 16) and 0xFF
            gSum += (px shr 8) and 0xFF
            bSum += px and 0xFF
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return null
    val avg = android.graphics.Color.rgb(
        (rSum / count).toInt(),
        (gSum / count).toInt(),
        (bSum / count).toInt(),
    )
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(avg, lab)
    val chroma = hypot(lab[1], lab[2])
    if (chroma < 4.0) {
        lab[1] = 4.0
        lab[2] = 14.0
    } else if (chroma > 28.0) {
        val scale = 28.0 / chroma
        lab[1] *= scale
        lab[2] *= scale
    }
    lab[0] = if (isDark) lab[0].coerceIn(54.0, 70.0) else lab[0].coerceIn(36.0, 50.0)
    val argb = ColorUtils.LABToColor(lab[0], lab[1], lab[2])
    return Color(argb).takeIf { it.toArgb() != 0 }
}

/**
 * Record Sleeve Card (16dp radius, surface1, 1dp hairline border, 3dp cover-derived vertical spine on left,
 * 2×2 album cover mosaic or warm vinyl-sleeve monogram fallback, clean title & tnum subtitle).
 */
@Composable
fun RecordSleeveCard(
    title: String,
    subtitle: String,
    spineColor: Color,
    modifier: Modifier = Modifier,
    coverArtIds: List<String> = emptyList(),
    monogram: String? = null,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val effectiveSpine = rememberCoverSpineColor(
        coverArtId = coverArtIds.firstOrNull { it.isNotBlank() },
        fallback = spineColor,
    )
    Row(
        modifier = modifier
            .height(78.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(effectiveSpine.copy(alpha = 0.88f)),
        )
        CoverMosaicBox(
            coverArtIds = coverArtIds,
            accent = effectiveSpine,
            monogram = monogram ?: title.take(1),
            modifier = Modifier
                .padding(start = 9.dp)
                .size(44.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 9.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.tnum(),
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun CoverMosaicBox(
    coverArtIds: List<String>,
    accent: Color,
    monogram: String,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    val validIds = remember(coverArtIds) {
        coverArtIds.filter { it.isNotBlank() }.distinct().take(4)
    }
    Box(
        modifier = modifier
            .clip(EditorialRadii.ListCover)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, EditorialRadii.ListCover),
        contentAlignment = Alignment.Center,
    ) {
        when {
            validIds.size >= 4 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (r in 0..1) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            for (c in 0..1) {
                                val artId = validIds[r * 2 + c]
                                GlideImage(
                                    model = CustomGlideRequest.createUrl(artId, 140),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(EditorialRadii.Badge)
                                        .background(colors.surface3),
                                )
                            }
                        }
                    }
                }
            }
            validIds.isNotEmpty() -> {
                GlideImage(
                    model = CustomGlideRequest.createUrl(validIds.first(), 180),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                // Warm vinyl-sleeve fallback with subtle concentric grooves + clean monogram
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension * 0.42f
                    drawCircle(color = colors.surface3, radius = maxR, center = center)
                    drawCircle(
                        color = colors.hairline,
                        radius = maxR * 0.72f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    drawCircle(color = accent.copy(alpha = 0.28f), radius = maxR * 0.34f, center = center)
                }
                Text(
                    text = monogram.take(2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
        }
    }
}

/**
 * Circular Match Rate Ring (Moss / Brass / Terracotta on #28241F surface3 track) with tnum percentage.
 */
@Composable
fun MatchRateRing(
    matched: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = EditorialTheme.colors
    val safeTotal = total.coerceAtLeast(1)
    val ratio = (matched.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val pct = (ratio * 100f).toInt()
    val ringColor = when {
        total <= 0 -> colors.textTertiary
        ratio >= 0.70f -> colors.moss
        ratio >= 0.35f -> colors.brass
        else -> colors.terracotta
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = colors.surface3,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (total > 0 && ratio > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = if (total > 0) "$pct%" else "—",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (total > 0) colors.textPrimary else colors.textTertiary,
            style = LocalTextStyle.current.tnum(),
        )
    }
}

/**
 * Subtle 40dp dashed warm ring shown on External Chart cards before Top-100 library matching runs.
 */
@Composable
fun UnmatchedChartRing(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = EditorialTheme.colors
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()), 0f)
            drawArc(
                color = colors.brass.copy(alpha = 0.42f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round, pathEffect = dash),
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * 3-Segment stacked progress bar: Playable/Matched (Moss #8DB596), Pending/Ambiguous (Brass #CFA46A),
 * Missing (Terracotta #D9785F) on #28241F surface3 track.
 */
@Composable
fun ThreeSegmentBar(
    matched: Int,
    pending: Int,
    missing: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val colors = EditorialTheme.colors
    val m = matched.coerceAtLeast(0)
    val p = pending.coerceAtLeast(0)
    val x = missing.coerceAtLeast(0)
    val sum = m + p + x
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(EditorialRadii.Pill)
            .background(colors.surface3),
    ) {
        if (sum > 0) {
            if (m > 0) {
                Box(
                    Modifier
                        .weight(m.toFloat())
                        .fillMaxHeight()
                        .background(colors.moss),
                )
            }
            if (p > 0) {
                Box(
                    Modifier
                        .weight(p.toFloat())
                        .fillMaxHeight()
                        .background(colors.brass),
                )
            }
            if (x > 0) {
                Box(
                    Modifier
                        .weight(x.toFloat())
                        .fillMaxHeight()
                        .background(colors.terracotta),
                )
            }
        }
    }
}

/** Compact Brass / Warm-Dark outline action pill (supports optional inline progress fraction). */
@Composable
fun EditorialOutlinePill(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    accent: Color = EditorialTheme.colors.brass,
    active: Boolean = false,
    progress: Float? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val borderColor = if (active || progress != null) accent.copy(alpha = 0.62f) else accent.copy(alpha = 0.36f)
    val bgColor = if (active) accent.copy(alpha = 0.16f) else colors.surface2
    Box(
        modifier = modifier
            .height(height)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(EditorialRadii.Pill)
            .background(bgColor)
            .border(1.dp, borderColor, EditorialRadii.Pill),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            val clamped = progress.coerceIn(0.04f, 1f)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .background(accent.copy(alpha = 0.22f)),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = painterResource(leadingIcon),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) accent else colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

/**
 * Editorial Genre / Category Sleeve Tile (replaces old Spotify rainbow CategoryTile).
 */
@Composable
fun CategoryTile(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    height: Dp = 64.dp,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier
            .height(height)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.85f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.tnum(),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_navigate_next),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(16.dp),
        )
    }
}

/**
 * Library (`音乐库`) unified Editorial Header:
 * Title + active Server avatar chip + optional Folder switcher + Settings icon button +
 * 44dp GlobalSearchPill + single-row horizontally scrollable quick-jump strip.
 */
@OptIn(UnstableApi::class)
object LibraryHeaderBinder {
    @JvmStatic
    fun bind(
        view: ComposeView,
        fragment: Fragment,
        libraryViewModel: LibraryViewModel,
        mainViewModel: MainViewModel,
    ) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            BrowseTheme {
                val folders by libraryViewModel.getMusicFolders(fragment.viewLifecycleOwner).observeAsState(emptyList())
                val activeFolderSignal by mainViewModel.activeMusicFolderId.observeAsState()
                val activeFolderId = remember(activeFolderSignal, folders) { Preferences.getActiveMusicFolderId() }
                var showProfileSheet by remember { mutableStateOf(false) }
                LibraryEditorialHeader(
                    folders = folders.orEmpty(),
                    activeFolderId = activeFolderId,
                    onSelectFolder = { folderId -> mainViewModel.setActiveMusicFolderId(folderId) },
                    onOpenProfile = { showProfileSheet = true },
                    onOpenSearch = {
                        val args = Bundle().apply { putBoolean(SearchFragment.ARG_FOCUS, true) }
                        fragment.findNavController().navigate(R.id.searchFragment, args)
                    },
                    onOpenPlaylists = {
                        val args = Bundle().apply { putString(Constants.PLAYLIST_ALL, Constants.PLAYLIST_ALL) }
                        fragment.findNavController().navigate(R.id.playlistCatalogueFragment, args)
                    },
                    onOpenAlbums = { fragment.findNavController().navigate(R.id.albumCatalogueFragment) },
                    onOpenArtists = { fragment.findNavController().navigate(R.id.artistCatalogueFragment) },
                    onOpenStarred = {
                        val args = Bundle().apply { putString(Constants.MEDIA_STARRED, Constants.MEDIA_STARRED) }
                        fragment.findNavController().navigate(R.id.songListPageFragment, args)
                    },
                    onOpenDownloads = { fragment.findNavController().navigate(R.id.downloadFragment) },
                    onOpenFolders = {
                        val selectedFolder = folders.orEmpty().firstOrNull { it.id == activeFolderId }
                            ?: folders.orEmpty().firstOrNull()
                        val args = if (selectedFolder != null) {
                            Bundle().apply { putParcelable(Constants.MUSIC_FOLDER_OBJECT, selectedFolder) }
                        } else {
                            null
                        }
                        fragment.findNavController().navigate(R.id.indexFragment, args)
                    },
                    onOpenFilter = { fragment.findNavController().navigate(R.id.filterFragment) },
                )
                if (showProfileSheet) {
                    ProfileCenterSheet(
                        fragment = fragment,
                        onDismiss = { showProfileSheet = false },
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryEditorialHeader(
    folders: List<MusicFolder>,
    activeFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val folderLabel = remember(folders, activeFolderId) {
        MusicFolderUtil.resolveMusicFolderName(folders, activeFolderId)
    }
    var folderMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        WeatherSkyCanopy()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            EditorialDrawerButton(modifier = Modifier.padding(end = 6.dp))
            PageTitle(
                text = stringResource(R.string.editorial_library_title),
                modifier = Modifier.weight(1f),
            )
            if (folders.size > 1) {
                Box(Modifier.padding(end = 6.dp)) {
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .pressable { folderMenuExpanded = true }
                            .clip(EditorialRadii.Pill)
                            .background(colors.surface2)
                            .border(1.dp, colors.hairline, EditorialRadii.Pill)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = colors.brass,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = folderLabel ?: stringResource(R.string.editorial_library_all_folders),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = folderMenuExpanded,
                        onDismissRequest = { folderMenuExpanded = false },
                        modifier = Modifier.background(colors.surface2),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.editorial_library_all_folders),
                                    color = if (activeFolderId == null) colors.primary else colors.textPrimary,
                                )
                            },
                            onClick = {
                                folderMenuExpanded = false
                                onSelectFolder(null)
                            },
                        )
                        folders.forEach { folder ->
                            val isSelected = folder.id == activeFolderId
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        folder.name ?: folder.id.orEmpty(),
                                        color = if (isSelected) colors.primary else colors.textPrimary,
                                    )
                                },
                                onClick = {
                                    folderMenuExpanded = false
                                    onSelectFolder(folder.id)
                                },
                            )
                        }
                    }
                }
            }
            EditorialProfileButton(onClick = onOpenProfile)
        }

        GlobalSearchPill(
            placeholder = stringResource(R.string.editorial_search_library_placeholder),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            onClick = onOpenSearch,
        )

        val chips = listOf(
            Triple(R.string.editorial_chip_playlists, R.drawable.ic_playlist, onOpenPlaylists),
            Triple(R.string.editorial_chip_albums, R.drawable.ic_albums, onOpenAlbums),
            Triple(R.string.editorial_chip_artists, R.drawable.ic_artists, onOpenArtists),
            Triple(R.string.editorial_chip_starred, R.drawable.ic_favorites_outlined, onOpenStarred),
            Triple(R.string.editorial_chip_downloads, R.drawable.ic_download, onOpenDownloads),
            Triple(R.string.editorial_chip_folders, R.drawable.ic_folder, onOpenFolders),
            Triple(R.string.editorial_chip_filter, R.drawable.ic_filter_list, onOpenFilter),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { (labelRes, iconRes, action) ->
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .pressable(onClick = action)
                        .clip(EditorialRadii.Pill)
                        .background(colors.surface3)
                        .border(
                            width = 1.dp,
                            color = colors.hairline,
                            shape = EditorialRadii.Pill,
                        )
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = colors.brass,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                }
            }
        }
        }
    }
}
