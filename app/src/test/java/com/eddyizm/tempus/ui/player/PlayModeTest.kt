package com.eddyizm.tempus.ui.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeTest {
    @Test
    fun mapsPlayerSettingsToOneMode() {
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.of(false, Player.REPEAT_MODE_OFF))
        assertEquals(PlayMode.REPEAT_ALL, PlayMode.of(false, Player.REPEAT_MODE_ALL))
        assertEquals(PlayMode.REPEAT_ONE, PlayMode.of(true, Player.REPEAT_MODE_ONE))
        assertEquals(PlayMode.SHUFFLE, PlayMode.of(true, Player.REPEAT_MODE_OFF))
        assertEquals(PlayMode.SHUFFLE, PlayMode.of(true, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun cyclesThroughEveryModeAndBack() {
        var mode = PlayMode.REPEAT_ALL
        val seen = mutableListOf<PlayMode>()
        repeat(PlayMode.entries.size) { seen += mode; mode = mode.next() }
        assertEquals(PlayMode.entries.toList(), seen)
        assertEquals(PlayMode.REPEAT_ALL, mode)
        // Applying a mode and reading it back gives the same mode.
        PlayMode.entries.forEach { assertEquals(it, PlayMode.of(it.shuffle, it.repeat)) }
    }
}
