package com.eddyizm.tempus.ui.browse

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.eddyizm.tempus.R
import com.eddyizm.tempus.discovery.ChartMatches
import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.discovery.ImportedPlaylist
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import kotlinx.coroutines.launch


/** Live inline state for the 32dp `整理歌单` pill in `曲库画廊`. */
data class OrganizeProgressState(
    val running: Boolean = false,
    val phase: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val organizedCount: Int = 0,
)

/** Navigation & action callbacks owned by [DiscoverFragment]. */
interface DiscoverActions {
    fun openSearch()
    fun openSettings()
    fun openExternalChart(source: ChartSource)
    fun openDecade(decade: Int)
    fun openGenre(genre: Genre)
    fun openAllGenres()
    fun openArtist(artist: ArtistID3)
    fun shuffleArtists(artists: List<ArtistID3>)
    fun exportAllMissing()
    fun organizePlaylists()
    fun importExternalPlaylist(initialUrl: String? = null)
}

/** 5 record-store language shelves (`华语 / 粤语 / 日语 / 韩语 / 欧美`). */
enum class LanguageShelf(
    @param:StringRes val labelRes: Int,
    @param:StringRes val codeRes: Int,
    val spineIndex: Int,
) {
    MANDARIN(R.string.editorial_lang_mandarin, R.string.editorial_lang_code_mandarin, 0),
    CANTONESE(R.string.editorial_lang_cantonese, R.string.editorial_lang_code_cantonese, 4),
    JAPANESE(R.string.editorial_lang_japanese, R.string.editorial_lang_code_japanese, 1),
    KOREAN(R.string.editorial_lang_korean, R.string.editorial_lang_code_korean, 2),
    WESTERN(R.string.editorial_lang_western, R.string.editorial_lang_code_western, 3),
}

