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


@Composable
internal fun LibraryGalleryHeader(
    organizedCount: Int,
    organizeProgress: OrganizeProgressState,
    onOrganize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    val pillText = when {
        organizeProgress.running && organizeProgress.phase == 0 ->
            stringResource(R.string.editorial_organize_pill_scanning, organizeProgress.done)
        organizeProgress.running && organizeProgress.total > 0 -> {
            val pct = (organizeProgress.done * 100 / organizeProgress.total.coerceAtLeast(1)).coerceIn(0, 100)
            stringResource(
                R.string.editorial_organize_pill_writing,
                organizeProgress.done.coerceAtMost(organizeProgress.total),
                organizeProgress.total,
                pct,
            )
        }
        organizedCount > 0 -> stringResource(R.string.editorial_organize_pill_synced, organizedCount)
        else -> stringResource(R.string.editorial_organize_pill_idle)
    }

    val fraction = when {
        !organizeProgress.running -> null
        organizeProgress.phase == 0 -> 0.18f
        organizeProgress.total > 0 -> (organizeProgress.done.toFloat() / organizeProgress.total.toFloat()).coerceIn(0.08f, 1f)
        else -> 0.2f
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.editorial_gallery_title),
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = colors.textPrimary,
        )
        EditorialOutlinePill(
            text = pillText,
            height = 32.dp,
            accent = colors.brass,
            active = organizeProgress.running,
            progress = fraction,
            leadingIcon = R.drawable.ic_playlist,
            onClick = onOrganize,
        )
    }
}

