package com.eddyizm.tempus.ui.browse

import android.net.Uri
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.repository.ServerRepository
import com.eddyizm.tempus.subsonic.models.MusicFolder
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.MusicFolderUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.viewmodel.LibraryViewModel
import com.eddyizm.tempus.viewmodel.MainViewModel

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
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val moss: Color,
    val terracotta: Color,
    val brass: Color,
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
    textPrimary = Color(0xFFF2ECE3),
    textSecondary = Color(0xA3F2ECE3),
    textTertiary = Color(0x66F2ECE3),
    moss = Color(0xFF8DB596),
    terracotta = Color(0xFFD9785F),
    brass = Color(0xFFCFA46A),
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
    hairline = Color(0x1A1D1A17),
    innerHighlight = Color(0x66FFFFFF),
    primary = Color(0xFF9A7136),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1D1A17),
    textSecondary = Color(0xA31D1A17),
    textTertiary = Color(0x661D1A17),
    moss = Color(0xFF4B7554),
    terracotta = Color(0xFFB8523A),
    brass = Color(0xFF9A7136),
)

val LocalEditorialColors = staticCompositionLocalOf { DarkEditorialColors }

object EditorialTheme {
    val colors: EditorialColors
        @Composable get() = LocalEditorialColors.current
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
    fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
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
    val systemDark = isSystemInDarkTheme()
    val themePref = Preferences.getTheme()
    val isDark = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val isAmoled = isDark && Preferences.isDarkThemeBlack()
    return remember(isDark, isAmoled) {
        when {
            !isDark -> LightEditorialColors
            isAmoled -> AmoledEditorialColors
            else -> DarkEditorialColors
        }
    }
}

private fun EditorialColors.toMaterialColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surface3,
            onPrimaryContainer = textPrimary,
            secondary = moss,
            onSecondary = onPrimary,
            secondaryContainer = surface2,
            onSecondaryContainer = textPrimary,
            tertiary = terracotta,
            background = bg,
            onBackground = textPrimary,
            surface = bg,
            onSurface = textPrimary,
            surfaceVariant = surface2,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface1,
            surfaceContainer = surface1,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface3,
            outline = hairline,
            outlineVariant = hairline,
            error = terracotta,
            onError = onPrimary,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surface3,
            onPrimaryContainer = textPrimary,
            secondary = moss,
            onSecondary = onPrimary,
            secondaryContainer = surface2,
            onSecondaryContainer = textPrimary,
            tertiary = terracotta,
            background = bg,
            onBackground = textPrimary,
            surface = bg,
            onSurface = textPrimary,
            surfaceVariant = surface2,
            onSurfaceVariant = textSecondary,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface1,
            surfaceContainer = surface1,
            surfaceContainerHigh = surface2,
            surfaceContainerHighest = surface3,
            outline = hairline,
            outlineVariant = hairline,
            error = terracotta,
            onError = onPrimary,
        )
    }

@Composable
fun BrowseTheme(content: @Composable () -> Unit) {
    val editorial = rememberEditorialColors()
    val scheme = remember(editorial) { editorial.toMaterialColorScheme() }
    CompositionLocalProvider(LocalEditorialColors provides editorial) {
        MaterialTheme(colorScheme = scheme, content = content)
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
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
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
        fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
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
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textTertiary,
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
            )
        }
    }
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
        runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
    }?.takeIf { it.isNotBlank() }

    return host ?: Preferences.getUser()?.takeIf { it.isNotBlank() } ?: defaultLabel
}

