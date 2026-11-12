package com.eddyizm.tempus.ui.queue

import android.os.Build
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Preferences
import sh.calvin.reorderable.rememberReorderableLazyListState

private val EditorialWarmDarkScheme = darkColorScheme(
    primary = Color(0xFFCFA46A),
    onPrimary = Color(0xFF1D160B),
    primaryContainer = Color(0xFF382B18),
    onPrimaryContainer = Color(0xFFF5DFBB),
    secondary = Color(0xFFC9BFA9),
    onSecondary = Color(0xFF2B251D),
    secondaryContainer = Color(0xFF28241F),
    onSecondaryContainer = Color(0xFFEAE0D0),
    tertiary = Color(0xFF8DB596),
    onTertiary = Color(0xFF132619),
    tertiaryContainer = Color(0xFF1F3124),
    onTertiaryContainer = Color(0xFFCFE6D4),
    error = Color(0xFFD9785F),
    onError = Color(0xFF3B140A),
    errorContainer = Color(0xFF3B1F19),
    onErrorContainer = Color(0xFFF6CFC5),
    background = Color(0xFF0E0D0C),
    onBackground = Color(0xFFF2ECE3),
    surface = Color(0xFF0E0D0C),
    onSurface = Color(0xFFF2ECE3),
    surfaceVariant = Color(0xFF28241F),
    onSurfaceVariant = Color(0xFFA39C93),
    outline = Color(0xFF4A433B),
    outlineVariant = Color(0xFF2A2520),
    inverseSurface = Color(0xFFF2ECE3),
    inverseOnSurface = Color(0xFF1E1B18),
    inversePrimary = Color(0xFF9A7136),
    surfaceDim = Color(0xFF0E0D0C),
    surfaceBright = Color(0xFF332E28),
    surfaceContainerLowest = Color(0xFF0E0D0C),
    surfaceContainerLow = Color(0xFF161412),
    surfaceContainer = Color(0xFF161412),
    surfaceContainerHigh = Color(0xFF1E1B18),
    surfaceContainerHighest = Color(0xFF28241F),
)

private val EditorialWarmLightScheme = lightColorScheme(
    primary = Color(0xFF9A7136),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E2C3),
    onPrimaryContainer = Color(0xFF2E1F06),
    secondary = Color(0xFF655D52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAE2D6),
    onSecondaryContainer = Color(0xFF211B13),
    tertiary = Color(0xFF456B4E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3EBD8),
    onTertiaryContainer = Color(0xFF0C2412),
    error = Color(0xFFB85039),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFADBD4),
    onErrorContainer = Color(0xFF3D0D04),
    background = Color(0xFFF5F1EA),
    onBackground = Color(0xFF1D1A17),
    surface = Color(0xFFF5F1EA),
    onSurface = Color(0xFF1D1A17),
    surfaceVariant = Color(0xFFE5DDD0),
    onSurfaceVariant = Color(0xFF665F56),
    outline = Color(0xFFCFC6B9),
    outlineVariant = Color(0xFFE3DBD0),
    inverseSurface = Color(0xFF2C2722),
    inverseOnSurface = Color(0xFFF5F1EA),
    inversePrimary = Color(0xFFCFA46A),
    surfaceDim = Color(0xFFE5DDD0),
    surfaceBright = Color(0xFFFFFDF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFFFFDF9),
    surfaceContainerHigh = Color(0xFFEFE9DF),
    surfaceContainerHighest = Color(0xFFE5DDD0),
)

