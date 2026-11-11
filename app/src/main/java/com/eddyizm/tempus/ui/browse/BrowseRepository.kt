package com.eddyizm.tempus.ui.browse

import android.icu.text.Transliterator
import com.eddyizm.tempus.App
import com.eddyizm.tempus.discovery.MusicDiscoveryRules
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.subsonic.base.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.util.Collections

/** Blocking Subsonic calls for the browse pages, run on the IO dispatcher. */
object BrowseRepository {
    private val pinyin by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII") }

    private fun execute(call: Call<ApiResponse>) =
        call.execute().takeIf { it.isSuccessful }?.body()?.subsonicResponse

    suspend fun genres(): List<Genre> = withContext(Dispatchers.IO) {
        execute(App.getSubsonicClientInstance(false).browsingClient.genres)
            ?.genres?.genres.orEmpty()
            .filter { !it.genre.isNullOrBlank() && it.songCount > 0 }
            .sortedByDescending { it.songCount }
    }

    suspend fun artists(): List<ArtistID3> = withContext(Dispatchers.IO) {
        execute(App.getSubsonicClientInstance(false).browsingClient.artists)
            ?.artists?.indices.orEmpty()
            .flatMap { it.artists.orEmpty() }
    }

    suspend fun randomSongs(size: Int): List<Child> = withContext(Dispatchers.IO) {
        execute(App.getSubsonicClientInstance(false).albumSongListClient.getRandomSongs(size, null, null))
            ?.randomSongs?.songs.orEmpty()
    }

    suspend fun starredSongs(): List<Child> = withContext(Dispatchers.IO) {
        execute(App.getSubsonicClientInstance(false).albumSongListClient.starred2)
            ?.starred2?.songs.orEmpty()
    }

    suspend fun similarSongs(id: String, count: Int): List<Child> = withContext(Dispatchers.IO) {
        runCatching {
            execute(App.getSubsonicClientInstance(false).browsingClient.getSimilarSongs2(id, count))
                ?.similarSongs2?.songs.orEmpty()
        }.getOrDefault(emptyList())
    }

    fun initialOf(name: String?): Char = BrowseClassifier.initialOf(name) { pinyin.transliterate(it) }

    /** Up to [limit] random songs by [artists], matching collaborations and falling back to search for small groups. */
    suspend fun songsByArtists(artists: List<ArtistID3>, limit: Int): List<Child> = withContext(Dispatchers.IO) {
        val artistIds = artists.mapNotNull { it.id }.toSet()
        val artistNames = artists.flatMapTo(HashSet()) { MusicDiscoveryRules.artistNames(it.name) }
        if (artistIds.isEmpty() && artistNames.isEmpty()) return@withContext emptyList()
        fun matchesGroup(song: Child): Boolean =
            !song.isDir && !song.isVideo && song.id != null &&
                (song.artistId in artistIds || !Collections.disjoint(MusicDiscoveryRules.artistNames(song.artist), artistNames))
        val found = LinkedHashMap<String, Child>()
        repeat(3) {
            if (found.size >= limit) return@repeat
            randomSongs(500).filter(::matchesGroup).forEach { found.putIfAbsent(it.id, it) }
        }
        if (found.size < limit / 2) {
            val searching = App.getSubsonicClientInstance(false).searchingClient
            for (artist in artists.shuffled().take(8)) {
                if (found.size >= limit) break
                val name = artist.name?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val rows = runCatching {
                    execute(searching.search3(name, 25, 0, 0, 0, 0, 0))?.searchResult3?.songs.orEmpty()
                }.getOrDefault(emptyList())
                rows.filter(::matchesGroup).forEach { found.putIfAbsent(it.id, it) }
            }
        }
        found.values.take(limit)
    }

    suspend fun songsByArtists(artistIds: Set<String>, limit: Int): List<Child> {
        val found = LinkedHashMap<String, Child>()
        repeat(3) {
            if (found.size >= limit) return@repeat
            randomSongs(500).filter { it.artistId in artistIds }.forEach { found.putIfAbsent(it.id, it) }
        }
        return found.values.take(limit)
    }
}
