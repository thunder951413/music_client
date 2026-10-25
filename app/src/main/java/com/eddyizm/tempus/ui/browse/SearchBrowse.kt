package com.eddyizm.tempus.ui.browse

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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.util.Constants

/** "Browse all" genre tiles under the search bar, shown until a search runs. */
object SearchBrowse {
    @JvmStatic
    fun bind(view: ComposeView, fragment: Fragment) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            BrowseTheme {
                var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
                LaunchedEffect(Unit) { genres = runCatching { BrowseRepository.genres() }.getOrDefault(emptyList()).take(24) }
                if (genres.isEmpty()) return@BrowseTheme
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    SectionHeader(
                        stringResource(R.string.search_browse_title),
                        Modifier.padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
                        action = stringResource(R.string.discover_title),
                        onAction = { fragment.findNavController().navigate(R.id.discoverFragment) },
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
