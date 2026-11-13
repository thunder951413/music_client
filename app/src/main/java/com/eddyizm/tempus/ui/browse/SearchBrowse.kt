package com.eddyizm.tempus.ui.browse

import android.os.Bundle
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.util.Constants

/**
 * Editorial Warm Dark "Browse" shelves under the Search bar before a query runs:
 * Standalone External Playlist Match & Missing-Track Export entry + 2-column Genre sleeve cards.
 */
@OptIn(UnstableApi::class)
object SearchBrowse {

    @JvmStatic
    fun bind(view: ComposeView, fragment: Fragment) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            BrowseTheme {
                val colors = EditorialTheme.colors
                var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
                LaunchedEffect(Unit) {
                    genres = runCatching { BrowseRepository.genres() }.getOrDefault(emptyList()).take(24)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                ) {
                    CategoryTile(
                        title = stringResource(R.string.editorial_profile_playlist_export),
                        subtitle = stringResource(R.string.editorial_profile_playlist_export_sub),
                        color = colors.brass,
                        height = 66.dp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        if (fragment.isAdded) {
                            fragment.findNavController().navigate(R.id.externalPlaylistFragment)
                        }
                    }

                    if (genres.isNotEmpty()) {
                        SectionHeader(
                            title = stringResource(R.string.editorial_genre_title),
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                            action = stringResource(R.string.browse_all_genres),
                            onAction = {
                                if (fragment.isAdded) {
                                    fragment.findNavController().navigate(R.id.genreCatalogueFragment)
                                }
                            },
                        )
                        genres.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { genre ->
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
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (fragment.isAdded) {
                                            fragment.findNavController().navigate(
                                                R.id.songListPageFragment,
                                                Bundle().apply {
                                                    putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE)
                                                    putParcelable(Constants.GENRE_OBJECT, genre)
                                                },
                                            )
                                        }
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
