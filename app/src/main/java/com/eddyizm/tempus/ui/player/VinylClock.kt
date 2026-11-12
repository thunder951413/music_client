package com.eddyizm.tempus.ui.player

import android.os.SystemClock

/**
 * Shared record angle clock for the full-screen vinyl player.
 *
 * Advances ONLY while playing, animations are enabled (and Battery Saver is off), the full-screen
 * player sheet is expanded, and neither lyrics nor queue overlays are covering the artwork.
 */
object VinylClock : PlayerMotion.Listener {
    const val MS_PER_TURN = 24_000f

    private var base = 0f
    private var since = SystemClock.uptimeMillis()

    @JvmStatic
    var isRunning = false
        private set

    init {
        PlayerMotion.addListener(this)
        onPlayerMotionChanged()
    }

    override fun onPlayerMotionChanged() {
        val now = SystemClock.uptimeMillis()
        base = angle(now)
        since = now
        isRunning = PlayerMotion.isPlaying &&
            PlayerMotion.motionEnabled &&
            PlayerMotion.sheetExpanded &&
            !PlayerMotion.lyricsShowing
    }

    @JvmStatic
    @JvmOverloads
    fun angle(now: Long = SystemClock.uptimeMillis()): Float =
        if (isRunning) (base + (now - since) * 360f / MS_PER_TURN) % 360f else base
}
