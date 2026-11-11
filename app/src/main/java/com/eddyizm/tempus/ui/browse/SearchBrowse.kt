package com.eddyizm.tempus.ui.browse

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.ui.fragment.MusicDiscoveryFragment
import com.eddyizm.tempus.util.Constants

/** "Browse all" shortcuts and genre tiles under the search bar, shown until a search runs. */
@OptIn(UnstableApi::class)
object SearchBrowse {
    @JvmStatic
    fun bind(view: ComposeView, fragment: Fragment) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            BrowseTheme {
                var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
                LaunchedEffect(Unit) { genres = runCatching { BrowseRepository.genres() }.getOrDefault(emptyList()).take(24) }
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    SectionHeader(
                        stringResource(R.string.search_browse_title),
                        Modifier.padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                        action = stringResource(R.string.discover_title),
                        onAction = { fragment.findNavController().navigate(R.id.discoverFragment) },
                    )
                    LibraryChart.entries.chunked(2).forEach { row ->
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { chart ->
                                val title = stringResource(chart.title)
                                CategoryTile(title, Color(chart.color), Modifier.weight(1f)) {
                                    val nav = fragment.findNavController()
                                    val mode = chart.mode
                                    if (mode != null) {
                                        nav.navigate(
                                            R.id.musicDiscoveryFragment,
                                            bundleOf(MusicDiscoveryFragment.ARG_MODE to mode.name, MusicDiscoveryFragment.ARG_TITLE to title),
                                        )
                                    } else {
                                        val key = if (chart == LibraryChart.FREQUENT) Constants.ALBUM_MOST_PLAYED else Constants.ALBUM_RECENTLY_ADDED
                                        nav.navigate(R.id.albumListPageFragment, bundleOf(key to key))
                                    }
                                }
                            }
                        }
                    }
                    if (genres.isNotEmpty()) {
                        SectionHeader(
                            stringResource(R.string.browse_section_genre),
                            Modifier.padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                        )
                        genres.chunked(2).forEach { row ->
                            Row(
                                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { genre ->
                                    CategoryTile(genre.genre.orEmpty(), BrowsePalette.colorFor(genre.genre.orEmpty()), Modifier.weight(1f)) {
                                        fragment.findNavController().navigate(
                                            R.id.songListPageFragment,
                                            bundleOf(Constants.MEDIA_BY_GENRE to Constants.MEDIA_BY_GENRE, Constants.GENRE_OBJECT to genre),
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
