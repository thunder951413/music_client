package com.eddyizm.tempus.ui.browse

import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.discovery.DiscoveryRepository
import com.eddyizm.tempus.discovery.MusicDiscoveryRules
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.util.Preferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Call
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Today's mix, one per device, kept as a server playlist ("Daily mix · 10-28 · Pixel 9") so its
 * new-song picks reach the Mac import helper. Playlists older than [KEEP_DAYS] are removed, those of
 * other devices too, so a reinstalled app leaves nothing behind.
 */
@OptIn(UnstableApi::class)
object DailyMixRepository {
    @Keep data class Today(val songs: List<Child>, val wishes: List<DailyWish>, val pending: Boolean = false, val stale: Boolean = false)

    private const val KEEP_DAYS = 7L
    private const val HISTORY_DAYS = 30L
    private const val WISH_TIMEOUT_MS = 25_000L
    private const val CACHE_KEY = "browse_daily_mix_key"
    private const val CACHE_VALUE = "browse_daily_mix_today"
    private const val DEVICE_KEY = "browse_daily_mix_device"

    /** A random id per install, so two phones of the same model still get their own mix. */
    private val device: String by lazy {
        val prefs = App.getInstance().preferences
        prefs.getString(DEVICE_KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit { putString(DEVICE_KEY, it) } }
    }

    private fun execute(call: Call<ApiResponse>) =
        call.execute().takeIf { it.isSuccessful }?.body()?.subsonicResponse
            ?.takeIf { it.error == null } ?: throw IllegalStateException("request failed")

    private val client get() = App.getSubsonicClientInstance(false)

