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
@Keep data class DailyWish(
    val provider: String,
    val id: String,
    val title: String,
    val artists: List<String>,
    val duration: Int?,
    val original_title: String? = null,
)

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
    const val ROAM_SIZE = 35
    const val ROAM_SEEDS = 5
    const val ROAM_EXPLORE_TARGET = 10

    private fun isPlayable(song: Child?): Boolean =
        song != null && !song.isDir && !song.isVideo && !song.id.isNullOrBlank()

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
     * Multi-signal seed selection for Personal Roam (`私人漫游`): rotates across red-heart favorites,
     * high-frequency tracks, and recent plays (in randomized signal order per session so even a
     * 1-starred-song user does not always start on that one song), plus an exploration seed.
     */
    fun roamSeeds(
        starred: List<Child>,
        frequent: List<Child>,
        recent: List<Child>,
        explorePool: List<Child>,
        random: Random,
        maxSeeds: Int = ROAM_SEEDS,
    ): List<Child> {
        if (maxSeeds <= 0) return emptyList()
        val tasteBuckets = listOf(
            starred.filter(::isPlayable).shuffled(random),
            frequent.filter(::isPlayable).shuffled(random),
            recent.filter(::isPlayable).shuffled(random),
        ).filter { it.isNotEmpty() }.shuffled(random)

        val exploreShuffled = explorePool.filter(::isPlayable).shuffled(random)
        val picked = LinkedHashMap<String, Child>()

        // Round-robin one from each active taste signal first (primary seed rotates across signals).
        val tasteLimit = if (exploreShuffled.isNotEmpty() && maxSeeds > 1) maxSeeds - 1 else maxSeeds
        var progressed = true
        val cursors = IntArray(tasteBuckets.size)
        while (picked.size < tasteLimit && progressed) {
            progressed = false
            for (b in tasteBuckets.indices) {
                if (picked.size >= tasteLimit) break
                val bucket = tasteBuckets[b]
                while (cursors[b] < bucket.size) {
                    val candidate = bucket[cursors[b]++]
                    if (picked.putIfAbsent(candidate.id, candidate) == null) {
                        progressed = true
                        break
                    }
                }
            }
        }

        // Weave an exploration seed from the wider library, or fill all seeds when taste is empty.
        val tasteIds = HashSet<String>().apply {
            tasteBuckets.forEach { bucket -> bucket.forEach { add(it.id) } }
        }
        for (song in exploreShuffled) {
            if (picked.size >= maxSeeds) break
            if (song.id !in tasteIds) picked.putIfAbsent(song.id, song)
        }
        for (song in exploreShuffled) {
            if (picked.size >= maxSeeds) break
            picked.putIfAbsent(song.id, song)
        }
        tasteBuckets.flatten().forEach {
            if (picked.size < maxSeeds) picked.putIfAbsent(it.id, it)
        }
        return picked.values.take(maxSeeds)
    }

    /**
     * Builds a ~35-track Personal Roam queue blending multi-signal taste extensions with ~25–30%
     * library exploration. Blank/unknown artist tags are not artist-capped, and small/single-artist
     * libraries fall back to a second pass without artist cap instead of collapsing to 1–3 songs.
     */
    fun assembleRoamQueue(
        seeds: List<Child>,
        seedRelated: List<List<Child>>,
        starred: List<Child>,
        frequent: List<Child>,
        recent: List<Child>,
        explorePool: List<Child>,
        random: Random,
        targetSize: Int = ROAM_SIZE,
        artistCap: Int = PER_ARTIST,
    ): List<Child> {
        if (targetSize <= 0) return emptyList()
        val validSeeds = seeds.filter(::isPlayable)
        val validStarred = starred.filter(::isPlayable).shuffled(random)
        val validFrequent = frequent.filter(::isPlayable).shuffled(random)
        val validRecent = recent.filter(::isPlayable).shuffled(random)
        val validExplore = explorePool.filter(::isPlayable).shuffled(random)
        val validRelated = seedRelated.map { batch -> batch.filter(::isPlayable) }

        val tasteIdSet = HashSet<String>().apply {
            validStarred.forEach { add(it.id) }
            validFrequent.forEach { add(it.id) }
            validRecent.forEach { add(it.id) }
        }
        val freshExplore = validExplore.filter { it.id !in tasteIdSet }

        val chosen = LinkedHashMap<String, Child>()
        val perArtist = HashMap<String, Int>()

        fun tryAdd(song: Child, limit: Int = targetSize, cap: Int = artistCap): Boolean {
            if (chosen.size >= limit || !isPlayable(song) || song.id in chosen) return false
            val artists = MusicDiscoveryRules.artistNames(song.artist)
            if (cap < Int.MAX_VALUE && artists.isNotEmpty() && artists.any { (perArtist[it] ?: 0) >= cap }) {
                return false
            }
            artists.forEach { perArtist[it] = (perArtist[it] ?: 0) + 1 }
            chosen[song.id] = song
            return true
        }

        // Reserve ~25–30% slots for fresh library exploration whenever fresh tracks exist.
        val desiredFresh = minOf((targetSize * 3) / 10, freshExplore.size)
        val familiarBudget = (targetSize - desiredFresh).coerceAtLeast(1)

        // 1. Lead with the chosen primary seed.
        validSeeds.firstOrNull()?.let { tryAdd(it, familiarBudget) }

        // 2. Interleave related extensions, secondary seeds, and all 3 taste signals.
        val tasteInterleaved = ArrayList<Child>()
        val signalLists = listOf(validStarred, validFrequent, validRecent).filter { it.isNotEmpty() }.shuffled(random)
        val signalIdx = IntArray(signalLists.size)
        var moreSignals = true
        while (moreSignals) {
            moreSignals = false
            for (i in signalLists.indices) {
                if (signalIdx[i] < signalLists[i].size) {
                    tasteInterleaved += signalLists[i][signalIdx[i]++]
                    moreSignals = true
                }
            }
        }

        val streams = buildList {
            validRelated.forEach { if (it.isNotEmpty()) add(ArrayDeque(it)) }
            if (validSeeds.size > 1) add(ArrayDeque(validSeeds.drop(1)))
            if (tasteInterleaved.isNotEmpty()) add(ArrayDeque(tasteInterleaved))
        }
        while (chosen.size < familiarBudget && streams.any { it.isNotEmpty() }) {
            for (stream in streams) {
                if (chosen.size >= familiarBudget) break
                while (stream.isNotEmpty()) {
                    if (tryAdd(stream.removeFirst(), familiarBudget)) break
                }
            }
        }

        // 3. Collect fresh exploration picks and weave them smoothly after the lead track.
        val familiarOrdered = chosen.values.toList()
        val freshPicked = ArrayList<Child>()
        for (song in freshExplore) {
            if (chosen.size >= targetSize) break
            if (tryAdd(song, targetSize)) {
                freshPicked += song
            }
        }

        // 4. Top up with remaining artist-capped candidates if still below targetSize.
        val allRemaining = buildList {
            validRelated.forEach(::addAll)
            addAll(tasteInterleaved)
            addAll(validExplore)
            addAll(validSeeds)
        }
        val extraPicked = ArrayList<Child>()
        for (song in allRemaining) {
            if (chosen.size >= targetSize) break
            if (tryAdd(song, targetSize)) {
                extraPicked += song
            }
        }

        // 5. Relax artist cap on a second pass so small or single-artist libraries never play 1 song.
        if (chosen.size < targetSize) {
            for (song in allRemaining) {
                if (chosen.size >= targetSize) break
                if (tryAdd(song, targetSize, cap = Int.MAX_VALUE)) {
                    extraPicked += song
                }
            }
        }

        if (familiarOrdered.isEmpty()) {
            return chosen.values.toList()
        }

        // Weave fresh exploration tracks evenly (~1 every 3 familiar tracks) while keeping lead #0.
        val result = ArrayList<Child>(chosen.size)
        val used = HashSet<String>()
        fun emit(song: Child) {
            if (used.add(song.id)) result += song
        }
        emit(familiarOrdered.first())
        val famQueue = ArrayDeque(familiarOrdered.drop(1))
        val freshQueue = ArrayDeque(freshPicked)
        while (famQueue.isNotEmpty() || freshQueue.isNotEmpty()) {
            repeat(2) {
                if (famQueue.isNotEmpty()) emit(famQueue.removeFirst())
            }
            if (freshQueue.isNotEmpty()) emit(freshQueue.removeFirst())
        }
        extraPicked.forEach(::emit)
        chosen.values.forEach(::emit)
        return result
    }

    /**
     * [related] holds the songs found for each seed, similar ones first. Songs heard in the last
     * two days ([justHeard]) are skipped and no artist gets more than [PER_ARTIST] songs.
     */
    fun assemble(
        seeds: List<Child>, related: List<List<Child>>, unheard: List<Child>, filler: List<Child>,
        justHeard: Set<String>, random: Random, recentMix: Set<String> = emptySet(),
    ): List<Child> {
        val mix = LinkedHashMap<String, Child>()
        val perArtist = HashMap<String, Int>()
        fun add(song: Child, limit: Int = SIZE, allowRecentMix: Boolean = false): Boolean {
            if (mix.size >= limit || song.isDir || song.isVideo || song.id in mix || song.id in justHeard) return false
            if (!allowRecentMix && song.id in recentMix) return false
            val artists = MusicDiscoveryRules.artistNames(song.artist)
            if (artists.isNotEmpty() && artists.any { (perArtist[it] ?: 0) >= PER_ARTIST }) return false
            artists.forEach { perArtist[it] = (perArtist[it] ?: 0) + 1 }
            mix[song.id] = song
            return true
        }
        seeds.firstOrNull { it.id !in justHeard && it.id !in recentMix }?.let { add(it) }
        val queues = related.map { ArrayDeque(it) }
        val target = SIZE - UNHEARD
        while (mix.size < target && queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> while (queue.isNotEmpty()) if (add(queue.removeFirst(), target)) break }
        }
        seeds.forEach { add(it, target) }
        unheard.forEach { add(it) }
        while (mix.size < SIZE && queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> while (queue.isNotEmpty()) if (add(queue.removeFirst(), SIZE)) break }
        }
        filler.shuffled(random).forEach { add(it) }
        if (mix.size < SIZE) {
            related.flatten().forEach { add(it, SIZE, allowRecentMix = true) }
            seeds.forEach { add(it, SIZE, allowRecentMix = true) }
            unheard.forEach { add(it, SIZE, allowRecentMix = true) }
            filler.shuffled(random).forEach { add(it, SIZE, allowRecentMix = true) }
        }
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
        fun artists(track: ChartTrack) = (track.artists.ifEmpty { listOf(track.artist) })
            .flatMap { MusicDiscoveryRules.artistAliases(it) }
        val liked = rows.filter { (_, t) -> artists(t).any { it in favourite } }
        val known = rows.filter { (_, t) -> artists(t).any { it in library } }
        return (liked + known).distinctBy { AllChartsMatcher.songKey(it.second) }.take(limit)
    }

    fun wishKey(wish: DailyWish) = MusicDiscoveryRules.normalize(wish.title) + "|" +
        MusicDiscoveryRules.normalize(wish.artists.firstOrNull())
}
