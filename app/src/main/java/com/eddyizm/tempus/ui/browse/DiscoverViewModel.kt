package com.eddyizm.tempus.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoverViewModel : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val genres: List<Genre> = emptyList(),
        val decades: List<Int> = emptyList(),
        val languages: Map<Language, List<ArtistID3>> = emptyMap(),
        val sections: List<Pair<Char, List<ArtistID3>>> = emptyList(),
        /** Bumped when chart matches change, so the chart cards re-read their library counts. */
        val chartsVersion: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    /** Reuses the last result for this server for a while; [force] (pull to refresh) reloads. */
    fun load(force: Boolean = false) {
        val cached = if (force) null else Cache.fresh()
        if (cached != null) {
            _state.value = cached.copy(decades = _state.value.decades)
            return
        }
        _state.value = _state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            runCatching {
                val genres = async { BrowseRepository.genres() }
                val artists = async { BrowseRepository.artists() }
                val list = artists.await()
                val (languages, sections) = withContext(Dispatchers.Default) {
                    val byLanguage = list.groupBy { BrowseClassifier.languageOf(it.name) }
                    val byInitial = list
                        .groupBy { BrowseRepository.initialOf(it.name) }
                        .toSortedMap(compareBy { if (it == '#') '~' else it })
                        .map { (letter, group) -> letter to group.sortedBy { it.name?.lowercase() } }
                    byLanguage to byInitial
                }
                val next = _state.value.copy(
                    loading = false,
                    genres = genres.await(),
                    languages = languages,
                    sections = sections,
                )
                Cache.store(next)
                _state.value = next
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _state.value = _state.value.copy(loading = false, failed = true)
            }
        }
    }

    /** Survives tab switches, which recreate this view model; keyed by server and music folder. */
    private object Cache {
        private const val TTL_MS = 15 * 60 * 1000L
        private var key: String? = null
        private var at = 0L
        private var state: State? = null

        private fun scope() = "${Preferences.getServerId()}|${Preferences.getActiveMusicFolderId()}"

        fun fresh(): State? =
            state?.takeIf { key == scope() && System.currentTimeMillis() - at < TTL_MS }

        fun store(value: State) {
            key = scope()
            at = System.currentTimeMillis()
            state = value
        }
    }

    fun chartsMatched() {
        _state.value = _state.value.copy(chartsVersion = _state.value.chartsVersion + 1)
    }

    fun setDecades(decades: List<Int>?) {
        _state.value = _state.value.copy(decades = decades.orEmpty().sortedDescending())
    }
}
