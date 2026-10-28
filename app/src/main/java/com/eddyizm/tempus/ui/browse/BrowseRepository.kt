package com.eddyizm.tempus.ui.browse

import android.icu.text.Transliterator
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.subsonic.base.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call

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

    /** Up to [limit] random songs by [artistIds], sampled from a few random batches. */
    suspend fun songsByArtists(artistIds: Set<String>, limit: Int): List<Child> {
        val found = LinkedHashMap<String, Child>()
        repeat(3) {
            if (found.size >= limit) return@repeat
            randomSongs(500).filter { it.artistId in artistIds }.forEach { found.putIfAbsent(it.id, it) }
        }
        return found.values.take(limit)
    }
}
