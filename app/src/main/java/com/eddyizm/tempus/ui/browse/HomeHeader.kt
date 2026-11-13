package com.eddyizm.tempus.ui.browse

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import com.eddyizm.tempus.weather.WeatherMood
import com.eddyizm.tempus.weather.WeatherRepository
import com.eddyizm.tempus.weather.WeatherSnapshot
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
 * 1. Top bar: conditional Drawer button + Date & Live Weather Eyebrow + Greeting on left; clean circular `我的` button on right (no `账号名 · 切号` chip).
 * 2. 44dp GlobalSearchPill -> SearchFragment.
 * 3. Hero Dual Cards (28dp radius, 60% : 40% split, 188dp height, side-by-side on one row):
 *    - Left (`每日推荐`): Daily calendar rotation + ~30% weather style weighting + atmospheric wash.
 *    - Right (`私人漫游`): Daily calendar rotation + ~30% weather style weighting + grooved vinyl disc visual.
 * 4. `继续收听` (`ContinueListeningSection`) with 2dp accent bottom progress bar.
 * 5. `曲库漫游` (`未听探索 / 我的常听 / 最近入库 / 随机漫游`) in a 2×2 `RecordSleeveCard` grid.
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
    private var showProfileSheet by mutableStateOf(false)
    private var loadJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun refreshWeather() {
        if (!fragment.isAdded) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            runCatching { WeatherRepository.refresh(forceNetwork = false) }
        }
    }

    private fun loadDaily() {
        if (!fragment.isAdded) return
        if (loadJob?.isActive == true) return
        loadJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            runCatching { WeatherRepository.refresh(forceNetwork = false) }
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
        refreshWeather()
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

    @Composable
    private fun Content() {
        val colors = EditorialTheme.colors
        val weatherPref by WeatherRepository.preferenceState
        val weatherLive by WeatherRepository.liveSnapshotState
        val effectiveWeather = remember(weatherPref, weatherLive) {
            WeatherRepository.effectiveSnapshot()
        }
        val activePaletteSpec = remember(weatherPref, weatherLive) {
            WeatherRepository.activePaletteOrNull()
        }

        val greeting = when (LocalTime.now().hour) {
            in 5..10 -> R.string.home_greeting_morning
            in 11..17 -> R.string.home_greeting_afternoon
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
        val baseDateEyebrow = remember(currentDate) {
            currentDate.format(DateTimeFormatter.ofPattern("EEE · MMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        }
        val fullEyebrow = remember(baseDateEyebrow, effectiveWeather, activePaletteSpec) {
            if (effectiveWeather != null) {
                val themeTag = activePaletteSpec?.let { " · ${it.paletteName}" }.orEmpty()
                "$baseDateEyebrow · ${effectiveWeather.city} ${effectiveWeather.conditionLabel} ${effectiveWeather.tempC}°C$themeTag"
            } else {
                baseDateEyebrow
            }
        }

        // Observe sample covers from HomeViewModel for the 2×2 RecordSleeveCard mosaics
        val owner = fragment.viewLifecycleOwner
        val mostPlayedLive = remember(homeViewModel, owner) { homeViewModel?.getMostPlayedAlbums(owner) }
        val recentlyAddedLive = remember(homeViewModel, owner) { homeViewModel?.getMostRecentlyAddedAlbums(owner) }
        val discoverSampleLive = remember(homeViewModel, owner) { homeViewModel?.getDiscoverSongSample(owner) }

        val mostPlayedAlbums by (mostPlayedLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })
        val recentlyAddedAlbums by (recentlyAddedLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })
        val discoverSampleSongs by (discoverSampleLive?.observeAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })

        // Reactively re-order today's songs when calendar day or weather mood changes
        val effectiveToday = remember(daily, currentDate, effectiveWeather?.mood) {
            val raw = daily ?: return@remember null
            if (raw.songs.size <= 1) raw
            else raw.copy(
                songs = WeatherRepository.applyDailyWeatherOrder(
                    songs = raw.songs,
                    date = currentDate,
                    mood = effectiveWeather?.mood,
                ),
            )
        }
        val songs = effectiveToday?.songs
        val roamSubtitle = remember(currentDate, effectiveWeather) {
            WeatherRepository.dailyRoamSubtitle(currentDate, effectiveWeather)
        }

        val hideContinue = remember(homeViewModel) {
            homeViewModel?.checkHomeSectorVisibility(Constants.HOME_SECTOR_CONTINUE_LISTENING) == true
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            WeatherSkyCanopy()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
            ) {
                // 1. Top Bar: Optional Drawer + Date/Weather Eyebrow + Greeting on left; clean circular Profile button on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorialDrawerButton(modifier = Modifier.padding(end = 6.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                            .clickable { showProfileSheet = true },
                    ) {
                        Text(
                            text = fullEyebrow,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 0.8.sp,
                            color = colors.primary.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.tnum(),
                        )
                        PageTitle(
                            text = stringResource(greeting),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    EditorialProfileButton(
                        onClick = { showProfileSheet = true },
                    )
                }

                // 2. 44dp GlobalSearchPill
                GlobalSearchPill(
                    placeholder = stringResource(R.string.editorial_search_home_placeholder),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                    onClick = ::openSearch,
                )

                // 3. Hero Dual Cards (28dp radius, 60% : 40% split, 188dp height — side-by-side on one row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DailyMixHeroCard(
                        today = effectiveToday,
                        weather = effectiveWeather,
                        modifier = Modifier.weight(0.6f),
                        onClick = {
                            if (!songs.isNullOrEmpty()) play(songs) else loadDaily()
                        },
                    )
                    PersonalRoamHeroCard(
                        subtitle = roamSubtitle,
                        modifier = Modifier.weight(0.4f),
                        onClick = ::startRadio,
                    )
                }

                // 4. Continue Listening directly above Library Roam
                if (!hideContinue && resumePoints.isNotEmpty()) {
                    ContinueListeningSection(
                        items = resumePoints,
                        onResume = ::resumeItem,
                    )
                }

                // 5. `曲库漫游` (2×2 RecordSleeveCard grid)
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
        }

        if (showProfileSheet) {
            ProfileCenterSheet(
                fragment = fragment,
                onDismiss = { showProfileSheet = false },
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
            header.refreshWeather()
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
    weather: WeatherSnapshot?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    val songs = today?.songs
    val countNumeral = songs?.size?.takeIf { it > 0 }?.toString() ?: "—"

    val leadCoverIds = remember(songs) {
        songs.orEmpty().mapNotNull { it.coverArtId?.takeIf(String::isNotBlank) }.distinct().take(4)
    }
    val sampledWash = rememberClampedCoverWash(
        coverArtId = leadCoverIds.firstOrNull(),
        isDark = colors.isDark,
        fallback = colors.heroWashFallback,
    )
    val warmWash = remember(sampledWash, colors.heroWashFallback) {
        Color(ColorUtils.blendARGB(sampledWash.toArgb(), colors.heroWashFallback.toArgb(), 0.48f))
    }

    val weatherHint = when (weather?.mood) {
        WeatherMood.RAIN -> "每日焕新 · ${weather.conditionLabel}慢板加权"
        WeatherMood.CLEAR -> "每日焕新 · ${weather.conditionLabel}明快律动"
        WeatherMood.COLD -> "每日焕新 · ${weather.conditionLabel}纯音器乐"
        WeatherMood.CLOUDY -> "每日焕新 · ${weather.conditionLabel}耐听甄选"
        null -> null
    }

    val statusText = when {
        today == null -> stringResource(R.string.editorial_daily_status_preparing)
        today.pending -> stringResource(R.string.editorial_daily_status_preparing)
        songs.isNullOrEmpty() -> stringResource(R.string.editorial_daily_status_empty)
        weatherHint != null -> weatherHint
        today.stale -> stringResource(R.string.editorial_daily_status_cached)
        else -> stringResource(R.string.editorial_daily_status_ready)
    }

    Box(
        modifier = modifier
            .height(188.dp)
            .pressable(onClick = onClick)
            .editorialCard(shape = EditorialRadii.Hero, backgroundColor = colors.surface1),
    ) {
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

        FannedMiniCovers(
            coverArtIds = leadCoverIds,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 15.dp, end = 12.dp),
        )

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 15.dp, end = 68.dp),
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
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.tnum(),
            )
        }

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
    subtitle: String,
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
        val discBase = colors.vinylDisc
        val grooveStroke = colors.vinylGroove
        val centerLabel = colors.primary
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(92.dp)
                .offset(x = 34.dp, y = (-14).dp),
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
                text = subtitle,
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
    lab[2] = lab[2].coerceAtLeast(if (isDark) 3.0 else 4.0)
    lab[0] = if (isDark) lab[0].coerceIn(17.0, 25.0) else lab[0].coerceIn(88.0, 93.0)
    val clampedArgb = ColorUtils.LABToColor(lab[0], lab[1], lab[2])
    return Color(clampedArgb).takeIf { it.toArgb() != 0 } ?: fallback
}
