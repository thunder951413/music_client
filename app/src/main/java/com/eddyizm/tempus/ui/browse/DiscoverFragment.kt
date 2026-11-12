package com.eddyizm.tempus.ui.browse

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.eddyizm.tempus.discovery.AllChartsMatcher
import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.repository.AlbumRepository
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.fragment.MusicDiscoveryFragment
import com.eddyizm.tempus.ui.fragment.SearchFragment
import com.eddyizm.tempus.util.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Editorial Warm Dark Discover & Sync Center (`发现 · 入库中心`):
 * Single vertical scroll with Sync Center, External Playlist Full Import, 8 External Charts,
 * Library Gallery (`语种唱片架 / 年代 / 流派` + inline 32dp `整理歌单` pill), and overlaid Artist A–Z Index.
 */
@OptIn(UnstableApi::class)
class DiscoverFragment : Fragment(), DiscoverActions {
    private val model: DiscoverViewModel by viewModels()
    private var browser: ListenableFuture<MediaBrowser>? = null
    private var organized by mutableIntStateOf(LibraryOrganizer.playlistCount())
    private var organizeProgress by mutableStateOf(OrganizeProgressState())
    private var organizeJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val initialShelf = arguments?.getString(ARG_INITIAL_SHELF)?.let { raw ->
            runCatching { LanguageShelf.valueOf(raw) }.getOrNull()
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BrowseTheme {
                    val state by model.state.collectAsState()
                    DiscoverScreen(
                        state = state,
                        actions = this@DiscoverFragment,
                        backDispatcher = requireActivity().onBackPressedDispatcher,
                        onRetry = { model.load(force = true) },
                        organizedCount = organized,
                        organizeProgress = organizeProgress,
                        initialShelf = initialShelf,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        organized = LibraryOrganizer.playlistCount()
        model.refreshSyncCenter()
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

    override fun openSearch() {
        findNavController().navigate(
            R.id.searchFragment,
            bundleOf(SearchFragment.ARG_FOCUS to true),
        )
    }

    override fun openSettings() {
        findNavController().navigate(R.id.settingsFragment)
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

    override fun importExternalPlaylist(initialUrl: String?) {
        val args = if (!initialUrl.isNullOrBlank()) {
            bundleOf(ExternalPlaylistFragment.ARG_INITIAL_URL to initialUrl)
        } else {
            null
        }
        findNavController().navigate(R.id.externalPlaylistFragment, args)
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

    /**
     * Matches every public chart against the library, then exports all missing songs at once,
     * listing a song found on several charts only once.
     */
    override fun exportAllMissing() {
        val context = requireContext()
        val label = TextView(context).apply { setText(R.string.discover_export_fetching_start) }
        val bar = LinearProgressIndicator(context).apply { isIndeterminate = true }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = pad / 2
            })
        }
        val matcher = AllChartsMatcher()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.discover_export_all_title)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        val job = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                matcher.run(ChartSource.entries) { progress ->
                    label.post {
                        label.text = if (progress.phase == 0) {
                            getString(R.string.discover_export_fetching, progress.done + 1, progress.total)
                        } else {
                            getString(R.string.discover_export_matching, progress.done, progress.total)
                        }
                        if (progress.phase == 1 && progress.total > 0) {
                            if (bar.isIndeterminate) {
                                bar.visibility = View.INVISIBLE
                                bar.isIndeterminate = false
                                bar.visibility = View.VISIBLE
                            }
                            bar.setProgressCompat(progress.done * 100 / progress.total, true)
                        }
                    }
                }
            }
            if (!isActive) return@launch
            dialog.dismiss()
            model.chartsMatched()
            val done = result.getOrNull()
            when {
                done == null -> Toast.makeText(context, R.string.discovery_chart_failed, Toast.LENGTH_LONG).show()
                done.missing.isEmpty() -> Toast.makeText(context, R.string.discover_export_none_missing, Toast.LENGTH_LONG).show()
                else -> {
                    if (done.failedCharts > 0 || done.failedSearches > 0) {
                        Toast.makeText(context, getString(R.string.discover_export_partial, done.failedCharts, done.failedSearches), Toast.LENGTH_LONG).show()
                    }
                    MissingExport.show(
                        this@DiscoverFragment,
                        getString(R.string.discover_export_all_result, done.missing.size, done.charts),
                        getString(R.string.discover_export_all_file),
                        done.missing,
                    )
                }
            }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            matcher.cancel()
            job.cancel()
            dialog.dismiss()
        }
    }

    override fun organizePlaylists() {
        val activeJob = organizeJob
        if (activeJob?.isActive == true) {
            activeJob.cancel()
            organizeProgress = OrganizeProgressState(running = false)
            return
        }
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.library_mix_title)
            .setMessage(R.string.library_mix_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.library_mix_start) { _, _ ->
                startInlineOrganize()
            }
            .show()
    }

    private fun startInlineOrganize() {
        val context = requireContext().applicationContext
        organizeProgress = OrganizeProgressState(running = true, phase = 0, done = 0, total = 0)
        organizeJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                LibraryOrganizer.run(context) { phase, done, total ->
                    view?.post {
                        if (organizeJob?.isActive == true) {
                            organizeProgress = OrganizeProgressState(
                                running = true,
                                phase = phase,
                                done = if (phase == 1) done + 1 else done,
                                total = total,
                            )
                        }
                    }
                }
            }
            if (!isActive) {
                organizeProgress = OrganizeProgressState(running = false)
                return@launch
            }
            organizeProgress = OrganizeProgressState(running = false)
            val done = result.getOrNull()
            when {
                done == null -> Toast.makeText(requireContext(), R.string.library_mix_failed, Toast.LENGTH_LONG).show()
                done.playlists == 0 -> Toast.makeText(requireContext(), R.string.library_mix_none, Toast.LENGTH_LONG).show()
                else -> {
                    organized = LibraryOrganizer.playlistCount()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.library_mix_done, done.playlists, done.songs),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    override fun shuffleArtists(artists: List<ArtistID3>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val songs = runCatching { BrowseRepository.songsByArtists(artists, 60) }.getOrDefault(emptyList())
            val future = browser
            if (songs.isEmpty() || future == null) {
                Toast.makeText(requireContext(), R.string.browse_language_shuffle_empty, Toast.LENGTH_LONG).show()
                return@launch
            }
            MediaManager.startQueue(future, songs.shuffled(), 0)
            (activity as? MainActivity)?.setBottomSheetInPeek(true)
        }
    }

    companion object {
        const val ARG_INITIAL_SHELF = "initial_shelf"
    }
}
