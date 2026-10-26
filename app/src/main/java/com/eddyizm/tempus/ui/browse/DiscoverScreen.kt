package com.eddyizm.tempus.ui.browse

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.viewmodel.MusicDiscoveryViewModel.Mode
import kotlinx.coroutines.launch

/** What the Discover page can open; implemented by the fragment with navigation calls. */
interface DiscoverActions {
    fun openLibraryChart(chart: LibraryChart)
    fun openExternalChart(source: ChartSource)
    fun openDecade(decade: Int)
    fun openGenre(genre: Genre)
    fun openAllGenres()
    fun openArtist(artist: ArtistID3)
    fun shuffleArtists(artists: List<ArtistID3>)
}

enum class LibraryChart(val title: Int, val hint: Int, val color: Long, val mode: Mode?) {
    FREQUENT(R.string.browse_chart_frequent, R.string.browse_chart_frequent_hint, 0xFF1E9E74, null),
    NEWEST(R.string.browse_chart_newest, R.string.browse_chart_newest_hint, 0xFFE07B2E, null),
    UNHEARD(R.string.browse_chart_unheard, R.string.browse_chart_unheard_hint, 0xFF3D4A9E, Mode.UNHEARD),
    RANDOM(R.string.browse_chart_random, R.string.browse_chart_random_hint, 0xFFC2457A, Mode.RANDOM),
}

private val chartColors = mapOf(
    ChartSource.NETEASE_HOT to 0xFFC8303F, ChartSource.NETEASE_NEW to 0xFF2F6BD8,
    ChartSource.NETEASE_RISING to 0xFFE0662E, ChartSource.NETEASE_ORIGINAL to 0xFF8A4FD8,
    ChartSource.QQ_HOT to 0xFF1E9E74, ChartSource.QQ_NEW to 0xFF0F8FA8,
    ChartSource.QQ_RISING to 0xFFBF8510, ChartSource.QQ_POPULAR to 0xFFC2457A,
    ChartSource.APPLE_CN to 0xFFF0384F, ChartSource.APPLE_JP to 0xFF5E6AD2, ChartSource.APPLE_US to 0xFF2B2D33,
)

private val providerTitles = mapOf(
    ChartSource.Provider.NETEASE to R.string.discover_provider_netease,
    ChartSource.Provider.QQ to R.string.discover_provider_qq,
    ChartSource.Provider.APPLE to R.string.discover_provider_apple,
)

