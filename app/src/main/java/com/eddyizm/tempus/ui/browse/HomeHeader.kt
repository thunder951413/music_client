package com.eddyizm.tempus.ui.browse

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.function.Supplier

private enum class Quick(@StringRes val title: Int, @DrawableRes val icon: Int, val color: Long) {
    STARRED(R.string.home_quick_starred, R.drawable.ic_favorite, 0xFFE13D56),
    RECENT(R.string.home_quick_recent, R.drawable.ic_history, 0xFF2F6BD8),
    FREQUENT(R.string.home_quick_frequent, R.drawable.ic_graphic_eq, 0xFF1E9E74),
    ADDED(R.string.home_quick_added, R.drawable.ic_add, 0xFFE07B2E),
    PLAYLISTS(R.string.home_quick_playlists, R.drawable.ic_playlist, 0xFF8A4FD8),
    DOWNLOADS(R.string.home_quick_downloads, R.drawable.ic_download, 0xFF4B5563),
}

/**
 * The top of the Music home tab: a greeting, six shortcuts and two feature cards, a daily mix
 * built from listening ([DailyMixRepository]) and a personal radio that starts from a liked song.
 */
@OptIn(UnstableApi::class)
class HomeHeader private constructor(
    private val fragment: Fragment,
    private val browser: Supplier<ListenableFuture<MediaBrowser>?>,
) {
    private var daily by mutableStateOf<DailyMixRepository.Today?>(null)
    private var organized by mutableStateOf(LibraryOrganizer.playlistCount())
    private var currentDate by mutableStateOf(LocalDate.now())
    private var loadJob: Job? = null

    private fun loadDaily() {
        if (!fragment.isAdded) return
        if (loadJob?.isActive == true) return
        loadJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            repeat(8) {
                currentDate = LocalDate.now()
                daily = try { DailyMixRepository.today() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { DailyMixRepository.Today(emptyList(), emptyList(), offline = true) }
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
        organized = LibraryOrganizer.playlistCount()
        val current = daily
        if (dateChanged || current == null || current.pending || current.stale || current.songs.isEmpty()) {
            loadDaily()
        }
    }

    private fun open(quick: Quick) {
        val nav = fragment.findNavController()
        when (quick) {
            Quick.STARRED -> nav.navigate(R.id.songListPageFragment, bundleOf(Constants.MEDIA_STARRED to Constants.MEDIA_STARRED))
            // The server keeps these lists per album (recent, frequent, newest), shared by all devices.
            Quick.RECENT -> nav.navigate(R.id.albumListPageFragment, bundleOf(Constants.ALBUM_RECENTLY_PLAYED to Constants.ALBUM_RECENTLY_PLAYED))
            Quick.FREQUENT -> nav.navigate(R.id.albumListPageFragment, bundleOf(Constants.ALBUM_MOST_PLAYED to Constants.ALBUM_MOST_PLAYED))
            Quick.ADDED -> nav.navigate(R.id.albumListPageFragment, bundleOf(Constants.ALBUM_RECENTLY_ADDED to Constants.ALBUM_RECENTLY_ADDED))
            Quick.PLAYLISTS -> nav.navigate(R.id.playlistCatalogueFragment, bundleOf(Constants.PLAYLIST_ALL to Constants.PLAYLIST_ALL))
            Quick.DOWNLOADS -> nav.navigate(R.id.downloadFragment)
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

    private fun showWishesDialog(wishes: List<DailyWish>) {
        if (wishes.isEmpty() || !fragment.isAdded) return
        val context = fragment.requireContext()
        val rows = wishes.mapIndexed { i, w ->
            ChartTrack().apply {
                id = w.id
                title = w.title
                originalTitle = w.original_title
                artists = ArrayList(w.artists)
                artist = w.artists.joinToString(" / ")
                duration = w.duration
                rank = i + 1
                url = if (w.provider == "NETEASE") "https://music.163.com/song?id=${w.id}" else "https://y.qq.com/n/ryqq_v2/songDetail/${w.id}"
            }
        }
        val lines = wishes.mapIndexed { index, wish ->
            val platform = if (wish.provider == "NETEASE") "网易云" else "QQ"
            "${index + 1}. ${wish.title} — ${wish.artists.joinToString(" / ")} ($platform)"
        }.joinToString("\n")
        val copyText = wishes.joinToString("\n") { "${it.title} - ${it.artists.joinToString(" / ")}" }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.home_daily_wishes_title, wishes.size))
            .setMessage(lines)
            .setPositiveButton(R.string.discovery_export_copy) { _, _ ->
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("daily-wishes", copyText))
                Toast.makeText(context, context.getString(R.string.discovery_export_copied, wishes.size), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.discovery_export_share) { _, _ ->
                MissingExport.show(
                    fragment,
                    context.getString(R.string.home_daily_wishes_title, wishes.size),
                    "daily-wishes-$currentDate",
                    rows,
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Composable
    private fun Content() {
        val greeting = when (LocalTime.now().hour) {
            in 5..10 -> R.string.home_greeting_morning
            in 11..17 -> R.string.home_greeting_afternoon
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
            PageTitle(stringResource(greeting), Modifier.padding(horizontal = 20.dp))
            Quick.entries.chunked(2).forEach { row ->
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { quick ->
                        QuickTile(stringResource(quick.title), Color(quick.color), quick.icon, Modifier.weight(1f)) { open(quick) }
                    }
                }
            }
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val songs = daily?.songs
                val wishItems = daily?.wishes.orEmpty()
                val wishes = wishItems.size
                HeroCard(
                    title = stringResource(R.string.home_daily_title),
                    subtitle = when {
                        songs == null -> stringResource(R.string.home_daily_loading)
                        daily?.pending == true -> stringResource(R.string.home_daily_preparing)
                        daily?.stale == true && songs.isNotEmpty() -> stringResource(R.string.home_daily_cached, songs.size)
                        songs.isEmpty() -> stringResource(R.string.home_daily_empty)
                        wishes > 0 -> stringResource(R.string.home_daily_subtitle_wishes, songs.size, wishes)
                        else -> stringResource(R.string.home_daily_subtitle, songs.size)
                    },
                    colors = listOf(Color(0xFFE13D56), Color(0xFFF08A3C)),
                    bigText = currentDate.dayOfMonth.toString(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (!songs.isNullOrEmpty()) play(songs)
                    else loadDaily()
                }
                HeroCard(
                    title = stringResource(R.string.home_roam_title),
                    subtitle = stringResource(R.string.home_roam_subtitle),
                    colors = listOf(Color(0xFF3D4A9E), Color(0xFF8A4FD8)),
                    icon = R.drawable.ic_instantmix_on,
                    modifier = Modifier.weight(1f),
                ) { startRadio() }
            }
            val currentWishes = daily?.wishes.orEmpty()
            if (currentWishes.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_daily_wishes_chip, currentWishes.size),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { showWishesDialog(currentWishes) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OrganizeRow(
                title = stringResource(R.string.library_mix_title),
                subtitle = if (organized > 0) {
                    stringResource(R.string.library_mix_subtitle_done, organized)
                } else {
                    stringResource(R.string.library_mix_subtitle)
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            ) { LibraryOrganizer.confirmAndRun(fragment) { organized = LibraryOrganizer.playlistCount() } }
            Spacer(Modifier.padding(top = 8.dp))
        }
    }

    companion object {
        @JvmStatic
        fun bind(view: ComposeView, fragment: Fragment, browser: Supplier<ListenableFuture<MediaBrowser>?>) {
            val header = HomeHeader(fragment, browser)
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
