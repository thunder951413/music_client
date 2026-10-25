package com.eddyizm.tempus.ui.browse

import android.icu.text.Transliterator
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.util.Preferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.time.LocalDate
import kotlin.random.Random

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

    /**
     * Today's mix: songs similar to three favourites picked by date, topped up with favourites,
     * shuffled with a date seed. It is cached per server and day, so it stays put until tomorrow.
     */
    suspend fun dailyMix(): List<Child> {
        val key = "${Preferences.getServerId()}|${LocalDate.now()}"
        readDailyCache(key)?.let { return it }
        val starred = starredSongs()
        if (starred.isEmpty()) return emptyList()
        val random = Random(key.hashCode())
        val seeds = starred.shuffled(random).take(3)
        val mix = LinkedHashMap<String, Child>()
        for (seed in seeds) similarSongs(seed.id, 15).forEach { mix.putIfAbsent(it.id, it) }
        starred.shuffled(random).forEach { if (mix.size < DAILY_SIZE) mix.putIfAbsent(it.id, it) }
        val result = mix.values.shuffled(random).take(DAILY_SIZE)
        writeDailyCache(key, result)
        return result
    }

    private fun readDailyCache(key: String): List<Child>? {
        val prefs = App.getInstance().preferences
        if (prefs.getString(DAILY_KEY, null) != key) return null
        val json = prefs.getString(DAILY_SONGS, null) ?: return null
        return runCatching { Gson().fromJson<List<Child>>(json, object : TypeToken<List<Child>>() {}.type) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun writeDailyCache(key: String, songs: List<Child>) {
        if (songs.isEmpty()) return
        App.getInstance().preferences.edit {
            putString(DAILY_KEY, key)
            putString(DAILY_SONGS, Gson().toJson(songs))
        }
    }

    private const val DAILY_SIZE = 30
    private const val DAILY_KEY = "browse_daily_mix_key"
    private const val DAILY_SONGS = "browse_daily_mix_songs"
}
