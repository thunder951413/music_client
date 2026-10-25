package com.eddyizm.tempus.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSchemeTest {
    private fun lightness(color: Int) = CoverScheme.hsl(color)[2]

    @Test
    fun hslRoundTripsThroughTone() {
        val color = 0xFF3A7BD5.toInt()
        val hsl = CoverScheme.hsl(color)
        assertEquals(color, CoverScheme.tone(hsl[0], hsl[1], hsl[2]))
    }

    @Test
    fun singleColourStillProducesFourDistinctDarkBlobs() {
        val scheme = CoverScheme.fromColors(listOf(0xFFE0B040.toInt()))
        assertEquals(4, scheme.blobs.size)
        assertEquals(4, scheme.blobs.toSet().size)
        scheme.blobs.forEach { assertTrue("blob too light for white text", lightness(it) <= 0.40f + 1e-3f) }
    }

    @Test
    fun brightCoverKeepsLegibleBackdrop() {
        val scheme = CoverScheme.fromColors(listOf(0xFFFFFFFF.toInt(), 0xFFFFF0F0.toInt(), 0xFFF0F0FF.toInt()))
        scheme.blobs.forEach { assertTrue(lightness(it) in 0.13f - 1e-3f..0.40f + 1e-3f) }
        assertTrue(lightness(scheme.miniDark) < 0.25f)
        assertTrue(lightness(scheme.miniLight) > 0.85f)
    }

    @Test
    fun similarColoursAreNotPickedTwice() {
        val red = 0xFFC02020.toInt()
        val nearRed = 0xFFC42420.toInt()
        val blue = 0xFF2040C0.toInt()
        val scheme = CoverScheme.fromColors(listOf(red, nearRed, blue))
        val hues = scheme.blobs.map { CoverScheme.hsl(it)[0] }
        assertTrue(hues.any { it in 200f..250f })
    }
}
