package com.eddyizm.tempus.ui.browse

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.repository.AlbumRepository
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.fragment.MusicDiscoveryFragment
import com.eddyizm.tempus.util.Constants
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/** The Discover tab: charts, categories (language, decade, genre) and an A–Z artist index. */
@OptIn(UnstableApi::class)
class DiscoverFragment : Fragment(), DiscoverActions {
    private val model: DiscoverViewModel by viewModels()
    private var browser: ListenableFuture<MediaBrowser>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BrowseTheme {
                    val state by model.state.collectAsState()
                    DiscoverScreen(state, this@DiscoverFragment, requireActivity().onBackPressedDispatcher, onRetry = { model.load(force = true) })
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AlbumRepository().decades.observe(viewLifecycleOwner) { model.setDecades(it) }
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.let {
            it.toggleBottomNavigationBarVisibilityOnOrientationChange()
            it.setBottomSheetVisibility(true)
        }
        browser = MediaBrowser.Builder(
            requireContext(),
            SessionToken(requireContext(), ComponentName(requireContext(), MediaService::class.java)),
        ).buildAsync()
    }

    override fun onStop() {
        MediaBrowser.releaseFuture(browser ?: return)
        browser = null
        super.onStop()
    }

    override fun openLibraryChart(chart: LibraryChart) {
        val mode = chart.mode
        if (mode != null) {
            findNavController().navigate(
                R.id.musicDiscoveryFragment,
                bundleOf(MusicDiscoveryFragment.ARG_MODE to mode.name, MusicDiscoveryFragment.ARG_TITLE to getString(chart.title)),
            )
            return
        }
        val key = if (chart == LibraryChart.FREQUENT) Constants.MEDIA_MOST_PLAYED else Constants.MEDIA_RECENTLY_ADDED
        findNavController().navigate(R.id.songListPageFragment, bundleOf(key to key))
    }

    override fun openExternalChart(source: ChartSource) {
        findNavController().navigate(
            R.id.musicDiscoveryFragment,
            bundleOf(
                MusicDiscoveryFragment.ARG_MODE to "CHART",
                MusicDiscoveryFragment.ARG_SOURCE to source.name,
                MusicDiscoveryFragment.ARG_TITLE to source.label,
            ),
        )
    }

    override fun openDecade(decade: Int) {
        findNavController().navigate(
            R.id.songListPageFragment,
            bundleOf(Constants.MEDIA_BY_YEAR to Constants.MEDIA_BY_YEAR, "year_object" to decade),
        )
    }

    override fun openGenre(genre: Genre) {
        findNavController().navigate(
            R.id.songListPageFragment,
            bundleOf(Constants.MEDIA_BY_GENRE to Constants.MEDIA_BY_GENRE, Constants.GENRE_OBJECT to genre),
        )
    }

    override fun openAllGenres() = findNavController().navigate(R.id.genreCatalogueFragment)

    override fun openArtist(artist: ArtistID3) {
        findNavController().navigate(R.id.artistPageFragment, bundleOf(Constants.ARTIST_OBJECT to artist))
    }

    override fun shuffleArtists(artists: List<ArtistID3>) {
        val ids = artists.mapNotNull { it.id }.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = runCatching { BrowseRepository.songsByArtists(ids, 60) }.getOrDefault(emptyList())
            val future = browser
            if (songs.isEmpty() || future == null) {
                Toast.makeText(requireContext(), R.string.browse_language_shuffle_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            MediaManager.startQueue(future, songs.shuffled(), 0)
            (activity as? MainActivity)?.setBottomSheetInPeek(true)
        }
    }
}