    suspend fun today(): Today = withContext(Dispatchers.IO) {
        com.eddyizm.tempus.hub.HubRepository.today()?.let { return@withContext it }
        val date = LocalDate.now()
        val key = "${Preferences.getServerId()}|${Preferences.getUser()}|$device|$date"
        readCache(key)?.let { return@withContext it }
        // null when the server cannot be read: the mix is still built, just not saved.
        val everything = runCatching { all() }.getOrNull()
        val existing = everything?.filter { it.second.device == device }
        val others = everything?.filter { it.second.device != device }?.flatMap { it.second.missing }
        existing?.firstOrNull { it.second.date == date.toString() }?.let { (playlist, binding) ->
            runCatching { entries(playlist.id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                return@withContext Today(it, binding.missing).also { today -> writeCache(key, today) }
            }
        }
        // Every device's pending picks, so two phones do not both ask for the same new song.
        val today = generate(key, existing.orEmpty().flatMap { it.second.missing } + others.orEmpty())
        if (everything != null && today.songs.isNotEmpty()) runCatching { save(date, today, everything) }
        writeCache(key, today)
        today
    }

    private fun all(): List<Pair<Playlist, DailyBinding>> =
        execute(client.playlistClient.playlists).playlists?.playlists.orEmpty()
            .filter { it.owner == Preferences.getUser() }
            .mapNotNull { p -> DailyBinding.read(p.comment)?.let { p to it } }

    private fun saved() = all().filter { it.second.device == device }

    private fun entries(id: String): List<Child> =
        execute(client.playlistClient.getPlaylist(id)).playlist?.entries.orEmpty()

    private fun save(date: LocalDate, today: Today, everything: List<Pair<Playlist, DailyBinding>>) {
        // An earlier run on this device may have saved it while this one was building.
        if (saved().none { it.second.date == date.toString() }) {
            val name = App.getContext().getString(R.string.home_daily_playlist, date.format(DateTimeFormatter.ofPattern("MM-dd")), Build.MODEL)
            val ids = ArrayList(today.songs.map { it.id })
            val id = execute(client.playlistClient.createPlaylist(null, name, ids)).playlist?.id
                ?: execute(client.playlistClient.playlists).playlists?.playlists
                    ?.lastOrNull { it.name == name && it.owner == Preferences.getUser() && it.comment.isNullOrEmpty() }?.id
                ?: return
            val comment = DailyBinding(date = date.toString(), device = device, missing = today.wishes).comment()
            // Without its marker the playlist would never be recognised or cleaned up.
            runCatching { execute(client.playlistClient.setImportComment(id, comment, false, null)) }
                .onFailure { runCatching { execute(client.playlistClient.deletePlaylist(id)) } }
        }
        val oldest = date.minusDays(KEEP_DAYS - 1).toString()
        everything.filter { it.second.date < oldest }
            .forEach { runCatching { execute(client.playlistClient.deletePlaylist(it.first.id)) } }
    }

    private suspend fun generate(key: String, pending: List<DailyWish>): Today = coroutineScope {
        val random = Random(key.hashCode())
        val now = System.currentTimeMillis()
        val starred = async { runCatching { BrowseRepository.starredSongs() }.getOrDefault(emptyList()) }
        val pool = async { runCatching { BrowseRepository.randomSongs(500) }.getOrDefault(emptyList()) }
        val history = runCatching {
            AppDatabase.getInstance().chronologyDao()
                .getPlayedSince(Preferences.getServerId(), now - TimeUnit.DAYS.toMillis(HISTORY_DAYS), 200)
        }.getOrDefault(emptyList())
        val justHeard = history.filter { it.timestamp >= now - TimeUnit.DAYS.toMillis(2) }.mapTo(HashSet()) { it.id }
        val songs = pool.await()
        val frequent = songs.filter { (it.playCount ?: 0L) > 0L }.sortedByDescending { it.playCount }.take(30)
        val liked = starred.await()
        val listened = liked + history + frequent
        val wishList = async { withTimeoutOrNull(WISH_TIMEOUT_MS) { runCatching { wishes(listened, pending) }.getOrNull() }.orEmpty() }

        val seeds = DailyMixPlanner.seeds(liked, history, frequent, random)
        if (seeds.isEmpty()) return@coroutineScope Today(emptyList(), wishList.await())
        val related = seeds.map { seed -> async { related(seed, songs, Random(key.hashCode() xor seed.id.hashCode())) } }.awaitAll()
        val heard = runCatching { DiscoveryRepository().heardIds() }.getOrDefault(emptySet())
        val unheard = MusicDiscoveryRules.unheard(songs.shuffled(random), heard, emptySet(), DailyMixPlanner.UNHEARD * 2)
        Today(DailyMixPlanner.assemble(seeds, related, unheard, liked + frequent, justHeard, random), wishList.await())
    }

    /** Similar songs from the server (needs Last.fm in Navidrome), else same artist, genre and decade. */
    private suspend fun related(seed: Child, pool: List<Child>, random: Random): List<Child> {
        val similar = BrowseRepository.similarSongs(seed.id, 15)
        if (similar.size >= 5) return similar
        val artist = seed.artist?.takeIf { it.isNotBlank() }?.let { name ->
            runCatching { search(name) }.getOrDefault(emptyList())
                .filter { it.id != seed.id && (it.artistId == seed.artistId || it.artist == seed.artist) }
        }.orEmpty().shuffled(random).take(5)
        val genre = LibraryMixes.genresOf(seed).firstOrNull()?.let { name ->
            runCatching {
                execute(client.albumSongListClient.getRandomSongs(8, null, null, name)).randomSongs?.songs.orEmpty()
            }.getOrDefault(emptyList())
        }.orEmpty()
        val decade = seed.year?.takeIf { it >= 1900 }?.let { it / 10 }
        val era = if (decade == null) emptyList() else pool.filter { (it.year ?: 0) / 10 == decade }.take(5)
        val fallback = listOf(artist, genre, era).flatMap { it.withIndex() }.sortedBy { it.index }.map { it.value }
        return similar + fallback.filter { it.id != seed.id }
    }

    private fun search(query: String): List<Child> =
        execute(client.searchingClient.search3(query, 40, 0, 0, 0, 0, 0)).searchResult3?.songs.orEmpty()

    private suspend fun wishes(listened: List<Child>, pending: List<DailyWish>): List<DailyWish> {
        fun names(value: String?) = value.orEmpty().split('/', ',', '&', '、', ';', '，')
            .map { MusicDiscoveryRules.normalize(it) }.filter { it.isNotEmpty() }
        val favourite = listened.flatMapTo(HashSet()) { names(it.artist) }
        val library = runCatching { BrowseRepository.artists() }.getOrDefault(emptyList())
            .flatMapTo(HashSet()) { names(it.name) }
        val discovery = DiscoveryRepository()
        val charts = ChartSource.entries.mapNotNull { source ->
            runCatching { source to discovery.chart(source, false).tracks }.getOrNull()
        }
        val pendingKeys = pending.mapTo(HashSet()) { DailyMixPlanner.wishKey(it) }
        val found = ArrayList<DailyWish>()
        for ((provider, track) in DailyMixPlanner.wishCandidates(charts, favourite, library, pendingKeys, 25)) {
            if (found.size >= DailyMixPlanner.WISHES) break
            val candidates = runCatching { search(track.title) }.getOrNull() ?: continue
            if (MusicDiscoveryRules.matches(track, candidates).isNotEmpty()) continue
            val artists = track.artists.filterNotNull().ifEmpty { listOfNotNull(track.artist) }
            if (artists.isEmpty()) continue
            found += DailyWish(provider.name, track.id, track.title, artists, track.duration)
        }
        return found
    }

    private fun readCache(key: String): Today? {
        val prefs = App.getInstance().preferences
        if (prefs.getString(CACHE_KEY, null) != key) return null
        val json = prefs.getString(CACHE_VALUE, null) ?: return null
        return runCatching { Gson().fromJson(json, Today::class.java) }.getOrNull()
            ?.takeIf { it.songs != null && it.wishes != null && it.songs.isNotEmpty() }
    }

    private fun writeCache(key: String, today: Today) {
        if (today.songs.isEmpty()) return
        App.getInstance().preferences.edit {
            putString(CACHE_KEY, key)
            putString(CACHE_VALUE, Gson().toJson(today))
        }
    }
}