@Composable
fun rememberAppColorScheme(): ColorScheme {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }

    val isDynamic = Preferences.isDynamicColorAccent() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val defaultScheme = when {
        isDynamic -> if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> EditorialWarmDarkScheme
        else -> EditorialWarmLightScheme
    }

    val isAmoled = isDark && Preferences.isDarkThemeBlack()

    return remember(context, isDark, isAmoled, isDynamic) {
        // MaterialColors resolves references and ColorStateLists correctly;
        // reading TypedValue.data by hand does not (yielding transparent garbage).
        fun resolveColor(@AttrRes attr: Int, fallback: Color): Color {
            val resolved = Color(
                com.google.android.material.color.MaterialColors.getColor(
                    context, attr, fallback.toArgb()
                )
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
            secondaryContainer = if (isAmoled) Color(0xFF221F1B) else secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = if (isAmoled) Color.Black else surface,
            onBackground = onSurface,
            surface = if (isAmoled) Color.Black else surface,
            surfaceVariant = if (isAmoled) Color(0xFF121110) else surfaceContainerHighest,
            surfaceContainerLowest = if (isAmoled) Color.Black else surfaceContainerLowest,
            surfaceContainerLow = if (isAmoled) Color(0xFF0A0908) else surfaceContainerLow,
            surfaceContainer = if (isAmoled) Color(0xFF121110) else surfaceContainer,
            surfaceContainerHigh = if (isAmoled) Color(0xFF181614) else surfaceContainerHigh,
            surfaceContainerHighest = if (isAmoled) Color(0xFF221F1B) else surfaceContainerHighest,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            outline = if (isAmoled) Color(0xFF3A342E) else outline,
            outlineVariant = if (isAmoled) Color(0xFF1F1C18) else outlineVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    queueSongs: List<Child>,
    currentIndex: Int,
    isPlaying: Boolean,
    downloadedIds: Set<String>,
    onTrackClick: (Int, Child, List<Child>) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onSwapTracks: (from: Int, to: Int) -> Unit,
    onClearUpcoming: (List<Child>, Int) -> Unit,
    onShuffleUpcoming: (List<Child>, Int) -> Unit,
    onSaveToPlaylist: (List<Child>) -> Unit,
    onDownloadAll: (List<Child>) -> Unit,
    onLoadSavedQueue: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    currentSongId: String? = null,
    externalRefreshTrigger: Any? = null,
    onSheetReady: ((suspend () -> Unit)) -> Unit = {},
) {
    if (!isOpen) return

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }
    val isAmoled = isDark && Preferences.isDarkThemeBlack()
    val isDynamic = Preferences.isDynamicColorAccent() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = rememberAppColorScheme()
    val containerColor = when {
        isAmoled -> Color(0xFF0A0908)
        isDynamic -> if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
        isDark -> Color(0xFF1E1B18)
        else -> Color(0xFFFFFDF9)
    }

    val baseTypography = MaterialTheme.typography
    val queueTypography = remember(baseTypography) {
        baseTypography.copy(
            bodyLarge = baseTypography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            bodyMedium = baseTypography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            bodySmall = baseTypography.bodySmall.copy(fontFeatureSettings = "tnum"),
            labelLarge = baseTypography.labelLarge.copy(fontFeatureSettings = "tnum"),
            labelMedium = baseTypography.labelMedium.copy(fontFeatureSettings = "tnum"),
            labelSmall = baseTypography.labelSmall.copy(fontFeatureSettings = "tnum"),
        )
    }

    var reorderedChildList by remember { mutableStateOf<List<Child>?>(null) }
    val currentRawSongs = reorderedChildList ?: queueSongs

    val currentSongs = remember(currentRawSongs) {
        // Duplicate tracks need distinct keys, hence "id#n" past the first occurrence.
        val counts = HashMap<String, Int>()
        currentRawSongs.map { child ->
            val occ = counts.getOrDefault(child.id, 0)
            counts[child.id] = occ + 1
            val stableId = if (occ == 0) child.id else "${child.id}#$occ"
            QueueSongItem(
                stableId = stableId,
                song = child
            )
        }
    }

    val currentPlayingStableId = remember(queueSongs, currentIndex, currentSongId) {
        if (currentIndex in queueSongs.indices) {
            val counts = HashMap<String, Int>()
            var targetStableId: String? = null
            for ((i, song) in queueSongs.withIndex()) {
                val occ = counts.getOrDefault(song.id, 0)
                counts[song.id] = occ + 1
                if (i == currentIndex) {
                    targetStableId = if (occ == 0) song.id else "${song.id}#$occ"
                    break
                }
            }
            targetStableId
        } else {
            null
        }
    }

    val effectiveCurrentIndex = remember(currentSongs, currentIndex, currentPlayingStableId, currentSongId) {
        if (currentPlayingStableId != null) {
            val idx = currentSongs.indexOfFirst { it.stableId == currentPlayingStableId }
            if (idx >= 0) idx else currentIndex
        } else if (currentSongId != null) {
            val idx = currentSongs.indexOfFirst { it.song.id == currentSongId }
            if (idx >= 0) idx else currentIndex
        } else {
            currentIndex
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(sheetState) {
        onSheetReady { sheetState.hide() }
    }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = effectiveCurrentIndex.coerceAtLeast(0)
    )

    val hapticFeedback = LocalHapticFeedback.current
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val reorderScrollPadding = PaddingValues(
        bottom = 72.dp + navBarPadding.calculateBottomPadding()
    )

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = reorderScrollPadding
    ) { from, to ->
        if (dragStartIndex == null) {
            dragStartIndex = from.index
        }
        dragEndIndex = to.index
        val current = currentRawSongs.toMutableList()
        if (from.index in current.indices && to.index in current.indices) {
            val item = current.removeAt(from.index)
            current.add(to.index, item)
            reorderedChildList = current
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val totalDurationSeconds = remember(currentSongs) {
        currentSongs.sumOf { (it.song.duration ?: 0).toLong() }
    }

    val upcomingCount = remember(currentSongs.size, effectiveCurrentIndex) {
        if (effectiveCurrentIndex >= 0) {
            (currentSongs.size - (effectiveCurrentIndex + 1)).coerceAtLeast(0)
        } else {
            currentSongs.size
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = queueTypography
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = null,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerQueueHeader(
                    songCount = currentSongs.size,
                    totalDurationSeconds = totalDurationSeconds,
                    upcomingSongsCount = upcomingCount,
                    isSyncEnabled = Preferences.isSyncronizationEnabled(),
                    onClearUpcoming = { onClearUpcoming(currentRawSongs, effectiveCurrentIndex) },
                    onShuffleUpcoming = { onShuffleUpcoming(currentRawSongs, effectiveCurrentIndex) },
                    onSaveToPlaylist = { onSaveToPlaylist(currentSongs.map { it.song }) },
                    onDownloadAll = { onDownloadAll(currentSongs.map { it.song }) },
                    onLoadSavedQueue = onLoadSavedQueue,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat
                )

                PlayerQueueList(
                    items = currentSongs,
                    currentIndex = effectiveCurrentIndex,
                    isPlaying = isPlaying,
                    lazyListState = lazyListState,
                    reorderableState = reorderableState,
                    itemContainerColor = containerColor,
                    downloadedIds = downloadedIds,
                    externalRefreshTrigger = externalRefreshTrigger,
                    onDragStarted = {
                        dragStartIndex = null
                        dragEndIndex = null
                    },
                    onDragStopped = {
                        val initialIndex = dragStartIndex
                        val finalIndex = dragEndIndex
                        dragStartIndex = null
                        dragEndIndex = null
                        reorderedChildList = null
                        if (initialIndex != null && finalIndex != null && initialIndex != finalIndex) {
                            onSwapTracks(initialIndex, finalIndex)
                        }
                    },
                    onTrackClick = { clickedIdx, clickedChild ->
                        onTrackClick(clickedIdx, clickedChild, currentRawSongs)
                    },
                    onRemoveTrack = { removeIndex, _ ->
                        reorderedChildList = null
                        onRemoveTrack(removeIndex)
                    }
                )
            }
        }
    }
}
