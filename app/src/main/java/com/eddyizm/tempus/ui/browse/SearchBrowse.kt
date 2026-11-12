package com.eddyizm.tempus.ui.browse

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.util.Constants

/**
 * Editorial Warm Dark "Browse" shelves under the Search bar before a query runs:
 * Language record-store shelves + 2-column Genre sleeve cards (zero rainbow tiles, zero duplicate LibraryChart).
 */
@OptIn(UnstableApi::class)
object SearchBrowse {
    private fun navigateToDiscover(fragment: Fragment, shelf: LanguageShelf? = null) {
        if (!fragment.isAdded) return
        val navController = fragment.findNavController()
        val args = shelf?.let {
            Bundle().apply { putString(DiscoverFragment.ARG_INITIAL_SHELF, it.name) }
        }
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(navController.graph.startDestinationId, false)
            .build()
        navController.navigate(R.id.discoverFragment, args, navOptions)
    }

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
                    SectionHeader(
                        title = stringResource(R.string.editorial_shelf_language_title),
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        action = stringResource(R.string.discover_title),
                        onAction = { navigateToDiscover(fragment, null) },
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(LanguageShelf.entries) { shelf ->
                            val tabColor = BrowsePalette.colorAt(shelf.spineIndex)
                            Column(
                                modifier = Modifier
                                    .width(104.dp)
                                    .height(80.dp)
                                    .pressable { navigateToDiscover(fragment, shelf) }
                                    .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
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
                                        color = colors.textSecondary,
                                    )
                                    Text(
                                        text = stringResource(shelf.labelRes),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    if (genres.isNotEmpty()) {
                        SectionHeader(
                            title = stringResource(R.string.editorial_genre_title),
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                            action = stringResource(R.string.browse_all_genres),
                            onAction = { fragment.findNavController().navigate(R.id.genreCatalogueFragment) },
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
                                        fragment.findNavController().navigate(
                                            R.id.songListPageFragment,
                                            Bundle().apply {
                                                putString(Constants.MEDIA_BY_GENRE, Constants.MEDIA_BY_GENRE)
                                                putParcelable(Constants.GENRE_OBJECT, genre)
                                            },
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
