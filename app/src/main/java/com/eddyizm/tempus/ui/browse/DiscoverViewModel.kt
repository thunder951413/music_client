package com.eddyizm.tempus.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
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
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        load()
    }

    fun load() {
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
                _state.value = _state.value.copy(
                    loading = false,
                    genres = genres.await(),
                    languages = languages,
                    sections = sections,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, failed = true)
            }
        }
    }

    fun setDecades(decades: List<Int>?) {
        _state.value = _state.value.copy(decades = decades.orEmpty().sortedDescending())
    }
}