private enum class DiscoverTab(val label: Int) {
    CHARTS(R.string.discover_tab_charts), CATEGORIES(R.string.discover_tab_categories), ARTISTS(R.string.discover_tab_artists)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverViewModel.State,
    actions: DiscoverActions,
    backDispatcher: OnBackPressedDispatcher,
    onRetry: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(DiscoverTab.CHARTS) }
    var language by rememberSaveable { mutableStateOf<Language?>(null) }
    // Back leaves a language page before it leaves the tab.
    val back = remember { object : OnBackPressedCallback(false) { override fun handleOnBackPressed() { language = null } } }
    back.isEnabled = language != null
    DisposableEffect(backDispatcher) {
        backDispatcher.addCallback(back)
        onDispose { back.remove() }
    }

    AnimatedContent(targetState = language, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "discover") { shown ->
        if (shown != null) {
            LanguageScreen(shown, state.languages[shown].orEmpty(), actions) { language = null }
            return@AnimatedContent
        }
        PullToRefreshBox(isRefreshing = state.loading && state.genres.isNotEmpty(), onRefresh = onRetry) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.global_padding_bottom) + 24.dp),
            ) {
                item { PageTitle(stringResource(R.string.discover_title), Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp)) }
                item {
                    TabRow(tab) { tab = it }
                }
                when (tab) {
                    DiscoverTab.CHARTS -> chartsTab(actions)
                    DiscoverTab.CATEGORIES -> categoriesTab(state, actions, onRetry) { language = it }
                    DiscoverTab.ARTISTS -> artistsTab(state, actions, onRetry) { letterIndex ->
                        // Title and tab row come first, then the letter strip.
                        scope.launch { listState.animateScrollToItem(3 + letterIndex) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRow(selected: DiscoverTab, onSelect: (DiscoverTab) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiscoverTab.entries.forEach { tab ->
            val active = tab == selected
            Text(
                text = stringResource(tab.label),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Lays [items] out two per row with the page gutters. */
private fun <T> LazyListScope.grid(items: List<T>, columns: Int = 2, cell: @Composable (T, Modifier) -> Unit) {
    items(items.chunked(columns)) { row ->
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { cell(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun LazyListScope.chartsTab(actions: DiscoverActions) {
    item { SectionHeader(stringResource(R.string.discover_section_library_charts), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) }
    grid(LibraryChart.entries) { chart, modifier ->
        ChartCard(
            title = stringResource(chart.title),
            subtitle = stringResource(chart.hint),
            label = "TEMPUS",
            color = Color(chart.color),
            modifier = modifier,
        ) { actions.openLibraryChart(chart) }
    }
    item {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp)) {
            SectionHeader(stringResource(R.string.discover_section_external_charts))
            Text(stringResource(R.string.discover_external_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // NetEase and QQ Music first: their song and artist names match Chinese libraries best.
    ChartSource.Provider.values().forEach { provider ->
        item {
            Text(
                stringResource(providerTitles.getValue(provider)),
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        grid(ChartSource.values().filter { it.provider == provider }) { source, modifier ->
            val parts = source.label.split(" · ")
            val summary = ChartMatches.summary(source)
            ChartCard(
                title = parts.last(),
                subtitle = if (summary != null) {
                    stringResource(R.string.browse_chart_matched, summary.available, summary.total)
                } else {
                    stringResource(R.string.browse_chart_top100)
                },
                label = parts.first().uppercase(),
                color = Color(chartColors.getValue(source)),
                modifier = modifier,
            ) { actions.openExternalChart(source) }
        }
    }
}

private fun LazyListScope.categoriesTab(
    state: DiscoverViewModel.State,
    actions: DiscoverActions,
    onRetry: () -> Unit,
    onLanguage: (Language) -> Unit,
) {
    if (state.failed) {
        item { ErrorRow(onRetry) }
        return
    }
    val languages = Language.entries.filter { it != Language.OTHER && state.languages[it].orEmpty().isNotEmpty() }
    if (languages.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.browse_section_language), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) }
        grid(languages) { language, modifier ->
            CategoryTile(
                title = stringResource(language.label),
                color = BrowsePalette.colorAt(language.ordinal * 3),
                modifier = modifier,
            ) { onLanguage(language) }
        }
    }
    if (state.decades.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.browse_section_decade), Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.decades) { decade ->
                    CategoryTile(
                        title = stringResource(R.string.browse_decade_label, decade),
                        color = BrowsePalette.colorFor("decade$decade"),
                        modifier = Modifier.width(132.dp),
                        height = 76.dp,
                    ) { actions.openDecade(decade) }
                }
            }
        }
    }
    if (state.genres.isNotEmpty()) {
        item {
            SectionHeader(
                stringResource(R.string.browse_section_genre),
                Modifier.padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 6.dp),
                action = stringResource(R.string.browse_all_genres),
                onAction = actions::openAllGenres,
            )
        }
        grid(state.genres.take(16)) { genre, modifier ->
            CategoryTile(genre.genre.orEmpty(), BrowsePalette.colorFor(genre.genre.orEmpty()), modifier) { actions.openGenre(genre) }
        }
    }
}

private fun LazyListScope.artistsTab(
    state: DiscoverViewModel.State,
    actions: DiscoverActions,
    onRetry: () -> Unit,
    onLetter: (Int) -> Unit,
) {
    if (state.failed) {
        item { ErrorRow(onRetry) }
        return
    }
    if (!state.loading && state.sections.isEmpty()) {
        item { Text(stringResource(R.string.browse_empty_artists), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.sections.size) { index ->
                Text(
                    text = state.sections[index].first.toString(),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onLetter(index) }
                        .padding(top = 7.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    state.sections.forEach { (letter, artists) ->
        item(key = "section$letter") {
            Column {
                Text(
                    text = letter.toString(),
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                artists.forEach { ArtistRow(it) { actions.openArtist(it) } }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistAvatar(artist: ArtistID3, size: Int) {
    GlideImage(
        model = CustomGlideRequest.createUrl(artist.coverArtId, size * 3),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.ic_placeholder_artist),
        failure = placeholder(R.drawable.ic_placeholder_artist),
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun ArtistRow(artist: ArtistID3, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(artist, 46)
        Column(Modifier.padding(start = 14.dp)) {
            Text(artist.name.orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (artist.albumCount > 0) {
                Text(
                    stringResource(R.string.browse_album_count, artist.albumCount),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorRow(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.browse_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.browse_retry),
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onRetry).padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LanguageScreen(language: Language, artists: List<ArtistID3>, actions: DiscoverActions, onBack: () -> Unit) {
    val color = BrowsePalette.colorAt(language.ordinal * 3)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.global_padding_bottom) + 24.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, color.copy(alpha = 0f))))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 12.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.player_collapse), tint = Color.White)
                }
                PageTitle(stringResource(language.label), Modifier.padding(start = 20.dp, top = 24.dp))
                Text(
                    stringResource(R.string.browse_artist_count, artists.size),
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.browse_shuffle),
                    modifier = Modifier
                        .padding(start = 20.dp, top = 16.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { actions.shuffleArtists(artists) }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.browse_language_hint),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        grid(artists, columns = 3) { artist, modifier ->
            Column(
                modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { actions.openArtist(artist) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { ArtistAvatar(artist, 92) }
                Text(
                    artist.name.orEmpty(),
                    modifier = Modifier.padding(top = 6.dp).height(20.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
