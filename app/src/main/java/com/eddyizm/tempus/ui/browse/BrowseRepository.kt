package com.eddyizm.tempus.ui.browse

import android.icu.text.Transliterator
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.discovery.MusicDiscoveryRules
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Call
import java.util.Collections

/** Blocking Subsonic calls for the browse pages, run on the IO dispatcher. */
@OptIn(UnstableApi::class)
object BrowseRepository {
    private val pinyin by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII") }

    @Volatile
    internal var clientProvider: () -> com.eddyizm.tempus.subsonic.Subsonic = {
        App.getSubsonicClientInstance(false)
    }

    private fun execute(call: Call<ApiResponse>) =
        runCatching { call.execute().takeIf { it.isSuccessful }?.body()?.subsonicResponse?.takeIf { it.error == null } }
            .getOrNull()

    fun isPlayableSong(song: Child?): Boolean =
        song != null && !song.isDir && !song.isVideo && !song.id.isNullOrBlank()

    fun <T : Child> normalizeLocalTrack(song: T): T {
        if (song.userRating != null && song.userRating == 0) {
            song.userRating = null
        }
        return song
    }

    fun safeRatingFilter(songs: List<Child>): List<Child> {
        val playable = songs.filter(::isPlayableSong)
        if (playable.isEmpty()) return emptyList()
        val copy = ArrayList(playable)
        runCatching { MusicUtil.ratingFilter(copy) }
        return if (copy.isNotEmpty()) copy else playable
    }

    suspend fun genres(): List<Genre> = withContext(Dispatchers.IO) {
        execute(clientProvider().browsingClient.genres)
            ?.genres?.genres.orEmpty()
            .filter { !it.genre.isNullOrBlank() && it.songCount > 0 }
            .sortedByDescending { it.songCount }
    }

    suspend fun artists(): List<ArtistID3> = withContext(Dispatchers.IO) {
        execute(clientProvider().browsingClient.artists)
            ?.artists?.indices.orEmpty()
            .flatMap { it.artists.orEmpty() }
    }

    suspend fun randomSongs(size: Int): List<Child> = withContext(Dispatchers.IO) {
        val client = clientProvider().albumSongListClient
        val attempts = buildList {
            add(size)
            if (size > 100) add(100)
            if (size > 50) add(50)
        }.distinct()
        for (attempt in attempts) {
            val rows = execute(client.getRandomSongs(attempt, null, null))
                ?.randomSongs?.songs.orEmpty()
                .filter(::isPlayableSong)
            if (rows.isNotEmpty()) return@withContext rows
        }
        emptyList()
    }

    suspend fun starredSongs(): List<Child> = withContext(Dispatchers.IO) {
        execute(clientProvider().albumSongListClient.starred2)
            ?.starred2?.songs.orEmpty()
            .filter(::isPlayableSong)
    }

    /**
     * Subsonic `getSimilarSongs` accepts a Song/Media ID, whereas `getSimilarSongs2` expects an
     * ID3 Artist ID. Try song-level similarity first, then artist-level similarity when available.
     */
    suspend fun similarSongs(id: String, count: Int, artistId: String? = null): List<Child> = withContext(Dispatchers.IO) {
        val browsing = clientProvider().browsingClient
        val bySong = execute(browsing.getSimilarSongs(id, count))
            ?.similarSongs?.songs.orEmpty()
            .filter(::isPlayableSong)
        if (bySong.isNotEmpty()) return@withContext bySong
        if (!artistId.isNullOrBlank()) {
            val byArtist = execute(browsing.getSimilarSongs2(artistId, count))
                ?.similarSongs2?.songs.orEmpty()
                .filter(::isPlayableSong)
            if (byArtist.isNotEmpty()) return@withContext byArtist
        }
        emptyList()
    }

    /** Harvests playable tracks from album lists (`frequent`, `recent`, `random`, `newest`). */
    suspend fun albumTracksByType(type: String, albumLimit: Int = 6, trackLimit: Int = 36): List<Child> = withContext(Dispatchers.IO) {
        val subsonic = clientProvider()
        val albums = execute(subsonic.albumSongListClient.getAlbumList2(type, albumLimit, 0, null, null))
            ?.albumList2?.albums.orEmpty()
        if (albums.isEmpty()) return@withContext emptyList()
        val collected = LinkedHashMap<String, Child>()
        for (album in albums) {
            if (collected.size >= trackLimit) break
            val albumId = album.id?.takeIf { it.isNotBlank() } ?: continue
            val songs = execute(subsonic.browsingClient.getAlbum(albumId))
                ?.album?.songs.orEmpty()
                .filter(::isPlayableSong)
            for (song in songs) {
                collected.putIfAbsent(song.id, song)
                if (collected.size >= trackLimit) break
            }
        }
        collected.values.toList()
    }

    /**
     * Multi-stage library candidate harvester so Roam / Daily Mix never collapses to 1 track even if
     * `getRandomSongs(200)` is rejected, capped, or unavailable on a lightweight Subsonic server.
     */
    suspend fun resilientCandidatePool(minSize: Int = 80): List<Child> = withContext(Dispatchers.IO) {
        val pool = LinkedHashMap<String, Child>()
        fun addAll(rows: Iterable<Child>) {
            for (song in rows) {
                if (isPlayableSong(song)) pool.putIfAbsent(song.id, song)
            }
        }

        addAll(randomSongs(maxOf(minSize, 100)))
        if (pool.size < minSize) {
            addAll(randomSongs(50))
        }
        if (pool.size < minSize) {
            for (type in listOf("random", "newest", "frequent", "recent")) {
                if (pool.size >= minSize) break
                addAll(albumTracksByType(type, albumLimit = 6, trackLimit = minSize))
            }
        }
        if (pool.size < minSize) {
            val searching = clientProvider().searchingClient
            for (query in listOf("", "a", "e", "i")) {
                if (pool.size >= minSize) break
                val rows = execute(searching.search3(query, 100, 0, 0, 0, 0, 0))
                    ?.searchResult3?.songs.orEmpty()
                addAll(rows)
                if (query.isEmpty() && rows.isNotEmpty()) break
            }
        }
        if (pool.size < minSize) {
            runCatching {
                val db = AppDatabase.getInstance()
                addAll(db.chronologyDao().getPlayedSince(Preferences.getServerId(), 0L, 200).map(::normalizeLocalTrack))
                addAll(db.queueDao().allSimple.map(::normalizeLocalTrack))
            }
        }
        safeRatingFilter(pool.values.toList())
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
            val searching = clientProvider().searchingClient
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
