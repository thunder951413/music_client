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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @Keep data class Today(
        val songs: List<Child>,
        val wishes: List<DailyWish>,
        val pending: Boolean = false,
        val stale: Boolean = false,
        val offline: Boolean = false,
        val fromHub: Boolean = false,
        val importedToday: Int = 0,
        val syncedAtMs: Long = 0L,
    )

    private const val KEEP_DAYS = 7L
    private const val HISTORY_DAYS = 30L
    private const val WISH_TIMEOUT_MS = 25_000L
    private const val CACHE_KEY = "browse_daily_mix_key"
    private const val CACHE_VALUE = "browse_daily_mix_today"
    private const val DEVICE_KEY = "browse_daily_mix_device"

    private val mutex = Mutex()

    /** A random id per install, so two phones of the same model still get their own mix. */
    private val device: String by lazy {
        val prefs = App.getInstance().preferences
        prefs.getString(DEVICE_KEY, null) ?: UUID.randomUUID().toString().also { prefs.edit { putString(DEVICE_KEY, it) } }
    }

    private fun currentKey(date: LocalDate = LocalDate.now()): String =
        "${Preferences.getServerId()}|${Preferences.getUser()}|$device|$date"

    /** Non-blocking read of today's verified cache for the active server/account/date (`null` if stale/absent). */
    fun peekCachedToday(): Today? {
        com.eddyizm.tempus.hub.HubRepository.peekCachedToday()?.let { return it }
        return readCache(currentKey(LocalDate.now()))
    }

    private fun execute(call: Call<ApiResponse>) =
        call.execute().takeIf { it.isSuccessful }?.body()?.subsonicResponse
            ?.takeIf { it.error == null } ?: throw IllegalStateException("request failed")

    private val client get() = App.getSubsonicClientInstance(false)

    suspend fun today(): Today = withContext(Dispatchers.IO) {
        mutex.withLock {
            com.eddyizm.tempus.hub.HubRepository.today()?.let { return@withLock it }
            val date = LocalDate.now()
            val key = currentKey(date)
            readCache(key)?.let { return@withLock it }
            // null when the server cannot be read: the mix is still built, just not saved.
            val everything = runCatching { all() }.getOrNull()
            val existing = everything?.filter { it.second.device == device }
            val others = everything?.filter { it.second.device != device }?.flatMap { it.second.missing }
            existing?.firstOrNull { it.second.date == date.toString() }?.let { (playlist, binding) ->
                runCatching { entries(playlist.id) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                    return@withLock Today(
                        songs = it,
                        wishes = binding.missing,
                        fromHub = false,
                        syncedAtMs = System.currentTimeMillis(),
                    ).also { today -> writeCache(key, today) }
                }
            }
            // Every device's pending picks, so two phones do not both ask for the same new song.
            val recentPlaylists = existing.orEmpty()
                .filter { it.second.date < date.toString() }
                .sortedByDescending { it.second.date }
                .take(3)
            val recentMixIds = recentPlaylists.flatMapTo(HashSet()) { (playlist, _) ->
                runCatching { entries(playlist.id).mapNotNull { it.id } }.getOrDefault(emptyList())
            }
            val today = generate(key, existing.orEmpty().flatMap { it.second.missing } + others.orEmpty(), recentMixIds)
            if (everything != null && today.songs.isNotEmpty()) runCatching { save(date, today, everything) }
            writeCache(key, today)
            today
        }
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

    private suspend fun generate(key: String, pending: List<DailyWish>, recentMixIds: Set<String> = emptySet()): Today = coroutineScope {
        val random = Random(key.hashCode())
        val now = System.currentTimeMillis()
        val starred = async { runCatching { BrowseRepository.starredSongs() }.getOrDefault(emptyList()) }
        val pool = async {
            val initial = runCatching { BrowseRepository.randomSongs(500) }.getOrDefault(emptyList())
            if (initial.size >= DailyMixPlanner.SIZE) initial
            else (initial + runCatching { BrowseRepository.resilientCandidatePool(100) }.getOrDefault(emptyList())).distinctBy { it.id }
        }
        val frequentAlbums = async { runCatching { BrowseRepository.albumTracksByType("frequent", 4, 24) }.getOrDefault(emptyList()) }
        val history = runCatching {
            AppDatabase.getInstance().chronologyDao()
                .getPlayedSince(Preferences.getServerId(), now - TimeUnit.DAYS.toMillis(HISTORY_DAYS), 200)
                .map(BrowseRepository::normalizeLocalTrack)
        }.getOrDefault(emptyList())
        val justHeard = history.filter { it.timestamp >= now - TimeUnit.DAYS.toMillis(2) }.mapTo(HashSet()) { it.id }
        val songs = pool.await()
        val noiseGenres = detectNoiseGenres(songs)
        val frequent = (frequentAlbums.await() + songs.filter { (it.playCount ?: 0L) > 0L }.sortedByDescending { it.playCount }.take(30))
            .distinctBy { it.id }
        val liked = starred.await()
        val listened = liked + history + frequent
        val wishList = async { withTimeoutOrNull(WISH_TIMEOUT_MS) { runCatching { wishes(listened, pending) }.getOrNull() }.orEmpty() }

        val seeds = DailyMixPlanner.seeds(liked, history, frequent, random).ifEmpty {
            songs.shuffled(random).take(DailyMixPlanner.SEEDS)
        }
        if (seeds.isEmpty()) return@coroutineScope Today(emptyList(), wishList.await(), fromHub = false, syncedAtMs = now)
        val related = seeds.map { seed -> async { related(seed, songs, noiseGenres, Random(key.hashCode() xor seed.id.hashCode())) } }.awaitAll()
        val heard = runCatching { DiscoveryRepository().heardIds() }.getOrDefault(emptySet())
        val unheard = MusicDiscoveryRules.unheard(songs.shuffled(random), heard, emptySet(), DailyMixPlanner.UNHEARD * 2)
        Today(
            songs = DailyMixPlanner.assemble(seeds, related, unheard, liked + frequent + songs, justHeard, random, recentMixIds),
            wishes = wishList.await(),
            fromHub = false,
            syncedAtMs = System.currentTimeMillis(),
        )
    }

    private fun detectNoiseGenres(songs: List<Child>): Set<String> {
        val genreCounts = HashMap<String, Int>()
        var taggedSongs = 0
        for (s in songs) {
            val genres = LibraryMixes.genresOf(s)
            if (genres.isNotEmpty()) {
                taggedSongs++
                genres.forEach { genreCounts[it] = (genreCounts[it] ?: 0) + 1 }
            }
        }
        return if (taggedSongs >= 10) {
            genreCounts.filterValues { it * 5 > taggedSongs * 3 }.keys
        } else {
            emptySet()
        }
    }

    /** Similar songs from the server (song ID then artist ID), else same artist, genre and decade. */
    private suspend fun related(seed: Child, pool: List<Child>, noiseGenres: Set<String>, random: Random): List<Child> {
        val similar = BrowseRepository.similarSongs(seed.id, 15, seed.artistId).filter { it.id != seed.id }
        if (similar.size >= 5) return similar
        val seedArtists = MusicDiscoveryRules.artistNames(seed.artist)
        val lead = seed.artist?.split('/', '•', '·', ',', '&', '、', '，', '(')?.firstOrNull()?.trim()
        val artist = if (!lead.isNullOrEmpty() && seedArtists.isNotEmpty()) {
            (runCatching { search(lead) }.getOrDefault(emptyList()) + pool)
                .filter(BrowseRepository::isPlayableSong)
                .distinctBy { it.id }
                .filter { it.id != seed.id && ((!seed.artistId.isNullOrBlank() && it.artistId == seed.artistId) || !java.util.Collections.disjoint(MusicDiscoveryRules.artistNames(it.artist), seedArtists)) }
                .shuffled(random).take(6)
        } else emptyList()
        val genre = LibraryMixes.genresOf(seed).firstOrNull { it !in noiseGenres }?.let { name ->
            val byRandom = runCatching {
                execute(client.albumSongListClient.getRandomSongs(6, null, null, name)).randomSongs?.songs.orEmpty()
            }.getOrDefault(emptyList()).filter(BrowseRepository::isPlayableSong)
            byRandom.ifEmpty {
                runCatching {
                    execute(client.albumSongListClient.getSongsByGenre(name, 12, 0)).songsByGenre?.songs.orEmpty()
                }.getOrDefault(emptyList()).filter(BrowseRepository::isPlayableSong).shuffled(random)
            }
        }.orEmpty().filter { it.id != seed.id }.take(4)
        val decade = seed.year?.takeIf { it >= 1900 }?.let { it / 10 }
        val era = if (decade == null) emptyList() else pool.filter { it.id != seed.id && (it.year ?: 0) / 10 == decade }.shuffled(random).take(3)
        val fallback = listOf(artist, genre, era).flatMap { it.withIndex() }.sortedBy { it.index }.map { it.value }
        return (similar + fallback.filter { it.id != seed.id }).distinctBy { it.id }
    }

    private fun search(query: String): List<Child> =
        execute(client.searchingClient.search3(query, 100, 0, 0, 0, 0, 0)).searchResult3?.songs.orEmpty()

    /**
     * Builds a ~35-track Personal Roam (`私人漫游`) queue combining 4 signals:
     * 1) red-heart favorites (`starredSongs`),
     * 2) high-frequency listening (`frequent` albums + `playCount > 0`),
     * 3) recent listening (local `chronologyDao` + server `recent` albums),
     * 4) library exploration (`resilientCandidatePool`).
     */
    suspend fun roamQueue(): List<Child> = withContext(Dispatchers.IO) {
        coroutineScope {
            val random = Random(System.nanoTime())
            val now = System.currentTimeMillis()

            val starredDeferred = async { runCatching { BrowseRepository.starredSongs() }.getOrDefault(emptyList()) }
            val frequentAlbumsDeferred = async { runCatching { BrowseRepository.albumTracksByType("frequent", 6, 36) }.getOrDefault(emptyList()) }
            val recentAlbumsDeferred = async { runCatching { BrowseRepository.albumTracksByType("recent", 6, 36) }.getOrDefault(emptyList()) }
            val poolDeferred = async { runCatching { BrowseRepository.resilientCandidatePool(100) }.getOrDefault(emptyList()) }

            val localHistory = runCatching {
                AppDatabase.getInstance().chronologyDao()
                    .getPlayedSince(Preferences.getServerId(), now - TimeUnit.DAYS.toMillis(HISTORY_DAYS), 100)
                    .map(BrowseRepository::normalizeLocalTrack)
                    .filter(BrowseRepository::isPlayableSong)
            }.getOrDefault(emptyList())

            val starred = BrowseRepository.safeRatingFilter(starredDeferred.await())
            val explorePool = poolDeferred.await()
            val frequentFromPool = explorePool
                .filter { (it.playCount ?: 0L) > 0L }
                .sortedByDescending { it.playCount }
                .take(30)
            val frequent = BrowseRepository.safeRatingFilter(
                (frequentAlbumsDeferred.await() + frequentFromPool).distinctBy { it.id }
            )
            val recent = BrowseRepository.safeRatingFilter(
                (localHistory + recentAlbumsDeferred.await()).distinctBy { it.id }
            )
            val allCandidates = (explorePool + starred + frequent + recent).distinctBy { it.id }

            val seeds = DailyMixPlanner.roamSeeds(
                starred = starred,
                frequent = frequent,
                recent = recent,
                explorePool = allCandidates,
                random = random,
            )
            if (seeds.isEmpty()) return@coroutineScope emptyList()

            val noiseGenres = detectNoiseGenres(allCandidates)
            val seedRelated = seeds.map { seed ->
                async {
                    val similar = runCatching { BrowseRepository.similarSongs(seed.id, 20, seed.artistId) }
                        .getOrDefault(emptyList())
                        .filter { it.id != seed.id }
                    if (similar.size >= 10) similar
                    else (similar + runCatching { related(seed, allCandidates, noiseGenres, Random(random.nextLong())) }
                        .getOrDefault(emptyList())).distinctBy { it.id }
                }
            }.awaitAll()

            DailyMixPlanner.assembleRoamQueue(
                seeds = seeds,
                seedRelated = seedRelated,
                starred = starred,
                frequent = frequent,
                recent = recent,
                explorePool = allCandidates,
                random = random,
            )
        }
    }

    private suspend fun wishes(listened: List<Child>, pending: List<DailyWish>): List<DailyWish> {
        val favourite = listened.flatMapTo(HashSet()) { MusicDiscoveryRules.artistNames(it.artist) }
        val library = runCatching { BrowseRepository.artists() }.getOrDefault(emptyList())
            .flatMapTo(HashSet()) { MusicDiscoveryRules.artistNames(it.name) }
        val discovery = DiscoveryRepository()
        val charts = ChartSource.entries.mapNotNull { source ->
            runCatching { source to discovery.chart(source, false).tracks }.getOrNull()
        }
        val pendingKeys = pending.mapTo(HashSet()) { DailyMixPlanner.wishKey(it) }
        val found = ArrayList<DailyWish>()
        for ((provider, track) in DailyMixPlanner.wishCandidates(charts, favourite, library, pendingKeys, 25)) {
            if (found.size >= DailyMixPlanner.WISHES) break
            val candidates = runCatching { discovery.searchTrack(track) }.getOrNull() ?: continue
            if (MusicDiscoveryRules.matches(track, candidates).isNotEmpty()) continue
            val artists = track.artists.filterNotNull().ifEmpty { listOfNotNull(track.artist) }
            if (artists.isEmpty()) continue
            found += DailyWish(provider.name, track.id, track.title, artists, track.duration, track.originalTitle)
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
