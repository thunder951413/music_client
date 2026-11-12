package com.eddyizm.tempus.ui.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.eddyizm.tempus.R
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.fragment.MusicDiscoveryFragment
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.viewmodel.HomeViewModel
import com.eddyizm.tempus.viewmodel.MusicDiscoveryViewModel.Mode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Supplier
import kotlin.math.hypot

/**
 * Editorial Warm Dark Home Header (`首页`):
 * 1. Top bar: Serif English date eyebrow + Greeting on left; active Subsonic Server chip on right.
 * 2. 44dp GlobalSearchPill -> SearchFragment.
 * 3. Hero Dual Cards (28dp radius, 58% : 42% split, 188dp height):
 *    - Left (`每日推荐`): Low-chroma cover-derived warm wash, Serif numeral, fanned 3 mini covers,
 *      40dp Stylus Brass circular Play button.
 *    - Right (`私人漫游`): surface1 + hairline border, grooved vinyl disc visual with brass label.
 * 4. Honest `WishStatusCapsule` + Warm-Dark `ModalBottomSheet` for `DailyWish` tracks.
 * 5. Single Home for `曲库漫游` (`未听探索 / 我的常听 / 最近入库 / 随机漫游`) in a 2×2 `RecordSleeveCard` grid.
 */
@OptIn(UnstableApi::class)
class HomeHeader private constructor(
    private val fragment: Fragment,
    private val homeViewModel: HomeViewModel?,
    private val browser: Supplier<ListenableFuture<MediaBrowser>?>,
) {
    private var daily by mutableStateOf<DailyMixRepository.Today?>(null)
    private var currentDate by mutableStateOf(LocalDate.now())
    private var showWishSheet by mutableStateOf(false)
    private var loadJob: Job? = null

    private fun loadDaily() {
        if (!fragment.isAdded) return
        if (loadJob?.isActive == true) return
        loadJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            repeat(8) {
                currentDate = LocalDate.now()
                daily = try {
                    DailyMixRepository.today()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    DailyMixRepository.Today(emptyList(), emptyList(), offline = true)
                }
                val shouldPoll = daily?.pending == true || (daily?.stale == true && daily?.offline != true)
                if (!shouldPoll) return@launch
                delay(4_000)
            }
        }
    }

    private fun onResumeRefresh() {
        val now = LocalDate.now()
        val dateChanged = now != currentDate
        currentDate = now
        val current = daily
        if (dateChanged || current == null || current.pending || current.stale || current.songs.isEmpty()) {
            loadDaily()
        }
    }

    private fun play(songs: List<Child>) {
        val future = browser.get() ?: return
        MediaManager.startQueue(future, songs, 0)
        (fragment.activity as? MainActivity)?.setBottomSheetInPeek(true)
    }

    private fun startRadio() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val queue = runCatching { DailyMixRepository.roamQueue() }.getOrDefault(emptyList())
            if (queue.isEmpty()) {
                Toast.makeText(fragment.requireContext(), R.string.home_roam_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            play(queue)
        }
    }

    private fun openSearch() {
        if (!fragment.isAdded) return
        fragment.findNavController().navigate(
            R.id.searchFragment,
            bundleOf(SearchFragment.ARG_FOCUS to true),
        )
    }

    private fun openSettings() {
        if (!fragment.isAdded) return
        fragment.findNavController().navigate(R.id.settingsFragment)
    }

    private fun openRoamMode(mode: Mode, title: String) {
        if (!fragment.isAdded) return
        fragment.findNavController().navigate(
            R.id.musicDiscoveryFragment,
            bundleOf(
                MusicDiscoveryFragment.ARG_MODE to mode.name,
                MusicDiscoveryFragment.ARG_TITLE to title,
            ),
        )
    }

    private fun openAlbumList(key: String) {
        if (!fragment.isAdded) return
        fragment.findNavController().navigate(
            R.id.albumListPageFragment,
            bundleOf(key to key),
        )
    }

    private fun copyWishes(wishes: List<DailyWish>) {
        if (!fragment.isAdded || wishes.isEmpty()) return
        val context = fragment.requireContext()
        val copyText = wishes.joinToString("\n") { "${it.title} - ${it.artists.joinToString(" / ")}" }
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("daily-wishes", copyText))
        Toast.makeText(
            context,
            context.getString(R.string.discovery_export_copied, wishes.size),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun exportWishes(wishes: List<DailyWish>) {
        if (!fragment.isAdded || wishes.isEmpty()) return
        val context = fragment.requireContext()
        val rows = wishes.mapIndexed { index, w ->
            ChartTrack().apply {
                id = w.id
                title = w.title
                originalTitle = w.original_title
                artists = ArrayList(w.artists)
                artist = w.artists.joinToString(" / ")
                duration = w.duration
                rank = index + 1
                url = if (w.provider == "NETEASE") {
                    "https://music.163.com/song?id=${w.id}"
                } else {
                    "https://y.qq.com/n/ryqq_v2/songDetail/${w.id}"
                }
            }
        }
        MissingExport.show(
            fragment,
            context.getString(R.string.home_daily_wishes_title, wishes.size),
            "daily-wishes-$currentDate",
            rows,
        )
    }

    @kotlin.OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        val colors = EditorialTheme.colors
        val greeting = when (LocalTime.now().hour) {
            in 5..10 -> R.string.home_greeting_morning
            in 11..17 -> R.string.home_greeting_afternoon
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
        val dateEyebrow = remember(currentDate) {
            currentDate.format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        }
        val serverLabel = rememberActiveServerLabel()

        // Observe sample covers from HomeViewModel for the 2×2 RecordSleeveCard mosaics
        val owner = fragment.viewLifecycleOwner
        val mostPlayedLive = remember(homeViewModel, owner) { homeViewModel?.getMostPlayedAlbums(owner) }
        val recentlyAddedLive = remember(homeViewModel, owner) { homeViewModel?.getMostRecentlyAddedAlbums(owner) }
        val discoverSampleLive = remember(homeViewModel, owner) { homeViewModel?.getDiscoverSongSample(owner) }

        val mostPlayedAlbums by (mostPlayedLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })
        val recentlyAddedAlbums by (recentlyAddedLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })
        val discoverSampleSongs by (discoverSampleLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })

        val songs = daily?.songs
        val wishes = daily?.wishes.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            // 1. Top Bar: Date eyebrow + Greeting on left, Server chip on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    EditorialEyebrow(text = dateEyebrow, serif = true)
                    PageTitle(
                        text = stringResource(greeting),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                ServerChip(
                    label = serverLabel,
                    onClick = ::openSettings,
                )
            }

            // 2. 44dp GlobalSearchPill
            GlobalSearchPill(
                placeholder = stringResource(R.string.editorial_search_home_placeholder),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                onClick = ::openSearch,
            )

            // 3. Hero Dual Cards (28dp radius, 58% : 42% split, 188dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DailyMixHeroCard(
                    today = daily,
                    modifier = Modifier.weight(0.58f),
                    onClick = {
                        if (!songs.isNullOrEmpty()) play(songs) else loadDaily()
                    },
                )
                PersonalRoamHeroCard(
                    modifier = Modifier.weight(0.42f),
                    onClick = ::startRadio,
                )
            }

            // 4. Honest Wish Status Capsule
            WishStatusCapsule(
                today = daily,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                onOpenWishes = {
                    if (wishes.isNotEmpty()) {
                        showWishSheet = true
                    }
                },
            )

            // 5. Single Home for `曲库漫游` (2×2 RecordSleeveCard grid)
            SectionHeader(
                title = stringResource(R.string.editorial_home_roam_section),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
            )

            val unheardTitle = stringResource(R.string.browse_chart_unheard)
            val frequentTitle = stringResource(R.string.browse_chart_frequent)
            val newestTitle = stringResource(R.string.browse_chart_newest)
            val randomTitle = stringResource(R.string.browse_chart_random)

            val sampleCovers = remember(discoverSampleSongs) {
                discoverSampleSongs.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct()
            }
            val frequentCovers = remember(mostPlayedAlbums) {
                mostPlayedAlbums.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct()
            }
            val newestCovers = remember(recentlyAddedAlbums) {
                recentlyAddedAlbums.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct()
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecordSleeveCard(
                        title = unheardTitle,
                        subtitle = stringResource(R.string.editorial_roam_unheard_sub),
                        spineColor = colors.moss,
                        coverArtIds = sampleCovers.take(4),
                        monogram = "0",
                        modifier = Modifier.weight(1f),
                        onClick = { openRoamMode(Mode.UNHEARD, unheardTitle) },
                    )
                    RecordSleeveCard(
                        title = frequentTitle,
                        subtitle = if (mostPlayedAlbums.orEmpty().isNotEmpty()) {
                            stringResource(R.string.editorial_roam_frequent_count, mostPlayedAlbums.orEmpty().size)
                        } else {
                            stringResource(R.string.editorial_roam_frequent_sub)
                        },
                        spineColor = colors.brass,
                        coverArtIds = frequentCovers.take(4),
                        monogram = "★",
                        modifier = Modifier.weight(1f),
                        onClick = { openAlbumList(Constants.ALBUM_MOST_PLAYED) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RecordSleeveCard(
                        title = newestTitle,
                        subtitle = if (recentlyAddedAlbums.orEmpty().isNotEmpty()) {
                            stringResource(R.string.editorial_roam_newest_count, recentlyAddedAlbums.orEmpty().size)
                        } else {
                            stringResource(R.string.editorial_roam_newest_sub)
                        },
                        spineColor = BrowsePalette.colorAt(2),
                        coverArtIds = newestCovers.take(4),
                        monogram = "+",
                        modifier = Modifier.weight(1f),
                        onClick = { openAlbumList(Constants.ALBUM_RECENTLY_ADDED) },
                    )
                    RecordSleeveCard(
                        title = randomTitle,
                        subtitle = stringResource(R.string.editorial_roam_random_sub),
                        spineColor = BrowsePalette.colorAt(4),
                        coverArtIds = sampleCovers.asReversed().take(4),
                        monogram = "≈",
                        modifier = Modifier.weight(1f),
                        onClick = { openRoamMode(Mode.RANDOM, randomTitle) },
                    )
                }
            }
        }

        if (showWishSheet && wishes.isNotEmpty()) {
            DailyWishesBottomSheet(
                wishes = wishes,
                onDismiss = { showWishSheet = false },
                onCopy = { copyWishes(wishes) },
                onExport = {
                    showWishSheet = false
                    exportWishes(wishes)
                },
            )
        }
    }

    companion object {
        @JvmStatic
        fun bind(
            view: ComposeView,
            fragment: Fragment,
            browser: Supplier<ListenableFuture<MediaBrowser>?>,
        ) {
            val vm = runCatching {
                ViewModelProvider(fragment.requireActivity()).get(HomeViewModel::class.java)
            }.getOrNull()
            bind(view, fragment, vm, browser)
        }

        @JvmStatic
        fun bind(
            view: ComposeView,
            fragment: Fragment,
            homeViewModel: HomeViewModel?,
            browser: Supplier<ListenableFuture<MediaBrowser>?>,
        ) {
            val header = HomeHeader(fragment, homeViewModel, browser)
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            view.setContent { BrowseTheme { header.Content() } }
            fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    header.onResumeRefresh()
                }
            })
            header.loadDaily()
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DailyMixHeroCard(
    today: DailyMixRepository.Today?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val songs = today?.songs
    val wishesCount = today?.wishes?.size ?: 0
    val countNumeral = (songs?.size?.takeIf { it > 0 } ?: 30).toString()

    val leadCoverIds = remember(songs) {
        songs.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct().take(3)
    }
    val warmWash = rememberClampedCoverWash(
        coverArtId = leadCoverIds.firstOrNull(),
        isDark = colors.isDark,
    )

    val statusText = when {
        today == null || today.pending -> stringResource(R.string.editorial_daily_status_preparing)
        today.offline -> stringResource(R.string.editorial_daily_status_local)
        songs.isNullOrEmpty() -> stringResource(R.string.editorial_daily_status_empty)
        wishesCount > 0 -> stringResource(R.string.editorial_daily_status_ready_wishes, wishesCount)
        else -> stringResource(R.string.editorial_daily_status_ready)
    }

    Box(
        modifier = modifier
            .height(188.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Hero, backgroundColor = colors.surface1),
    ) {
        // Low-chroma radial warm wash (L* 16..26 in dark mode)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(warmWash, colors.surface1),
                        center = Offset(120f, 80f),
                        radius = 460f,
                    ),
                ),
        )

        // Fanned 3 mini album covers at top-right
        FannedMiniCovers(
            coverArtIds = leadCoverIds,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 14.dp),
        )

        // Editorial Serif Big Numeral on top-left
        Text(
            text = countNumeral,
            modifier = Modifier.padding(start = 18.dp, top = 12.dp),
            fontFamily = FontFamily.Serif,
            fontSize = 48.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
            color = colors.textPrimary,
            style = LocalTextStyle.current.tnum(),
        )

        // Title & Status at bottom-left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 16.dp, end = 64.dp),
        ) {
            Text(
                text = stringResource(R.string.home_daily_title),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }

        // 40dp Solid Stylus Brass circular Play button (#CFA46A fill, #1D160B glyph)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(40.dp)
                .clip(EditorialRadii.Pill)
                .background(colors.brass)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.home_daily_title),
                tint = colors.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FannedMiniCovers(
    coverArtIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = EditorialTheme.colors
    val rotations = floatArrayOf(-9f, 0f, 9f)
    val xOffsets = arrayOf((-18).dp, (-9).dp, 0.dp)
    val yOffsets = arrayOf(4.dp, 0.dp, 3.dp)
    Box(modifier = modifier.width(74.dp).height(52.dp), contentAlignment = Alignment.CenterEnd) {
        for (i in 0..2) {
            val artId = coverArtIds.getOrNull(i)
            Box(
                modifier = Modifier
                    .offset(x = xOffsets[i], y = yOffsets[i])
                    .rotate(rotations[i])
                    .size(42.dp)
                    .clip(EditorialRadii.ListCover)
                    .background(colors.surface3)
                    .border(1.dp, colors.hairline, EditorialRadii.ListCover),
                contentAlignment = Alignment.Center,
            ) {
                if (!artId.isNullOrBlank()) {
                    GlideImage(
                        model = CustomGlideRequest.createUrl(artId, 160),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_albums),
                        contentDescription = null,
                        tint = colors.brass.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRoamHeroCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Box(
        modifier = modifier
            .height(188.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Hero, backgroundColor = colors.surface1),
    ) {
        // Peeking grooved dark vinyl disc on top-right with Stylus Brass center label
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(124.dp)
                .offset(x = 26.dp, y = (-20).dp),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val discRadius = size.minDimension * 0.48f
            val discBase = if (colors.isDark) Color(0xFF221E1A) else Color(0xFF2B2621)
            val grooveStroke = Color(0x26FFF0DC)

            drawCircle(color = discBase, radius = discRadius, center = center)
            for (fraction in floatArrayOf(0.88f, 0.74f, 0.60f, 0.46f)) {
                drawCircle(
                    color = grooveStroke,
                    radius = discRadius * fraction,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            // Stylus Brass center label + spindle hole
            drawCircle(color = colors.brass, radius = discRadius * 0.30f, center = center)
            drawCircle(color = discBase, radius = discRadius * 0.07f, center = center)
        }

        EditorialEyebrow(
            text = stringResource(R.string.editorial_roam_eyebrow),
            serif = true,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_roam_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.editorial_roam_subtitle_short),
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

@Composable
private fun WishStatusCapsule(
    today: DailyMixRepository.Today?,
    modifier: Modifier = Modifier,
    onOpenWishes: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val wishes = today?.wishes.orEmpty()
    val songsCount = today?.songs?.size ?: 0
    val hasWishes = today != null && !today.offline && wishes.isNotEmpty()

    val label = when {
        today == null || today.pending -> stringResource(R.string.editorial_wish_syncing)
        today.offline -> stringResource(R.string.editorial_wish_offline)
        wishes.isNotEmpty() -> stringResource(R.string.editorial_wish_pending, wishes.size, songsCount)
        else -> stringResource(R.string.editorial_wish_synced, songsCount)
    }

    val accentColor = when {
        today == null || today.pending -> colors.brass
        today.offline -> colors.textTertiary
        wishes.isNotEmpty() -> colors.brass
        else -> colors.moss
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(EditorialRadii.Pill)
            .background(colors.surface1)
            .border(
                width = 1.dp,
                color = if (hasWishes) colors.brass.copy(alpha = 0.34f) else colors.hairline,
                shape = EditorialRadii.Pill,
            )
            .clickable(enabled = hasWishes, onClick = onOpenWishes)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (hasWishes) accentColor else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.tnum(),
        )
        if (hasWishes) {
            Icon(
                painter = painterResource(R.drawable.ic_navigate_next),
                contentDescription = null,
                tint = colors.brass,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyWishesBottomSheet(
    wishes: List<DailyWish>,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = EditorialRadii.SheetTop,
        containerColor = colors.surface2,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.editorial_wish_sheet_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.editorial_wish_sheet_subtitle, wishes.size),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = colors.textSecondary,
                style = LocalTextStyle.current.tnum(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorialOutlinePill(
                    text = stringResource(R.string.editorial_wish_sheet_copy),
                    height = 36.dp,
                    accent = colors.brass,
                    modifier = Modifier.weight(1f),
                    onClick = onCopy,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .pressable(onClick = onExport)
                        .clip(EditorialRadii.Pill)
                        .background(colors.brass),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.editorial_wish_sheet_export),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                    )
                }
            }

            HorizontalDivider(color = colors.hairline)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(wishes, key = { idx, item -> "$idx:${item.provider}:${item.id}" }) { index, wish ->
                    val platformLabel = if (wish.provider == "NETEASE") {
                        stringResource(R.string.editorial_wish_platform_netease)
                    } else {
                        stringResource(R.string.editorial_wish_platform_qq)
                    }
                    val durationText = wish.duration?.takeIf { it > 0 }?.let {
                        "%d:%02d".format(Locale.US, it / 60, it % 60)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "%02d".format(Locale.US, index + 1),
                            modifier = Modifier.width(28.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary,
                            style = LocalTextStyle.current.tnum(),
                        )
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = wish.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = wish.artists.joinToString(" / "),
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (durationText != null) {
                            Text(
                                text = durationText,
                                modifier = Modifier.padding(end = 8.dp),
                                fontSize = 11.5.sp,
                                color = colors.textTertiary,
                                style = LocalTextStyle.current.tnum(),
                            )
                        }
                        Text(
                            text = platformLabel,
                            modifier = Modifier
                                .clip(EditorialRadii.Badge)
                                .background(colors.surface3)
                                .border(1.dp, colors.hairline, EditorialRadii.Badge)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.brass,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts a warm, low-chroma cover-derived tint clamped to L* 16..26 (dark) or L* 88..94 (light).
 */
@Composable
private fun rememberClampedCoverWash(coverArtId: String?, isDark: Boolean): Color {
    val context = LocalContext.current
    val fallback = if (isDark) Color(0xFF2B2117) else Color(0xFFEBE1D1)
    var wash by remember(coverArtId, isDark) { mutableStateOf(fallback) }

    LaunchedEffect(coverArtId, isDark) {
        if (coverArtId.isNullOrBlank()) {
            wash = fallback
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val url = CustomGlideRequest.createUrl(coverArtId, 96)
                val bitmap: Bitmap = Glide.with(context).asBitmap().load(url).submit(48, 48).get()
                sampleAndClampLab(bitmap, isDark)
            }.getOrNull()
        }
        if (computed != null) {
            wash = computed
        }
    }
    return wash
}

private fun sampleAndClampLab(bitmap: Bitmap, isDark: Boolean): Color {
    val w = bitmap.width.coerceAtLeast(1)
    val h = bitmap.height.coerceAtLeast(1)
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    val stepX = (w / 6).coerceAtLeast(1)
    val stepY = (h / 6).coerceAtLeast(1)
    var y = stepY / 2
    while (y < h) {
        var x = stepX / 2
        while (x < w) {
            val px = bitmap.getPixel(x, y)
            rSum += (px shr 16) and 0xFF
            gSum += (px shr 8) and 0xFF
            bSum += px and 0xFF
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return if (isDark) Color(0xFF2B2117) else Color(0xFFEBE1D1)
    val avgArgb = android.graphics.Color.rgb(
        (rSum / count).toInt(),
        (gSum / count).toInt(),
        (bSum / count).toInt(),
    )
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(avgArgb, lab)
    val chroma = hypot(lab[1], lab[2])
    val maxChroma = if (isDark) 20.0 else 14.0
    if (chroma > maxChroma && chroma > 0.001) {
        val scale = maxChroma / chroma
        lab[1] *= scale
        lab[2] *= scale
    }
    // Warm bias so icy cyan/magenta covers never turn neon
    lab[2] = lab[2].coerceAtLeast(if (isDark) 3.0 else 4.0)
    lab[0] = if (isDark) lab[0].coerceIn(17.0, 25.0) else lab[0].coerceIn(88.0, 93.0)
    val clampedArgb = ColorUtils.LABToColor(lab[0], lab[1], lab[2])
    return Color(clampedArgb).takeIf { it.toArgb() != 0 } ?: if (isDark) Color(0xFF2B2117) else Color(0xFFEBE1D1)
}
