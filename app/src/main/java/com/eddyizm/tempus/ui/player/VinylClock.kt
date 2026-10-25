package com.eddyizm.tempus.ui.player

import android.os.SystemClock
import android.view.View

/**
 * One record angle shared by the mini player, the flying cover and the full-player disc, so the
 * artwork keeps its orientation when it moves between them. It turns only while playing.
 */
object VinylClock : PlayerMotion.Listener {
    private const val MS_PER_TURN = 24_000f

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
        isRunning = PlayerMotion.isPlaying && PlayerMotion.motionEnabled
    }

    @JvmStatic
    @JvmOverloads
    fun angle(now: Long = SystemClock.uptimeMillis()): Float =
        if (isRunning) (base + (now - since) * 360f / MS_PER_TURN) % 360f else base

    /** Keeps [view] round and turning with the clock while it is attached. */
    @JvmStatic
    fun spin(view: View) {
        val tick = object : Runnable {
            override fun run() {
                if (!view.isAttachedToWindow) return
                view.rotation = angle()
                when {
                    !isRunning -> Unit
                    view.isShown -> view.postOnAnimation(this)
                    // Hidden (player expanded): look again later instead of every frame.
                    else -> view.postDelayed(this, 500)
                }
            }
        }
        val listener = PlayerMotion.Listener {
            view.removeCallbacks(tick)
            view.post(tick)
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                PlayerMotion.addListener(listener)
                view.post(tick)
            }

            override fun onViewDetachedFromWindow(v: View) {
                PlayerMotion.removeListener(listener)
                view.removeCallbacks(tick)
            }
        })
        if (view.isAttachedToWindow) {
            PlayerMotion.addListener(listener)
            view.post(tick)
        }
    }
}
