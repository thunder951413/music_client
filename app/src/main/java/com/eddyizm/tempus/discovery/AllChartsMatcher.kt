package com.eddyizm.tempus.discovery

import com.eddyizm.tempus.subsonic.models.Child
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Matches every public chart against the library and gathers the songs it lacks, so they can be
 * exported in one go. The charts overlap a lot, so each distinct title is searched only once, and
 * charts already matched this session are reused.
 */
class AllChartsMatcher(private val repository: DiscoveryRepository = DiscoveryRepository()) {
    /** [phase] 0 while fetching charts, 1 while searching the library. */
    data class Progress(val phase: Int, val done: Int, val total: Int)

    data class Result(val missing: List<ChartTrack>, val charts: Int, val failedCharts: Int, val failedSearches: Int)

    fun cancel() = repository.cancel()

    suspend fun run(sources: List<ChartSource>, onProgress: (Progress) -> Unit): Result = withContext(Dispatchers.IO) {
        val charts = ArrayList<Pair<ChartSource, ChartParser.Result>>()
        var failedCharts = 0
        sources.forEachIndexed { index, source ->
            onProgress(Progress(0, index, sources.size))
            ensureActive()
            runCatching { repository.chart(source, false) }
                .onSuccess { charts += source to it }
                .onFailure { failedCharts++ }
        }

        // Rows already matched this session, and the distinct titles still to search.
        val known = HashMap<ChartSource, List<ChartTrack>>()
        val queries = LinkedHashMap<String, String>()
        for ((source, chart) in charts) {
            val rows = ChartMatches.get(source, chart.updated)
            if (rows != null && rows.size == chart.tracks.size) {
                known[source] = rows
            } else {
                chart.tracks.forEach { queries.putIfAbsent(MusicDiscoveryRules.normalize(it.title), it.title) }
            }
        }

        val candidates = ConcurrentHashMap<String, List<Child>>()
        val failed = ConcurrentHashMap.newKeySet<String>()
        val searched = AtomicInteger()
        onProgress(Progress(1, 0, queries.size))
        val io = Dispatchers.IO.limitedParallelism(SEARCH_PARALLELISM)
        coroutineScope {
            queries.map { (key, title) ->
                async(io) {
                    ensureActive()
                    runCatching { repository.search(title) }
                        .onSuccess { candidates[key] = it }
                        .onFailure { failed += key }
                    onProgress(Progress(1, searched.incrementAndGet(), queries.size))
                }
            }.awaitAll()
        }

        val missing = ArrayList<ChartTrack>()
        val seen = HashSet<String>()
        for ((source, chart) in charts) {
            val rows = known[source] ?: chart.tracks.map { track ->
                val key = MusicDiscoveryRules.normalize(track.title)
                val found = candidates[key].orEmpty()
                track.matched(MusicDiscoveryRules.matches(track, found), found).also { it.matchFailed = key in failed }
            }.also { rows -> if (rows.none { it.matchFailed }) ChartMatches.put(source, chart.updated, rows) }
            // The same song on several charts is listed once, at its first appearance.
            for (row in ChartMatches.missing(rows)) {
                if (seen.add(songKey(row))) missing += row
            }
        }
        Result(missing, charts.size, failedCharts, failed.size)
    }

    companion object {
        private const val SEARCH_PARALLELISM = 4

        @JvmStatic
        fun songKey(track: ChartTrack): String {
            val artist = track.artists.firstOrNull() ?: track.artist
            return MusicDiscoveryRules.normalize(track.title) + "|" + MusicDiscoveryRules.normalize(artist)
        }
    }
}
