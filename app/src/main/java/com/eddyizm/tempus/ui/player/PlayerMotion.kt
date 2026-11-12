package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Shared playback, visibility, and cover-scheme signals for the immersive player's views.
 *
 * All calls are main-thread only.
 */
object PlayerMotion {
    fun interface Listener {
        fun onPlayerMotionChanged()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    @JvmStatic
    var isPlaying: Boolean = false
        private set

    @JvmStatic
    var scheme: CoverScheme = CoverScheme.FALLBACK
        private set

    /** False when the system "remove animations" setting is on; animated views then stay still. */
    @JvmStatic
    val motionEnabled: Boolean
        get() = ValueAnimator.areAnimatorsEnabled()

    /** True while the cover flies between mini and full player; the disc then hides its label. */
    @JvmStatic
    var coverInFlight: Boolean = false
        private set

    /** True while the full-screen player sheet is expanded or actively dragging open. */
    @JvmStatic
    var sheetExpanded: Boolean = false
        private set

    /** True while the lyrics page is covering the vinyl artwork pager. */
    @JvmStatic
    var lyricsShowing: Boolean = false
        private set

    @JvmStatic
    fun setCoverInFlight(flying: Boolean) {
        if (coverInFlight == flying) return
        coverInFlight = flying
        notifyListeners()
    }

    @JvmStatic
    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        notifyListeners()
    }

    @JvmStatic
    fun setSheetExpanded(expanded: Boolean) {
        if (sheetExpanded == expanded) return
        sheetExpanded = expanded
        notifyListeners()
    }

    @JvmStatic
    fun setLyricsShowing(showing: Boolean) {
        if (lyricsShowing == showing) return
        lyricsShowing = showing
        notifyListeners()
    }

    @JvmStatic
    fun setScheme(next: CoverScheme) {
        if (scheme == next) return
        scheme = next
        notifyListeners()
    }

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onPlayerMotionChanged() }
    }
}
