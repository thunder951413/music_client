package com.eddyizm.tempus.ui.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.core.graphics.get
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
import com.eddyizm.tempus.catalog.AudioCatalog
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.podcast.LocalPodcastStore
import com.eddyizm.tempus.repository.AlbumRepository
import com.eddyizm.tempus.repository.QueueRepository
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.fragment.MusicDiscoveryFragment
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.Preferences
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
 * 1. Top bar: conditional Drawer button + Serif English date eyebrow + Greeting on left; active Subsonic Server chip on right.
 * 2. 44dp GlobalSearchPill -> SearchFragment.
 * 3. Hero Dual Cards (28dp radius, 60% : 40% split, 188dp height):
 *    - Left (`每日推荐`): Low-chroma cover-derived warm wash, Serif numeral (`—` when empty), fanned 4 mini covers,
 *      52dp Stylus Brass circular Play button.
 *    - Right (`私人漫游`): surface1 + hairline border, grooved vinyl disc visual with brass label.
 * 4. Honest `WishStatusCapsule` (12dp arc progress indicator, Hub vs Local vs Offline truth) + Warm-Dark `ModalBottomSheet`.
 * 5. `继续收听` (`ContinueListeningSection`) with 2dp Stylus Brass bottom progress bar (positioned directly above `曲库漫游`).
 * 6. Single Home for `曲库漫游` (`未听探索 / 我的常听 / 最近入库 / 随机漫游`) in a 2×2 `RecordSleeveCard` grid.
 */
