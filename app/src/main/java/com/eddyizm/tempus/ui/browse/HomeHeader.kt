package com.eddyizm.tempus.ui.browse

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.Constants
import com.google.common.util.concurrent.ListenableFuture
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
 * built from favourites and a personal radio that starts from a liked song.
 */
@OptIn(UnstableApi::class)
class HomeHeader private constructor(
    private val fragment: Fragment,
    private val browser: Supplier<ListenableFuture<MediaBrowser>?>,
) {
    private var daily by mutableStateOf<List<Child>?>(null)

    private fun loadDaily() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            daily = runCatching { BrowseRepository.dailyMix() }.getOrDefault(emptyList())
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
            val seed = runCatching { BrowseRepository.starredSongs() }.getOrDefault(emptyList()).randomOrNull()
            if (seed == null) {
                Toast.makeText(fragment.requireContext(), R.string.home_roam_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            val similar = BrowseRepository.similarSongs(seed.id, 50).filter { it.id != seed.id }
            play(listOf(seed) + similar)
        }
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
                val songs = daily
                HeroCard(
                    title = stringResource(R.string.home_daily_title),
                    subtitle = when {
                        songs == null -> stringResource(R.string.home_daily_loading)
                        songs.isEmpty() -> stringResource(R.string.home_daily_empty)
                        else -> stringResource(R.string.home_daily_subtitle, songs.size)
                    },
                    colors = listOf(Color(0xFFE13D56), Color(0xFFF08A3C)),
                    bigText = LocalDate.now().dayOfMonth.toString(),
                    modifier = Modifier.weight(1f),
                ) { if (!songs.isNullOrEmpty()) play(songs) }
                HeroCard(
                    title = stringResource(R.string.home_roam_title),
                    subtitle = stringResource(R.string.home_roam_subtitle),
                    colors = listOf(Color(0xFF3D4A9E), Color(0xFF8A4FD8)),
                    icon = R.drawable.ic_instantmix_on,
                    modifier = Modifier.weight(1f),
                ) { startRadio() }
            }
            Spacer(Modifier.padding(top = 8.dp))
        }
    }

    companion object {
        @JvmStatic
        fun bind(view: ComposeView, fragment: Fragment, browser: Supplier<ListenableFuture<MediaBrowser>?>) {
            val header = HomeHeader(fragment, browser)
            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            view.setContent { BrowseTheme { header.Content() } }
            header.loadDaily()
        }
    }
}