@Composable
internal fun LanguageShelvesSection(
    state: DiscoverViewModel.State,
    onSelectShelf: (LanguageShelf) -> Unit,
) {
    val colors = EditorialTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.editorial_shelf_language_title),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(LanguageShelf.entries) { shelf ->
                val count = artistsForShelf(state, shelf).size
                val tabColor = BrowsePalette.colorAt(shelf.spineIndex)
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .height(94.dp)
                        .pressable { onSelectShelf(shelf) }
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 3dp top record-store divider tab
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(tabColor.copy(alpha = 0.88f)),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(shelf.codeRes),
                            fontFamily = FontFamily.Serif,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp,
                            color = colors.textTertiary,
                        )
                        Column {
                            Text(
                                text = stringResource(shelf.labelRes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = stringResource(R.string.browse_artist_count, count),
                                modifier = Modifier.padding(top = 1.dp),
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                style = LocalTextStyle.current.tnum(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DecadePillsSection(
    decades: List<Int>,
    onOpenDecade: (Int) -> Unit,
) {
    val colors = EditorialTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.editorial_decade_title),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(decades) { decade ->
                val decadeText = if (decade in 1950..1990) "${decade % 100}s" else "${decade}s"
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .pressable { onOpenDecade(decade) }
                        .clip(EditorialRadii.Pill)
                        .background(colors.surface3)
                        .border(1.dp, colors.hairline, EditorialRadii.Pill)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = decadeText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        style = LocalTextStyle.current.tnum(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArtistAzEntryCard(
    totalArtists: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(colors.brass),
        )
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(44.dp)
                .clip(EditorialRadii.ListCover)
                .background(colors.surface3)
                .border(1.dp, colors.hairline, EditorialRadii.ListCover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_artists),
                contentDescription = null,
                tint = colors.brass,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.editorial_artist_az_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.editorial_artist_az_subtitle, totalArtists),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
        Text(
            text = stringResource(R.string.editorial_artist_az_preview),
            modifier = Modifier.padding(end = 16.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.brass,
            style = LocalTextStyle.current.tnum(),
        )
    }
}

/** Overlaid full-screen Artist A–Z Index sub-screen with search filter & vertical scrub rail. */
@Composable
internal fun ArtistAzIndexScreen(
    state: DiscoverViewModel.State,
    actions: DiscoverActions,
    onBack: () -> Unit,
) {
    val colors = EditorialTheme.colors
    var filterQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredSections = remember(state.sections, filterQuery) {
        val q = filterQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.sections
        } else {
            state.sections.mapNotNull { (letter, group) ->
                val matches = group.filter { artist ->
                    artist.name?.lowercase()?.contains(q) == true || letter.lowercaseChar().toString() == q
                }
                if (matches.isEmpty()) null else letter to matches
            }
        }
    }

    val sectionOffsets = remember(filteredSections) {
        val offsets = IntArray(filteredSections.size)
        var cursor = 0
        filteredSections.forEachIndexed { index, (_, artists) ->
            offsets[index] = cursor
            cursor += 1 + artists.size
        }
        offsets
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.player_collapse),
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = stringResource(R.string.editorial_artist_az_title),
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = state.totalArtists.toString(),
                modifier = Modifier
                    .clip(EditorialRadii.Pill)
                    .background(colors.surface2)
                    .border(1.dp, colors.hairline, EditorialRadii.Pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.brass,
                style = LocalTextStyle.current.tnum(),
            )
        }

        // Live 44dp filter pill
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .fillMaxWidth()
                .height(44.dp)
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (filterQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editorial_search_artists_placeholder),
                        fontSize = 15.sp,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.brass),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (filterQuery.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.editorial_ext_clear),
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { filterQuery = "" },
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filteredSections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.sections.isEmpty()) {
                            stringResource(R.string.browse_empty_artists)
                        } else {
                            stringResource(R.string.editorial_artist_az_empty_filter)
                        },
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        end = 30.dp,
                        bottom = dimensionResource(R.dimen.global_padding_bottom) + 24.dp,
                    ),
                ) {
                    filteredSections.forEach { (letter, artists) ->
                        item(key = "az_header_$letter") {
                            Text(
                                text = letter.toString(),
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.brass,
                            )
                        }
                        items(artists, key = { artist -> "az_artist_${letter}_${artist.id ?: artist.name}" }) { artist ->
                            ArtistRow(artist = artist) { actions.openArtist(artist) }
                        }
                    }
                }

                // Vertical A–Z Scrub Rail on right edge
                var railHeightPx by remember { mutableIntStateOf(1) }
                fun scrubTo(yPx: Float) {
                    if (filteredSections.isEmpty() || railHeightPx <= 0) return
                    val idx = ((yPx / railHeightPx.toFloat()) * filteredSections.size)
                        .toInt()
                        .coerceIn(0, filteredSections.lastIndex)
                    val target = sectionOffsets.getOrElse(idx) { 0 }
                    scope.launch { listState.scrollToItem(target) }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            end = 6.dp,
                            top = 6.dp,
                            bottom = dimensionResource(R.dimen.global_padding_bottom) + 10.dp,
                        )
                        .width(24.dp)
                        .clip(EditorialRadii.Pill)
                        .background(colors.surface2.copy(alpha = 0.88f))
                        .border(1.dp, colors.hairline, EditorialRadii.Pill)
                        .padding(vertical = 4.dp)
                        .onSizeChanged { railHeightPx = it.height.coerceAtLeast(1) }
                        .pointerInput(filteredSections) {
                            detectTapGestures { offset -> scrubTo(offset.y) }
                        }
                        .pointerInput(filteredSections) {
                            detectVerticalDragGestures { change, _ ->
                                change.consume()
                                scrubTo(change.position.y)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    filteredSections.forEach { (letter, _) ->
                        Text(
                            text = letter.toString(),
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = colors.brass,
                            style = LocalTextStyle.current.tnum(),
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistCoverBox(artist: ArtistID3, sizeDp: Int, circular: Boolean = false) {
    val colors = EditorialTheme.colors
    val shape = if (circular) EditorialRadii.Pill else EditorialRadii.ListCover
    val modelUrl = artist.coverArtId?.takeIf { it.isNotBlank() }?.let {
        CustomGlideRequest.createUrl(it, sizeDp * 3)
    }
    GlideImage(
        model = modelUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = placeholder(R.drawable.ic_placeholder_artist),
        failure = placeholder(R.drawable.ic_placeholder_artist),
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, colors.hairline, shape),
    )
}

@Composable
private fun ArtistRow(artist: ArtistID3, onClick: () -> Unit) {
    val colors = EditorialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistCoverBox(artist = artist, sizeDp = 48, circular = false)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = artist.name.orEmpty(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textPrimary,
            )
            if (artist.albumCount > 0) {
                Text(
                    text = stringResource(R.string.browse_album_count, artist.albumCount),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    style = LocalTextStyle.current.tnum(),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_navigate_next),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun ErrorRow(onRetry: () -> Unit) {
    val colors = EditorialTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.browse_error), color = colors.textSecondary, fontSize = 15.sp)
        EditorialOutlinePill(
            text = stringResource(R.string.browse_retry),
            modifier = Modifier.padding(top = 12.dp),
            accent = colors.brass,
            onClick = onRetry,
        )
    }
}

@Composable
internal fun EditorialLanguageScreen(
    shelf: LanguageShelf,
    artists: List<ArtistID3>,
    actions: DiscoverActions,
    onBack: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val accent = BrowsePalette.colorAt(shelf.spineIndex)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(
            bottom = dimensionResource(R.dimen.global_padding_bottom) + 24.dp,
        ),
    ) {
        item(key = "lang_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.player_collapse),
                        tint = colors.textPrimary,
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
                        .padding(20.dp),
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(3.dp)
                            .clip(EditorialRadii.Pill)
                            .background(accent),
                    )
                    EditorialEyebrow(
                        text = stringResource(shelf.codeRes),
                        serif = true,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    PageTitle(
                        text = stringResource(shelf.labelRes),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.browse_artist_count, artists.size),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        style = LocalTextStyle.current.tnum(),
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .height(40.dp)
                            .pressable(enabled = artists.isNotEmpty()) { actions.shuffleArtists(artists) }
                            .clip(EditorialRadii.Pill)
                            .background(if (artists.isNotEmpty()) colors.primary else colors.surface3)
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_shuffle),
                            contentDescription = null,
                            tint = if (artists.isNotEmpty()) colors.onPrimary else colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.browse_shuffle),
                            color = if (artists.isNotEmpty()) colors.onPrimary else colors.textTertiary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.browse_language_hint),
                        modifier = Modifier.padding(top = 10.dp),
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        if (artists.isEmpty()) {
            item(key = "lang_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.editorial_lang_empty),
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            editorialGrid(artists, columns = 3) { artist, cellModifier ->
                Column(
                    modifier = cellModifier
                        .clip(EditorialRadii.Card)
                        .clickable { actions.openArtist(artist) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArtistCoverBox(artist = artist, sizeDp = 88, circular = true)
                    }
                    Text(
                        text = artist.name.orEmpty(),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .height(20.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
