package com.eddyizm.tempus.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.theme.EditorialTheme
import com.eddyizm.tempus.ui.theme.EditorialTokens
import com.eddyizm.tempus.ui.theme.LocalEditorialPalette
import com.eddyizm.tempus.ui.theme.rememberEditorialColorScheme
import com.eddyizm.tempus.util.Preferences
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun rememberAppColorScheme(): ColorScheme = rememberEditorialColorScheme()

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

    EditorialTheme(useTabularNumerals = true) {
        val containerColor = LocalEditorialPalette.current.sheetContainer
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = EditorialTokens.SheetTopShape,
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
