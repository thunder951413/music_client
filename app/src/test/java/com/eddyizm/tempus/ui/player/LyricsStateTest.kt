package com.eddyizm.tempus.ui.player

import com.eddyizm.tempus.ui.player.lyrics.LyricLine
import com.eddyizm.tempus.ui.player.lyrics.LyricsState
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsStateTest {
    @Test
    fun sortsLinesAndEndsEachAtTheNextStart() {
        val lines = LyricsState.buildLines(listOf(2_000L, 1_000L, 3_000L), listOf("b", "a", "c"))
        assertEquals(
            listOf(LyricLine(1_000, 2_000, "a"), LyricLine(2_000, 3_000, "b"), LyricLine(3_000, 8_000, "c")),
            lines,
        )
    }

    @Test
    fun addsOpeningInterludeBeforeALateFirstLine() {
        val lines = LyricsState.buildLines(listOf(12_000L, 15_000L), listOf("first", "second"))
        assertEquals(LyricLine(0, 12_000, ""), lines.first())
        assertEquals(3, lines.size)
    }

    @Test
    fun keepsLongInstrumentalGapsAndDropsShortBlankLines() {
        val lines = LyricsState.buildLines(
            listOf(0L, 1_000L, 2_000L, 10_000L),
            listOf("one", " ", "two", "three"),
        )
        // The blank line at 1 s lasts only 1 s, so it is dropped; "two" then runs until 10 s.
        assertEquals(listOf("one", "two", "three"), lines.map { it.text })

        val withGap = LyricsState.buildLines(listOf(0L, 1_000L, 9_000L), listOf("one", "", "two"))
        assertEquals(listOf("one", "", "two"), withGap.map { it.text })
        assertEquals(true, withGap[1].isInterlude)
    }
}
