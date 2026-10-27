package com.eddyizm.tempus.ui.browse

import com.eddyizm.tempus.subsonic.models.Child
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class LibraryMixesTest {
    private fun song(id: Int, title: String, artist: String, year: Int? = null, genre: String? = null, plays: Long? = null) =
        Child(id.toString(), title = title, artist = artist).apply {
            this.year = year
            this.genre = genre
            this.playCount = plays
            this.created = Date(id * 1000L)
        }

    @Test
    fun titleInKanaOrHangulBeatsTheArtist() {
        assertEquals(Language.JAPANESE, LibraryMixes.languageOf(song(1, "夜に駆ける", "YOASOBI")))
        assertEquals(Language.CHINESE, LibraryMixes.languageOf(song(2, "Mojito", "周杰伦")))
        assertEquals(Language.WESTERN, LibraryMixes.languageOf(song(3, "Hello", "Adele")))
        assertEquals(Language.KOREAN, LibraryMixes.languageOf(song(4, "좋은 날", "IU")))
    }

    @Test
    fun genresSplitOnSeparators() {
        assertEquals(listOf("Pop", "Rock", "R&B"), LibraryMixes.genresOf(song(1, "a", "b", genre = "Pop; Rock/ R&B")))
    }

    @Test
    fun smallGroupsAreDroppedAndBigOnesKept() {
        val chinese = (1..10).map { song(it, "歌$it", "歌手", year = 2015, genre = "Pop", plays = if (it <= 3) it.toLong() else 0) }
        val western = (11..13).map { song(it, "Song $it", "Band", year = 1995) }
        val kinds = LibraryMixes.plan(chinese + western).map { it.kind }
        assertTrue(LibraryMix.Kind.ByLanguage(Language.CHINESE) in kinds)
        assertFalse(LibraryMix.Kind.ByLanguage(Language.WESTERN) in kinds)
        assertTrue(LibraryMix.Kind.ByDecade(2010) in kinds)
        assertFalse(LibraryMix.Kind.ByDecade(1990) in kinds)
        assertTrue(LibraryMix.Kind.ByGenre("Pop") in kinds)
        assertTrue(LibraryMix.Kind.Newest in kinds)
        assertTrue(LibraryMix.Kind.Unplayed in kinds)
        // Only three songs were ever played.
        assertFalse(LibraryMix.Kind.MostPlayed in kinds)
    }

    @Test
    fun newestIsOrderedAndOrderIsStableBetweenRuns() {
        val songs = (1..20).map { song(it, "歌$it", "歌手") }
        val first = LibraryMixes.plan(songs)
        assertEquals(first, LibraryMixes.plan(songs.reversed().reversed()))
        val newest = first.first { it.kind == LibraryMix.Kind.Newest }
        assertEquals("20", newest.songIds.first())
    }
}
