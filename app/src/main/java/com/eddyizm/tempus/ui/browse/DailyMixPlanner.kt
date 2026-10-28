package com.eddyizm.tempus.ui.browse

import androidx.annotation.Keep
import com.eddyizm.tempus.discovery.AllChartsMatcher
import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.discovery.MusicDiscoveryRules
import com.eddyizm.tempus.subsonic.models.Child
import com.google.gson.Gson
import java.util.UUID
import kotlin.random.Random

/** A chart song the library lacks, by an artist the listener already has; downloaded by hand. */
@Keep data class DailyWish(val provider: String, val id: String, val title: String, val artists: List<String>, val duration: Int?)

/** Stored in the comment of the day's server playlist, so the Mac helper sees it; [device] owns it. */
@Keep data class DailyBinding(
    val version: Int = 1, val date: String, val device: String, val requestId: String = UUID.randomUUID().toString(),
    val complete: Boolean = true, val missing: List<DailyWish> = emptyList(),
) {
    fun comment(): String = PREFIX + Gson().toJson(this)

    companion object {
        const val PREFIX = "Tempus daily mix v1\n"
        fun read(comment: String?): DailyBinding? = runCatching {
            if (comment?.startsWith(PREFIX) != true || comment.length > 200_000) return null
            Gson().fromJson(comment.removePrefix(PREFIX), DailyBinding::class.java)
                .takeIf { it.version == 1 && it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && !it.device.isNullOrBlank() && it.missing != null }
        }.getOrNull()
    }
}

/**
 * Picks today's mix without network access, so the choices can be tested. Seeds come from four
 * kinds of listening (recent, most played, favourites, favourites not heard lately), related songs
 * are taken in turns per seed, and a few never-heard songs keep the mix from repeating itself.
 */
object DailyMixPlanner {
    const val SIZE = 30
    const val SEEDS = 4
    const val PER_ARTIST = 3
    const val UNHEARD = 5
    const val WISHES = 5

    fun seeds(starred: List<Child>, recent: List<Child>, frequent: List<Child>, random: Random): List<Child> {
        val recentIds = recent.mapTo(HashSet()) { it.id }
        val forgotten = (starred + frequent).filter { it.id !in recentIds }
        val pools = listOf(recent, frequent, starred, forgotten).map { it.shuffled(random) }
        val picked = LinkedHashMap<String, Child>()
        // One from each kind first, then whatever is left, so a thin history still gives seeds.
        pools.forEach { pool -> pool.firstOrNull { it.id !in picked }?.let { picked[it.id] = it } }
        pools.flatten().forEach { if (picked.size < SEEDS) picked.putIfAbsent(it.id, it) }
        return picked.values.take(SEEDS)
    }

    /**
     * [related] holds the songs found for each seed, similar ones first. Songs heard in the last
     * two days ([justHeard]) are skipped and no artist gets more than [PER_ARTIST] songs.
     */
    fun assemble(
        seeds: List<Child>, related: List<List<Child>>, unheard: List<Child>, filler: List<Child>,
        justHeard: Set<String>, random: Random,
    ): List<Child> {
        val mix = LinkedHashMap<String, Child>()
        val perArtist = HashMap<String, Int>()
        fun add(song: Child, limit: Int = SIZE): Boolean {
            if (mix.size >= limit || song.isDir || song.id in mix || song.id in justHeard) return false
            val artist = MusicDiscoveryRules.normalize(song.artist)
            if ((perArtist[artist] ?: 0) >= PER_ARTIST) return false
            perArtist[artist] = (perArtist[artist] ?: 0) + 1
            mix[song.id] = song
            return true
        }
        seeds.forEach { add(it) }
        val queues = related.map { ArrayDeque(it) }
        val target = SIZE - UNHEARD
        while (mix.size < target && queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> while (queue.isNotEmpty()) if (add(queue.removeFirst(), target)) break }
        }
        unheard.forEach { add(it) }
        filler.shuffled(random).forEach { add(it) }
        return mix.values.shuffled(random)
    }

    /**
     * Chart songs by artists the listener likes, then by any artist in the library, in chart order.
     * The caller still checks each against the library before treating it as missing.
     */
    fun wishCandidates(
        charts: List<Pair<ChartSource, List<ChartTrack>>>, favourite: Set<String>, library: Set<String>,
        pending: Set<String>, limit: Int,
    ): List<Pair<ChartSource.Provider, ChartTrack>> {
        val seen = HashSet(pending)
        val rows = charts.flatMap { (source, tracks) -> tracks.map { source.provider to it } }
            .filter { (_, track) -> !track.id.isNullOrBlank() && !track.title.isNullOrBlank() && seen.add(AllChartsMatcher.songKey(track)) }
        fun artists(track: ChartTrack) = (track.artists.ifEmpty { listOf(track.artist) }).map { MusicDiscoveryRules.normalize(it) }
        val liked = rows.filter { (_, t) -> artists(t).any { it in favourite } }
        val known = rows.filter { (_, t) -> artists(t).any { it in library } }
        return (liked + known).distinctBy { AllChartsMatcher.songKey(it.second) }.take(limit)
    }

    fun wishKey(wish: DailyWish) = MusicDiscoveryRules.normalize(wish.title) + "|" +
        MusicDiscoveryRules.normalize(wish.artists.firstOrNull())
}
