package com.eddyizm.tempus.ui.player

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Shared record angle for the full-screen vinyl player.
 *
 * Rotation runs ONLY while playing, animations are enabled, the full-screen player sheet is
 * expanded, and lyrics are not covering the artwork. When any condition turns false, rotation
 * halts immediately with 0 frame callbacks.
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

    /**
     * Rotates [view] using hardware-backed [View.ROTATION] property animation while [isRunning]
     * and [view] is attached & visible, stopping immediately with 0 callbacks otherwise.
     */
    @JvmStatic
    fun spin(view: View) {
        var animator: ObjectAnimator? = null

        fun sync() {
            val shouldSpin = isRunning && view.isAttachedToWindow && view.isShown
            if (shouldSpin) {
                if (animator?.isRunning == true) return
                val startAngle = angle()
                view.rotation = startAngle
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                animator = ObjectAnimator.ofFloat(view, View.ROTATION, startAngle, startAngle + 360f).apply {
                    duration = MS_PER_TURN.toLong()
                    interpolator = LinearInterpolator()
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.RESTART
                    start()
                }
            } else {
                if (animator != null) {
                    animator?.cancel()
                    animator = null
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
                view.rotation = angle()
            }
        }

        val motionListener = PlayerMotion.Listener { sync() }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> sync() }

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                PlayerMotion.addListener(motionListener)
                v.addOnLayoutChangeListener(layoutListener)
                sync()
            }

            override fun onViewDetachedFromWindow(v: View) {
                PlayerMotion.removeListener(motionListener)
                v.removeOnLayoutChangeListener(layoutListener)
                animator?.cancel()
                animator = null
                v.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        })

        if (view.isAttachedToWindow) {
            PlayerMotion.addListener(motionListener)
            view.addOnLayoutChangeListener(layoutListener)
            sync()
        }
    }
}
