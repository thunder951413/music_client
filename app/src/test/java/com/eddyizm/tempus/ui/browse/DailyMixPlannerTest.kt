package com.eddyizm.tempus.ui.browse

import com.eddyizm.tempus.discovery.ChartSource
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.subsonic.models.Child
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DailyMixPlannerTest {
    private fun song(id: String, artist: String = "Artist $id") = Child(id, title = "Song $id", artist = artist)

    private fun chart(id: String, title: String, artist: String) = ChartTrack().apply {
        this.id = id
        this.title = title
        this.artist = artist
        this.artists = listOf(artist)
    }

    @Test
    fun seedsTakeOneOfEachKindOfListening() {
        val recent = listOf(song("r1"), song("r2"))
        val frequent = listOf(song("f1"))
        val starred = listOf(song("s1"), song("r1"))
        val seeds = DailyMixPlanner.seeds(starred, recent, frequent, Random(1)).map { it.id }
        assertEquals(DailyMixPlanner.SEEDS, seeds.size)
        assertTrue(seeds.any { it.startsWith("r") })
        assertTrue("f1" in seeds)
        assertTrue("s1" in seeds)
    }

    @Test
    fun seedsStillComeFromThinHistory() {
        assertEquals(listOf("s1"), DailyMixPlanner.seeds(listOf(song("s1")), emptyList(), emptyList(), Random(1)).map { it.id })
        assertTrue(DailyMixPlanner.seeds(emptyList(), emptyList(), emptyList(), Random(1)).isEmpty())
    }

    @Test
    fun assembleLimitsArtistsSkipsJustHeardAndLeavesRoomForUnheard() {
        val seeds = listOf(song("seed", "Same"))
        val related = listOf((1..10).map { song("same$it", "Same") } + (1..40).map { song("other$it") })
        val unheard = (1..5).map { song("new$it") }
        val mix = DailyMixPlanner.assemble(seeds, related, unheard, emptyList(), setOf("other1"), Random(1))
        assertEquals(DailyMixPlanner.SIZE, mix.size)
        assertEquals(DailyMixPlanner.PER_ARTIST, mix.count { it.artist == "Same" })
        assertFalse(mix.any { it.id == "other1" })
        assertTrue(unheard.all { it in mix })
        assertEquals(mix.size, mix.map { it.id }.toSet().size)
    }

    @Test
    fun assembleTopsUpFromFillerWhenRelatedIsEmpty() {
        val filler = (1..10).map { song("fav$it") }
        val mix = DailyMixPlanner.assemble(listOf(song("seed")), listOf(emptyList()), emptyList(), filler, emptySet(), Random(1))
        assertEquals(11, mix.size)
    }

    @Test
    fun wishesPreferLikedArtistsAndSkipPendingAndDuplicates() {
        val charts = listOf(
            ChartSource.QQ_HOT to listOf(chart("a1", "Unknown", "Stranger"), chart("a2", "Known", "Library Band"), chart("a3", "Liked", "Fav")),
            ChartSource.NETEASE_HOT to listOf(chart("9", "Liked", "Fav"), chart("10", "Waiting", "Fav")),
        )
        val pending = setOf(DailyMixPlanner.wishKey(DailyWish("NETEASE", "10", "Waiting", listOf("Fav"), null)))
        val picks = DailyMixPlanner.wishCandidates(charts, setOf("fav"), setOf("libraryband", "fav"), pending, 10)
        assertEquals(listOf("a3", "a2"), picks.map { it.second.id })
        assertEquals(ChartSource.Provider.QQ, picks.first().first)
    }

    @Test
    fun bindingRoundTripsAndRejectsOtherComments() {
        val binding = DailyBinding(date = "2026-10-28", device = "phone-1", missing = listOf(DailyWish("QQ", "Ab1", "Song", listOf("A"), 200)))
        assertEquals(binding, DailyBinding.read(binding.comment()))
        assertNull(DailyBinding.read(DailyBinding.PREFIX + "{\"version\":1,\"date\":\"2026-10-28\",\"missing\":[]}"))
        assertNull(DailyBinding.read("Tempus external playlist v1\n{}"))
        assertNull(DailyBinding.read(DailyBinding.PREFIX + "{\"version\":1,\"date\":\"tomorrow\"}"))
    }

    @Test
    fun assembleExcludesRecentMixUnlessLibraryIsTinyAndCapsBulletCollaborations() {
        val seeds = listOf(song("seed", "Main"))
        val related = listOf((1..40).map { song("rel$it", "Artist $it") })
        val recentMix = (1..10).map { "rel$it" }.toSet()
        val mix = DailyMixPlanner.assemble(seeds, related, emptyList(), emptyList(), emptySet(), Random(2), recentMix)
        assertEquals(DailyMixPlanner.SIZE, mix.size)
        assertTrue(mix.none { it.id in recentMix })

        val tiny = (1..5).map { song("tiny$it") }
        val fallback = DailyMixPlanner.assemble(emptyList(), listOf(tiny), emptyList(), emptyList(), emptySet(), Random(2), tiny.map { it.id }.toSet())
        assertEquals(5, fallback.size)

        val collabs = (1..20).map { song("c$it", "Shared • Guest$it") }
        val capped = DailyMixPlanner.assemble(emptyList(), listOf(collabs), emptyList(), emptyList(), emptySet(), Random(2))
        assertEquals(DailyMixPlanner.PER_ARTIST, capped.size)
    }

    @Test
    fun roamSeedsRotateAcrossSignalsInsteadOfLockingToSingleStarredSong() {
        val starred = listOf(song("s1", "FavArtist"))
        val frequent = (1..5).map { song("f$it", "FreqArtist $it") }
        val recent = (1..5).map { song("r$it", "RecentArtist $it") }
        val explore = (1..20).map { song("e$it", "ExploreArtist $it") }

        val firstLeads = (1..24).map { seed ->
            val picks = DailyMixPlanner.roamSeeds(starred, frequent, recent, explore, Random(seed))
            assertEquals(DailyMixPlanner.ROAM_SEEDS, picks.size)
            assertTrue(picks.any { it.id == "s1" })
            assertTrue(picks.any { it.id.startsWith("f") })
            assertTrue(picks.any { it.id.startsWith("r") })
            assertTrue(picks.any { it.id.startsWith("e") })
            picks.first().id
        }.toSet()

        // Primary seed must rotate across signals rather than always starting on the 1 starred song.
        assertTrue(firstLeads.size >= 2)
        assertTrue(firstLeads.any { it != "s1" })
    }

    @Test
    fun assembleRoamQueueBuildsFull35QueueWithOneStarredEmptySimilarAndWeavesExploration() {
        val starred = listOf(song("s1", "StarArtist"))
        val frequent = (1..8).map { song("f$it", "FreqArtist $it") }
        val recent = (1..8).map { song("r$it", "RecentArtist $it") }
        val fresh = (1..30).map { song("x$it", "ExploreArtist $it") }
        val pool = starred + frequent + recent + fresh
        val seeds = DailyMixPlanner.roamSeeds(starred, frequent, recent, pool, Random(7))

        val queue = DailyMixPlanner.assembleRoamQueue(
            seeds = seeds,
            seedRelated = seeds.map { emptyList() },
            starred = starred,
            frequent = frequent,
            recent = recent,
            explorePool = pool,
            random = Random(7),
        )

        assertEquals(DailyMixPlanner.ROAM_SIZE, queue.size)
        assertEquals(queue.size, queue.map { it.id }.toSet().size)
        assertEquals(seeds.first().id, queue.first().id)
        val freshCount = queue.count { it.id.startsWith("x") }
        assertTrue("Expected ~25-30% fresh exploration tracks, got $freshCount", freshCount >= 10)
    }

    @Test
    fun assembleRoamQueueDoesNotCapBlankArtistsAndRelaxesSingleArtistCap() {
        val blankArtists = (1..40).map { Child("b$it", title = "Untitled $it", artist = "") }
        val blankQueue = DailyMixPlanner.assembleRoamQueue(
            seeds = listOf(blankArtists.first()),
            seedRelated = listOf(emptyList()),
            starred = listOf(blankArtists.first()),
            frequent = emptyList(),
            recent = emptyList(),
            explorePool = blankArtists,
            random = Random(3),
        )
        assertEquals(DailyMixPlanner.ROAM_SIZE, blankQueue.size)

        val singleArtist = (1..18).map { song("solo$it", "OnlyArtist") }
        val soloQueue = DailyMixPlanner.assembleRoamQueue(
            seeds = listOf(singleArtist.first()),
            seedRelated = listOf(emptyList()),
            starred = listOf(singleArtist.first()),
            frequent = emptyList(),
            recent = emptyList(),
            explorePool = singleArtist,
            random = Random(4),
        )
        assertEquals(18, soloQueue.size)
    }
}
