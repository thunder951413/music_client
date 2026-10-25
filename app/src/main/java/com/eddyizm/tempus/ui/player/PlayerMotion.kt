package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Shared playback and cover-scheme signals for the immersive player's animated views.
 *
 * The play button, the wavy time bar and the fluid backdrop live in different fragments; they
 * subscribe here instead of each binding its own media controller. All calls are main-thread only.
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

    @JvmStatic
    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
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