/** Compact warm-dark server/account pill that navigates to settings or opens switcher. */
@Composable
fun ServerChip(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(onClick = onClick)
            .clip(EditorialRadii.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, EditorialRadii.Pill)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(EditorialRadii.Pill)
                .background(colors.moss),
        )
        Text(
            text = if (subtitle.isNullOrBlank()) label else "$label · $subtitle",
            fontSize = 12.sp,
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
            fontSize = 14.sp,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Record Sleeve Card (16dp radius, surface1, 1dp hairline border, 3dp muted vertical spine on left,
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
                .background(spineColor.copy(alpha = 0.85f)),
        )
        CoverMosaicBox(
            coverArtIds = coverArtIds,
            accent = spineColor,
            monogram = monogram ?: title.take(1),
            modifier = Modifier
                .padding(start = 11.dp)
                .size(50.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, end = 12.dp),
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
                // Warm vinyl-sleeve fallback with subtle concentric grooves + serif/clean monogram
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
                    fontWeight = FontWeight.Bold,
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
    size: Dp = 38.dp,
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
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (total > 0) colors.textPrimary else colors.textTertiary,
            style = LocalTextStyle.current.tnum(),
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
                fontSize = 12.sp,
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
                .background(color.copy(alpha = 0.82f)),
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
                    fontSize = 12.sp,
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
 * Title + active Server/Folder chip + Settings icon button + 44dp GlobalSearchPill +
 * single-row horizontally scrollable Filter Chip strip (歌单 / 专辑 / 歌手 / 收藏 / 已下载 / 文件夹 / 筛选).
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
                LibraryEditorialHeader(
                    folders = folders.orEmpty(),
                    activeFolderId = activeFolderId,
                    onSelectFolder = { folderId -> mainViewModel.setActiveMusicFolderId(folderId) },
                    onOpenSettings = { fragment.findNavController().navigate(R.id.settingsFragment) },
                    onOpenSearch = {
                        fragment.findNavController().navigate(
                            R.id.searchFragment,
                            bundleOf(SearchFragment.ARG_FOCUS to true),
                        )
                    },
                    onOpenPlaylists = {
                        fragment.findNavController().navigate(
                            R.id.playlistCatalogueFragment,
                            bundleOf(Constants.PLAYLIST_ALL to Constants.PLAYLIST_ALL),
                        )
                    },
                    onOpenAlbums = { fragment.findNavController().navigate(R.id.albumCatalogueFragment) },
                    onOpenArtists = { fragment.findNavController().navigate(R.id.artistCatalogueFragment) },
                    onOpenStarred = {
                        fragment.findNavController().navigate(
                            R.id.songListPageFragment,
                            bundleOf(Constants.MEDIA_STARRED to Constants.MEDIA_STARRED),
                        )
                    },
                    onOpenDownloads = { fragment.findNavController().navigate(R.id.downloadFragment) },
                    onOpenFolders = {
                        val selectedFolder = folders.orEmpty().firstOrNull { it.id == activeFolderId }
                            ?: folders.orEmpty().firstOrNull()
                        val args = BundleBuilder.musicFolderArgs(selectedFolder)
                        fragment.findNavController().navigate(R.id.indexFragment, args)
                    },
                    onOpenFilter = { fragment.findNavController().navigate(R.id.filterFragment) },
                )
            }
        }
    }
}

private object BundleBuilder {
    fun musicFolderArgs(folder: MusicFolder?) =
        if (folder != null) bundleOf(Constants.MUSIC_FOLDER_OBJECT to folder) else null
}

@Composable
fun LibraryEditorialHeader(
    folders: List<MusicFolder>,
    activeFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onOpenSettings: () -> Unit,
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
    val serverLabel = rememberActiveServerLabel()
    val folderLabel = remember(folders, activeFolderId) {
        MusicFolderUtil.resolveMusicFolderName(folders, activeFolderId)
    }
    var folderMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageTitle(
                text = stringResource(R.string.editorial_library_title),
                modifier = Modifier.weight(1f),
            )
            Box {
                ServerChip(
                    label = serverLabel,
                    subtitle = folderLabel,
                    onClick = {
                        if (folders.size > 1) {
                            folderMenuExpanded = true
                        } else {
                            onOpenSettings()
                        }
                    },
                )
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
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.editorial_settings_desc),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(21.dp),
                )
            }
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
            chips.forEachIndexed { index, (labelRes, iconRes, action) ->
                val highlightFirst = index == 0
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .pressable(onClick = action)
                        .clip(EditorialRadii.Pill)
                        .background(if (highlightFirst) colors.surface3 else colors.surface1)
                        .border(
                            width = 1.dp,
                            color = if (highlightFirst) colors.primary.copy(alpha = 0.42f) else colors.hairline,
                            shape = EditorialRadii.Pill,
                        )
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = if (highlightFirst) colors.primary else colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (highlightFirst) colors.textPrimary else colors.textSecondary,
                    )
                }
            }
        }
    }
}