private enum class SubScreenType {
    NONE,
    LANGUAGE,
    ARTIST_AZ,
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverViewModel.State,
    actions: DiscoverActions,
    backDispatcher: OnBackPressedDispatcher,
    onRetry: () -> Unit,
    organizedCount: Int = 0,
    organizeProgress: OrganizeProgressState = OrganizeProgressState(),
    playableOnly: Boolean = true,
    onTogglePlayableOnly: (Boolean) -> Unit = {},
    initialShelf: LanguageShelf? = null,
) {
    val colors = EditorialTheme.colors
    var subScreen by rememberSaveable {
        mutableStateOf(if (initialShelf != null) SubScreenType.LANGUAGE else SubScreenType.NONE)
    }
    var activeShelf by rememberSaveable { mutableStateOf(initialShelf ?: LanguageShelf.MANDARIN) }

    // Hoist main scroll state so returning from Artist A–Z or Language Shelf preserves scroll position.
    val mainListState = rememberLazyListState()

    EditorialBackHandler(
        dispatcher = backDispatcher,
        enabled = subScreen != SubScreenType.NONE,
        onBack = { subScreen = SubScreenType.NONE },
    )

    AnimatedContent(
        targetState = subScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "discoverSubScreen",
    ) { currentScreen ->
        when (currentScreen) {
            SubScreenType.LANGUAGE -> {
                val shelfArtists = artistsForShelf(state, activeShelf)
                EditorialLanguageScreen(
                    shelf = activeShelf,
                    artists = shelfArtists,
                    actions = actions,
                    onBack = { subScreen = SubScreenType.NONE },
                )
            }
            SubScreenType.ARTIST_AZ -> {
                ArtistAzIndexScreen(
                    state = state,
                    actions = actions,
                    onBack = { subScreen = SubScreenType.NONE },
                )
            }
            SubScreenType.NONE -> {
                PullToRefreshBox(
                    isRefreshing = state.loading && (state.genres.isNotEmpty() || state.totalArtists > 0),
                    onRefresh = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bg),
                ) {
                    val serverLabel = rememberActiveServerLabel()
                    val chartSummaries = remember(state.chartsVersion) {
                        ChartSource.entries.associateWith { ChartMatches.summary(it) }
                    }

                    LazyColumn(
                        state = mainListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars),
                        contentPadding = PaddingValues(
                            bottom = dimensionResource(R.dimen.global_padding_bottom) + 28.dp,
                        ),
                    ) {
                        // 1. Header + 44dp GlobalSearchPill
                        item(key = "discover_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    EditorialDrawerButton(modifier = Modifier.padding(end = 6.dp))
                                    Column(Modifier.weight(1f)) {
                                        EditorialEyebrow(
                                            text = stringResource(R.string.editorial_discover_subtitle),
                                            serif = true,
                                        )
                                        PageTitle(
                                            text = stringResource(R.string.discover_title),
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    ServerChip(
                                        label = serverLabel,
                                        onClick = actions::openSettings,
                                    )
                                }
                                GlobalSearchPill(
                                    placeholder = stringResource(R.string.editorial_search_discover_placeholder),
                                    modifier = Modifier.padding(top = 14.dp),
                                    onClick = actions::openSearch,
                                )
                            }
                        }

                        // 2. Sync Center Banner (`入库中心`)
                        item(key = "sync_center_banner") {
                            SyncCenterBanner(
                                state = state,
                                chartSummaries = chartSummaries,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                                onOpenImport = { actions.importExternalPlaylist(null) },
                            )
                        }

                        // 3. External Playlist Full Import Card (`外部歌单完整导入`)
                        item(key = "external_playlist_card") {
                            ExternalPlaylistImportCard(
                                imports = state.imports,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                                onPasteNew = { actions.importExternalPlaylist(null) },
                                onOpenPlaylist = { url -> actions.importExternalPlaylist(url) },
                            )
                        }

                        // 4. External Charts (`外部热榜` — 8 charts in 2-column neutral #161412 cards)
                        item(key = "external_charts_header") {
                            ExternalChartsHeader(
                                playableOnly = playableOnly,
                                onTogglePlayable = { onTogglePlayableOnly(!playableOnly) },
                                onExportAll = actions::exportAllMissing,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                            )
                        }

                        editorialGrid(ChartSource.entries.toList()) { source, cellModifier ->
                            ExternalChartNeutralCard(
                                source = source,
                                summary = chartSummaries[source],
                                playableOnly = playableOnly,
                                modifier = cellModifier,
                                onClick = { actions.openExternalChart(source) },
                            )
                        }

                        // 5. Library Gallery (`曲库画廊`)
                        item(key = "gallery_header") {
                            LibraryGalleryHeader(
                                organizedCount = organizedCount,
                                organizeProgress = organizeProgress,
                                onOrganize = actions::organizePlaylists,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp),
                            )
                        }

                        if (state.failed) {
                            item(key = "gallery_error") {
                                ErrorRow(onRetry = onRetry)
                            }
                        } else {
                            // 5a. Languages (`语种唱片架`) — 5 record-store vertical divider cards
                            item(key = "language_shelves") {
                                LanguageShelvesSection(
                                    state = state,
                                    onSelectShelf = { shelf ->
                                        activeShelf = shelf
                                        subScreen = SubScreenType.LANGUAGE
                                    },
                                )
                            }

                            // 5b. Decades (`年代`) — clean horizontal surface3 pills with tnum
                            if (state.decades.isNotEmpty()) {
                                item(key = "decade_pills") {
                                    DecadePillsSection(
                                        decades = state.decades,
                                        onOpenDecade = actions::openDecade,
                                    )
                                }
                            }

                            // 5c. Genres (`流派`) — 2-column compact neutral cards (only clean >= 10 song genres in featured preview)
                            if (state.genres.isNotEmpty()) {
                                val featuredGenres = state.genres.filter { it.songCount >= 10 }.take(12)
                                item(key = "genres_header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.editorial_genre_title),
                                            modifier = Modifier.weight(1f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textSecondary,
                                        )
                                        Text(
                                            text = "${stringResource(R.string.browse_all_genres)} ›",
                                            modifier = Modifier
                                                .clip(EditorialRadii.Pill)
                                                .clickable(onClick = actions::openAllGenres)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.primary,
                                        )
                                    }
                                }
                                if (featuredGenres.isNotEmpty()) {
                                    editorialGrid(featuredGenres) { genre, cellModifier ->
                                        val name = genre.genre.orEmpty()
                                        val sub = if (genre.songCount > 0) {
                                            stringResource(R.string.editorial_genre_song_count, genre.songCount)
                                        } else {
                                            null
                                        }
                                        CategoryTile(
                                            title = name,
                                            color = BrowsePalette.colorFor(name),
                                            subtitle = sub,
                                            height = 62.dp,
                                            modifier = cellModifier,
                                            onClick = { actions.openGenre(genre) },
                                        )
                                    }
                                }
                            }

                            // 6. Artist A–Z Entry Card (`歌手全索引 A–Z`)
                            item(key = "artist_az_entry") {
                                ArtistAzEntryCard(
                                    totalArtists = state.totalArtists,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
                                    onClick = { subScreen = SubScreenType.ARTIST_AZ },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun artistsForShelf(state: DiscoverViewModel.State, shelf: LanguageShelf): List<ArtistID3> =
    when (shelf) {
        LanguageShelf.MANDARIN -> state.languages[Language.CHINESE].orEmpty()
        LanguageShelf.CANTONESE -> state.cantoneseArtists
        LanguageShelf.JAPANESE -> state.languages[Language.JAPANESE].orEmpty()
        LanguageShelf.KOREAN -> state.languages[Language.KOREAN].orEmpty()
        LanguageShelf.WESTERN -> state.languages[Language.WESTERN].orEmpty()
    }

internal fun <T> LazyListScope.editorialGrid(
    items: List<T>,
    columns: Int = 2,
    cell: @Composable (T, Modifier) -> Unit,
) {
    items(items.chunked(columns)) { row ->
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { cell(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SyncCenterBanner(
    state: DiscoverViewModel.State,
    chartSummaries: Map<ChartSource, ChartMatches.Summary?>,
    modifier: Modifier = Modifier,
    onOpenImport: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val importsCount = state.imports.size
    val incompleteListsCount = state.imports.count { !it.binding.complete }

    val nonNullSummaries = chartSummaries.values.filterNotNull()
    val checkedCharts = nonNullSummaries.size
    val chartsAvailable = nonNullSummaries.sumOf { it.available }
    val chartsTotal = nonNullSummaries.sumOf { it.total }
    val chartsMissing = (chartsTotal - chartsAvailable).coerceAtLeast(0)

    val importsMatched = state.imports.sumOf { (it.binding.total - it.binding.missing.size).coerceAtLeast(0) }
    val importsMissing = state.imports.sumOf { it.binding.missing.size }

    val barMatched = (state.dailySongsCount + importsMatched + chartsAvailable)
        .coerceAtLeast(if (checkedCharts == 0 && importsCount == 0 && !state.dailyLoaded) 1 else 0)
    val barPending = state.dailyWishesCount
    val barMissing = importsMissing + chartsMissing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(EditorialRadii.Pill)
                    .background(
                        when {
                            incompleteListsCount > 0 -> colors.terracotta
                            barPending > 0 || barMissing > 0 -> colors.brass
                            else -> colors.moss
                        },
                    ),
            )
            Text(
                text = stringResource(R.string.editorial_sync_center_title),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.editorial_sync_center_import_action),
                modifier = Modifier
                    .clip(EditorialRadii.Pill)
                    .clickable(onClick = onOpenImport)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.brass,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyncMetricColumn(
                label = stringResource(R.string.editorial_sync_col_wishes),
                value = state.dailyWishesCount.toString(),
                valueColor = if (state.dailyWishesCount > 0) colors.brass else colors.textPrimary,
                subLabel = if (state.dailyLoaded || state.dailySongsCount > 0) {
                    stringResource(R.string.editorial_sync_sub_ready, state.dailySongsCount)
                } else {
                    stringResource(R.string.editorial_sync_sub_wishes_idle)
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(colors.hairline),
            )
            SyncMetricColumn(
                label = stringResource(R.string.editorial_sync_col_playlists),
                value = importsMissing.toString(),
                valueColor = when {
                    incompleteListsCount > 0 -> colors.terracotta
                    importsMissing > 0 -> colors.brass
                    importsCount > 0 -> colors.moss
                    else -> colors.textPrimary
                },
                subLabel = when {
                    importsCount == 0 -> stringResource(R.string.editorial_sync_sub_no_lists)
                    incompleteListsCount > 0 -> stringResource(R.string.editorial_sync_sub_pending_lists, importsCount, incompleteListsCount)
                    importsMissing > 0 -> stringResource(R.string.editorial_sync_sub_backfill_lists, importsCount)
                    else -> stringResource(R.string.editorial_sync_sub_synced_lists, importsCount)
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(colors.hairline),
            )
            SyncMetricColumn(
                label = stringResource(R.string.editorial_sync_col_charts),
                value = if (checkedCharts > 0) chartsMissing.toString() else "—",
                valueColor = when {
                    checkedCharts == 0 -> colors.textSecondary
                    chartsMissing == 0 -> colors.moss
                    else -> colors.terracotta
                },
                subLabel = if (checkedCharts > 0) {
                    stringResource(R.string.editorial_sync_sub_charts_matched, checkedCharts)
                } else {
                    stringResource(R.string.editorial_sync_sub_charts_idle)
                },
                modifier = Modifier.weight(1f),
            )
        }

        ThreeSegmentBar(
            matched = barMatched,
            pending = barPending,
            missing = barMissing,
            height = 5.dp,
        )
    }
}

@Composable
private fun SyncMetricColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    subLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            style = LocalTextStyle.current.tnum(),
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = subLabel,
            modifier = Modifier.padding(top = 1.dp),
            fontSize = 11.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.tnum(),
        )
    }
}

@Composable
private fun ExternalPlaylistImportCard(
    imports: List<ImportedPlaylist>,
    modifier: Modifier = Modifier,
    onPasteNew: () -> Unit,
    onOpenPlaylist: (String?) -> Unit,
) {
    val colors = EditorialTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(EditorialRadii.ListCover)
                    .background(colors.surface3)
                    .border(1.dp, colors.hairline, EditorialRadii.ListCover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    tint = colors.brass,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = stringResource(R.string.editorial_import_card_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.editorial_import_card_subtitle),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Prominent Warm-Dark / Stylus Brass CTA pill
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(40.dp)
                .pressable(onClick = onPasteNew)
                .clip(EditorialRadii.Pill)
                .background(colors.primary),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_playlist_add),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.editorial_import_paste_cta),
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimary,
            )
        }

        if (imports.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                color = colors.hairline,
            )
            imports.take(3).forEach { entry ->
                val total = entry.binding.total.coerceAtLeast(entry.playlist.songCount)
                val missingCount = entry.binding.missing.size
                val matchedCount = (total - missingCount).coerceAtLeast(0)
                val incomplete = !entry.binding.complete

                val badgeText = when {
                    incomplete -> stringResource(R.string.editorial_import_badge_incomplete)
                    missingCount > 0 -> stringResource(R.string.editorial_import_badge_pending, missingCount)
                    else -> stringResource(R.string.editorial_import_badge_ready)
                }
                val badgeAccent = when {
                    incomplete -> colors.terracotta
                    missingCount > 0 -> colors.brass
                    else -> colors.moss
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(EditorialRadii.ListCover)
                        .clickable { onOpenPlaylist(entry.binding.url) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (entry.binding.provider == "NETEASE") {
                                    stringResource(R.string.editorial_badge_netease)
                                } else {
                                    stringResource(R.string.editorial_badge_qq)
                                },
                                modifier = Modifier
                                    .clip(EditorialRadii.Badge)
                                    .background(colors.surface3)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = entry.playlist.name.orEmpty(),
                                modifier = Modifier.padding(start = 6.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.editorial_import_item_progress, matchedCount, total),
                            modifier = Modifier.padding(top = 3.dp, bottom = 5.dp),
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            style = LocalTextStyle.current.tnum(),
                        )
                        ThreeSegmentBar(
                            matched = matchedCount,
                            pending = 0,
                            missing = missingCount,
                            height = 4.dp,
                        )
                    }
                    EditorialOutlinePill(
                        text = badgeText,
                        height = 28.dp,
                        accent = badgeAccent,
                        onClick = { onOpenPlaylist(entry.binding.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalChartsHeader(
    playableOnly: Boolean,
    onTogglePlayable: () -> Unit,
    onExportAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.discover_section_external_charts),
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary,
        )
        // `仅看可播` toggle chip — Stylus Brass interactive accent (Moss reserved for status only)
        Row(
            modifier = Modifier
                .padding(end = 8.dp)
                .height(30.dp)
                .pressable(onClick = onTogglePlayable)
                .clip(EditorialRadii.Pill)
                .background(if (playableOnly) colors.brass.copy(alpha = 0.16f) else colors.surface2)
                .border(
                    width = 1.dp,
                    color = if (playableOnly) colors.brass.copy(alpha = 0.55f) else colors.hairline,
                    shape = EditorialRadii.Pill,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(EditorialRadii.Pill)
                    .background(if (playableOnly) colors.brass else colors.textTertiary),
            )
            Text(
                text = stringResource(R.string.editorial_charts_playable_only),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (playableOnly) colors.brass else colors.textSecondary,
            )
        }
        // `导出全部缺歌` brass-outline pill
        EditorialOutlinePill(
            text = stringResource(R.string.editorial_charts_export_all),
            height = 30.dp,
            accent = colors.brass,
            leadingIcon = R.drawable.ic_export,
            onClick = onExportAll,
        )
    }
}

@Composable
private fun ExternalChartNeutralCard(
    source: ChartSource,
    summary: ChartMatches.Summary?,
    playableOnly: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val parts = source.label.split(" · ")
    val chartTitle = parts.lastOrNull().orEmpty()
    val isNetease = source.provider == ChartSource.Provider.NETEASE
    val badgeLabel = if (isNetease) {
        stringResource(R.string.editorial_badge_netease)
    } else {
        stringResource(R.string.editorial_badge_qq)
    }

    val subtitle = when {
        summary == null -> stringResource(R.string.editorial_chart_unmatched_hint)
        playableOnly -> stringResource(R.string.editorial_chart_playable_count, summary.available)
        else -> stringResource(R.string.editorial_chart_total_count, summary.available, summary.total)
    }

    Column(
        modifier = modifier
            .height(118.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Clean platform badge (replaces noisy double-play icon stack)
            Row(
                modifier = Modifier
                    .clip(EditorialRadii.Badge)
                    .background(colors.surface2)
                    .border(1.dp, colors.hairline, EditorialRadii.Badge)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(EditorialRadii.Pill)
                        .background(if (isNetease) colors.terracotta.copy(alpha = 0.85f) else colors.brass),
                )
                Text(
                    text = badgeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }

            if (summary != null) {
                MatchRateRing(
                    matched = summary.available,
                    total = summary.total,
                    size = 40.dp,
                )
            } else {
                UnmatchedChartRing(
                    label = stringResource(R.string.editorial_chart_ring_compare),
                    size = 40.dp,
                )
            }
        }

        Column {
            Text(
                text = chartTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 11.sp,
                color = if (summary != null && summary.available > 0) colors.moss else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