@OptIn(UnstableApi::class)
class HomeHeader private constructor(
    private val fragment: Fragment,
    private val homeViewModel: HomeViewModel?,
    private val browser: Supplier<ListenableFuture<MediaBrowser>?>,
) {
    private var daily by mutableStateOf<DailyMixRepository.Today?>(DailyMixRepository.peekCachedToday())
    private var resumePoints by mutableStateOf<List<Preferences.ResumePoint>>(Preferences.getContinueListening())
    private var currentDate by mutableStateOf(LocalDate.now())
    private var showWishSheet by mutableStateOf(false)
    private var loadJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val current = daily
                val shouldPoll = current != null && !current.offline && (current.pending || current.stale)
                if (!shouldPoll) return@launch
                delay(4_000)
            }
        }
    }

    private fun refreshContinueListening() {
        if (!fragment.isAdded) return
        resumePoints = Preferences.getContinueListening()
        QueueRepository().syncBookmarksFromServer {
            mainHandler.post {
                if (fragment.isAdded) {
                    resumePoints = Preferences.getContinueListening()
                }
            }
        }
    }

    private fun onResumeRefresh() {
        val now = LocalDate.now()
        val dateChanged = now != currentDate
        currentDate = now
        refreshContinueListening()
        val current = daily
        if (dateChanged || current == null || (!current.offline && (current.pending || current.stale)) || current.songs.isEmpty()) {
            loadDaily()
        }
    }

    private fun play(songs: List<Child>) {
        val future = browser.get() ?: return
        MediaManager.startQueue(future, songs, 0)
        (fragment.activity as? MainActivity)?.setBottomSheetInPeek(true)
    }

    private fun resumeItem(point: Preferences.ResumePoint) {
        val future = browser.get() ?: return
        if (LocalPodcastStore.isLocal(point.id)) {
            if (AudioCatalog.isWebUrl(point.mediaUrl)) {
                playSingleResume(future, point)
            } else {
                LocalPodcastStore.getInstance().findEpisode(point.id) { episode ->
                    if (episode != null) {
                        MediaManager.startPodcast(future, episode, point.position)
                        (fragment.activity as? MainActivity)?.setBottomSheetInPeek(true)
                    }
                }
            }
            return
        }
        val albumId = point.albumId
        if (albumId.isNullOrBlank()) {
            playSingleResume(future, point)
            return
        }
        AlbumRepository().getAlbumTracks(albumId) { tracks ->
            if (tracks.isNullOrEmpty()) {
                playSingleResume(future, point)
                return@getAlbumTracks
            }
            var index = 0
            var position = 0L
            for (i in tracks.indices) {
                if (point.id == tracks[i].id) {
                    index = i
                    position = point.position
                    tracks[i].type = point.type
                    break
                }
            }
            MediaManager.startQueue(future, tracks, index, position)
            (fragment.activity as? MainActivity)?.setBottomSheetInPeek(true)
        }
    }

    private fun playSingleResume(future: ListenableFuture<MediaBrowser>, point: Preferences.ResumePoint) {
        val child = Child(point.id).apply {
            title = point.title
            album = point.album
            artist = point.artist
            albumId = point.albumId
            coverArtId = point.coverArtId
            type = point.type
            path = point.mediaUrl
        }
        MediaManager.startQueue(future, listOf(child), 0, point.position)
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
            Bundle().apply { putBoolean(SearchFragment.ARG_FOCUS, true) },
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
            Bundle().apply {
                putString(MusicDiscoveryFragment.ARG_MODE, mode.name)
                putString(MusicDiscoveryFragment.ARG_TITLE, title)
            },
        )
    }

    private fun openAlbumList(key: String) {
        if (!fragment.isAdded) return
        fragment.findNavController().navigate(
            R.id.albumListPageFragment,
            Bundle().apply { putString(key, key) },
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
        val hideContinue = remember(homeViewModel) {
            homeViewModel?.checkHomeSectorVisibility(Constants.HOME_SECTOR_CONTINUE_LISTENING) == true
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            // 1. Top Bar: Optional Drawer escape hatch + Date eyebrow + Greeting on left, Server chip on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorialDrawerButton(modifier = Modifier.padding(end = 6.dp))
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

            // 3. Hero Dual Cards (28dp radius, 60% : 40% split, 188dp height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DailyMixHeroCard(
                    today = daily,
                    modifier = Modifier.weight(0.6f),
                    onClick = {
                        if (!songs.isNullOrEmpty()) play(songs) else loadDaily()
                    },
                )
                PersonalRoamHeroCard(
                    modifier = Modifier.weight(0.4f),
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

            // 5. Continue Listening (with 2dp Stylus Brass progress bar) directly above Library Roam
            if (!hideContinue && resumePoints.isNotEmpty()) {
                ContinueListeningSection(
                    items = resumePoints,
                    onResume = ::resumeItem,
                )
            }

            // 6. Single Home for `曲库漫游` (2×2 RecordSleeveCard grid)
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
            header.refreshContinueListening()
            header.loadDaily()
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ContinueListeningSection(
    items: List<Preferences.ResumePoint>,
    onResume: (Preferences.ResumePoint) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_title_continue_listening),
            badge = items.size.toString(),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items.take(16), key = { it.id }) { point ->
                ContinueListeningCard(
                    point = point,
                    onClick = { onResume(point) },
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ContinueListeningCard(
    point: Preferences.ResumePoint,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val progress = rememberResumeProgress(point)
    val coverModel = remember(point.coverArtId) {
        val art = point.coverArtId?.takeIf { it.isNotBlank() }
        when {
            art == null -> null
            art.startsWith("http://") || art.startsWith("https://") -> art
            else -> CustomGlideRequest.createUrl(art, 320)
        }
    }

    Column(
        modifier = Modifier
            .width(152.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (coverModel != null) {
                GlideImage(
                    model = coverModel,
                    contentDescription = point.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_albums),
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp),
                )
            }

            // 2dp Stylus Brass bottom progress bar across the cover base
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.surface3),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(colors.primary),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = point.title?.takeIf { it.isNotBlank() } ?: point.id,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = point.album?.takeIf { it.isNotBlank() }
                    ?: point.artist?.takeIf { it.isNotBlank() }
                    ?: "",
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.home_continue_listening_resume_at, formatResumePosition(point.position)),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

@Composable
private fun rememberResumeProgress(point: Preferences.ResumePoint): Float {
    var durationMs by remember(point.id) { mutableLongStateOf(0L) }
    LaunchedEffect(point.id, point.albumId) {
        if (LocalPodcastStore.isLocal(point.id)) {
            LocalPodcastStore.getInstance().findEpisode(point.id) { ep ->
                val sec = ep?.duration ?: 0
                if (sec > 0) durationMs = sec * 1000L
            }
        } else if (!point.albumId.isNullOrBlank()) {
            AlbumRepository().getAlbumTracks(point.albumId) { tracks ->
                val sec = tracks?.firstOrNull { it.id == point.id }?.duration ?: 0
                if (sec > 0) durationMs = sec * 1000L
            }
        }
    }
    return if (durationMs > 0L) {
        (point.position.toFloat() / durationMs.toFloat()).coerceIn(0.04f, 1f)
    } else {
        (point.position.toFloat() / (45f * 60_000f)).coerceIn(0.08f, 0.85f)
    }
}

private fun formatResumePosition(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
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
    val countNumeral = songs?.size?.takeIf { it > 0 }?.toString() ?: "—"

    val leadCoverIds = remember(songs) {
        songs.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct().take(4)
    }
    val warmWash = rememberClampedCoverWash(
        coverArtId = leadCoverIds.firstOrNull(),
        isDark = colors.isDark,
        fallback = colors.heroWashFallback,
    )

    val statusText = when {
        today == null -> stringResource(R.string.editorial_daily_status_preparing)
        today.offline -> stringResource(R.string.editorial_daily_status_local)
        today.pending -> stringResource(R.string.editorial_daily_status_preparing)
        !today.fromHub && !songs.isNullOrEmpty() -> stringResource(R.string.editorial_daily_status_local)
        today.stale && !songs.isNullOrEmpty() -> stringResource(R.string.editorial_daily_status_cached)
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

        // Fanned 4 mini album covers at top-right
        FannedMiniCovers(
            coverArtIds = leadCoverIds,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 15.dp, end = 12.dp),
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
                .padding(start = 18.dp, bottom = 16.dp, end = 76.dp),
        ) {
            Text(
                text = stringResource(R.string.home_daily_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 13.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }

        // 52dp Solid Stylus Brass circular Play button (#CFA46A fill, #1D160B glyph)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(52.dp)
                .clip(EditorialRadii.Pill)
                .background(colors.primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = stringResource(R.string.home_daily_title),
                tint = colors.onPrimary,
                modifier = Modifier.size(24.dp),
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
    val rotations = floatArrayOf(-11f, -4f, 4f, 11f)
    val xOffsets = arrayOf((-30).dp, (-20).dp, (-10).dp, 0.dp)
    val yOffsets = arrayOf(5.dp, 1.dp, 0.dp, 4.dp)
    Box(modifier = modifier.width(82.dp).height(52.dp), contentAlignment = Alignment.CenterEnd) {
        for (i in 0..3) {
            val artId = coverArtIds.getOrNull(i)
            Box(
                modifier = Modifier
                    .offset(x = xOffsets[i], y = yOffsets[i])
                    .rotate(rotations[i])
                    .size(40.dp)
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
                        tint = colors.primary.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp),
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
        val discBase = colors.vinylDisc
        val grooveStroke = colors.vinylGroove
        val centerLabel = colors.primary
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(124.dp)
                .offset(x = 26.dp, y = (-20).dp),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val discRadius = size.minDimension * 0.48f

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
            drawCircle(color = centerLabel, radius = discRadius * 0.30f, center = center)
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
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.editorial_roam_subtitle_short),
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }
    }
}

@Composable
private fun formatSyncAgo(syncedAtMs: Long): String {
    if (syncedAtMs <= 0L) return stringResource(R.string.editorial_sync_just_now)
    val elapsedMin = ((System.currentTimeMillis() - syncedAtMs).coerceAtLeast(0L)) / 60_000L
    return when {
        elapsedMin < 1L -> stringResource(R.string.editorial_sync_just_now)
        elapsedMin < 60L -> stringResource(R.string.editorial_sync_minutes_ago, elapsedMin.toInt())
        else -> stringResource(R.string.editorial_sync_hours_ago, (elapsedMin / 60L).toInt())
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
    val canOpenWishes = wishes.isNotEmpty()
    val syncAgo = formatSyncAgo(today?.syncedAtMs ?: 0L)

    val label = when {
        today == null -> stringResource(R.string.editorial_wish_syncing)
        today.offline -> if (wishes.isNotEmpty()) {
            stringResource(R.string.editorial_wish_offline_wishes, wishes.size)
        } else {
            stringResource(R.string.editorial_wish_offline)
        }
        today.pending -> stringResource(R.string.editorial_wish_syncing)
        !today.fromHub -> if (wishes.isNotEmpty()) {
            stringResource(R.string.editorial_wish_local_wishes, wishes.size, songsCount)
        } else {
            stringResource(R.string.editorial_wish_local_ready, songsCount)
        }
        wishes.isNotEmpty() -> stringResource(
            R.string.editorial_wish_hub_pending,
            wishes.size,
            today.importedToday.coerceAtLeast(0),
            syncAgo,
        )
        else -> stringResource(
            R.string.editorial_wish_hub_synced,
            today.importedToday.coerceAtLeast(0),
            songsCount,
            syncAgo,
        )
    }

    val ringColor = when {
        today == null -> colors.brass
        today.offline -> colors.textTertiary
        today.pending -> colors.brass
        !today.fromHub -> colors.brass.copy(alpha = 0.72f)
        wishes.isNotEmpty() -> colors.brass
        else -> colors.moss
    }

    val arcFraction = when {
        today == null || today.pending -> 0.62f
        today.offline -> 0f
        !today.fromHub -> if (songsCount > 0) 1f else 0.35f
        else -> {
            val total = today.importedToday.coerceAtLeast(0) + wishes.size
            when {
                total > 0 -> (today.importedToday.coerceAtLeast(0).toFloat() / total.toFloat()).coerceIn(0.12f, 1f)
                songsCount > 0 -> 1f
                else -> 0f
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(EditorialRadii.Pill)
            .background(colors.surface1)
            .border(
                width = 1.dp,
                color = if (canOpenWishes) colors.brass.copy(alpha = 0.34f) else colors.hairline,
                shape = EditorialRadii.Pill,
            )
            .clickable(enabled = canOpenWishes, onClick = onOpenWishes)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val trackColor = colors.surface3
        Canvas(modifier = Modifier.size(12.dp)) {
            val strokePx = 1.6.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawCircle(
                color = trackColor,
                radius = (size.minDimension - strokePx) / 2f,
                style = Stroke(width = strokePx),
            )
            if (arcFraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * arcFraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (canOpenWishes) colors.brass else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.tnum(),
        )
        if (canOpenWishes) {
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
                fontWeight = FontWeight.SemiBold,
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
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.editorial_wish_sheet_export),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textTertiary,
                            style = LocalTextStyle.current.tnum(),
                        )
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = wish.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = wish.artists.joinToString(" / "),
                                fontSize = 13.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (durationText != null) {
                            Text(
                                text = durationText,
                                modifier = Modifier.padding(end = 8.dp),
                                fontSize = 11.sp,
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
                            fontSize = 11.sp,
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
private fun rememberClampedCoverWash(
    coverArtId: String?,
    isDark: Boolean,
    fallback: Color,
): Color {
    val context = LocalContext.current
    var wash by remember(coverArtId, isDark, fallback) { mutableStateOf(fallback) }

    LaunchedEffect(coverArtId, isDark, fallback) {
        if (coverArtId.isNullOrBlank()) {
            wash = fallback
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val url = CustomGlideRequest.createUrl(coverArtId, 96)
                val bitmap: Bitmap = Glide.with(context).asBitmap().load(url).submit(48, 48).get()
                sampleAndClampLab(bitmap, isDark, fallback)
            }.getOrNull()
        }
        if (computed != null) {
            wash = computed
        }
    }
    return wash
}

private fun sampleAndClampLab(bitmap: Bitmap, isDark: Boolean, fallback: Color): Color {
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
            val px = bitmap[x, y]
            rSum += (px shr 16) and 0xFF
            gSum += (px shr 8) and 0xFF
            bSum += px and 0xFF
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return fallback
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
    return Color(clampedArgb).takeIf { it.toArgb() != 0 } ?: fallback
}
