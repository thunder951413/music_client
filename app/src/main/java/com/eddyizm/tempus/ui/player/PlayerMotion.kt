package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.eddyizm.tempus.App
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Shared playback, visibility, power-save, and cover-scheme signals for the immersive player's views.
 *
 * All calls are main-thread only.
 */
object PlayerMotion {
    fun interface Listener {
        fun onPlayerMotionChanged()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile
    private var powerSaveMode: Boolean = false

    @Volatile
    private var powerObserverRegistered: Boolean = false

    @JvmStatic
    var isPlaying: Boolean = false
        private set

    @JvmStatic
    var scheme: CoverScheme = CoverScheme.FALLBACK
        private set

    /**
     * False when system animations are disabled OR Battery Saver (`isPowerSaveMode`) is active;
     * vinyl rotation and aura crossfades then stay completely still.
     */
    @JvmStatic
    val motionEnabled: Boolean
        get() {
            ensurePowerSaveObserver()
            val animatorsOn = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
            return animatorsOn && !powerSaveMode
        }

    /** True while the cover flies between mini and full player; the sleeve hides while in flight. */
    @JvmStatic
    var coverInFlight: Boolean = false
        private set

    /** True while the full-screen player sheet is expanded or actively dragging open. */
    @JvmStatic
    var sheetExpanded: Boolean = false
        private set

    /** True while the lyrics page or queue sheet is covering the vinyl artwork pager. */
    @JvmStatic
    var lyricsShowing: Boolean = false
        private set

    private fun ensurePowerSaveObserver() {
        if (powerObserverRegistered) return
        runCatching {
            val app = App.getInstance() ?: return
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            powerSaveMode = pm.isPowerSaveMode
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val next = pm.isPowerSaveMode
                    if (powerSaveMode != next) {
                        powerSaveMode = next
                        notifyListeners()
                    }
                }
            }
            app.registerReceiver(
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
            powerObserverRegistered = true
        }
    }

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
        ensurePowerSaveObserver()
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
