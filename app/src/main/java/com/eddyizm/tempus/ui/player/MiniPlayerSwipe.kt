package com.eddyizm.tempus.ui.player

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import kotlin.math.abs

/**
 * Horizontal swipes on the mini player change track, as in Apple Music, Spotify and NetEase:
 * left for next, right for previous. The card follows the finger and slides out before the next
 * track slides in. Vertical drags are left to the bottom sheet, and a plain tap still clicks.
 */
object MiniPlayerSwipe {
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    @JvmStatic
    @SuppressLint("ClickableViewAccessibility")
    fun attach(touchTarget: View, card: View, onNext: Runnable, onPrevious: Runnable) {
        val slop = ViewConfiguration.get(touchTarget.context).scaledTouchSlop
        val flingVelocity = ViewConfiguration.get(touchTarget.context).scaledMinimumFlingVelocity * 4
        var downX = 0f
        var downY = 0f
        var dragging = false
        var tracker: VelocityTracker? = null

        touchTarget.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    card.animate().cancel()
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                        dragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        card.translationX = dx
                        card.alpha = 1f - (abs(dx) / card.width.coerceAtLeast(1)).coerceAtMost(0.6f)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val velocity = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    if (!dragging) {
                        if (abs(event.rawX - downX) < slop && abs(event.rawY - downY) < slop) view.performClick()
                    } else {
                        val dx = card.translationX
                        val far = abs(dx) > card.width * 0.3f
                        val fast = abs(velocity) > flingVelocity && velocity * dx > 0
                        if (far || fast) {
                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                            swapOut(card, toLeft = dx < 0, if (dx < 0) onNext else onPrevious)
                        } else {
                            settle(card)
                        }
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracker?.recycle()
                    tracker = null
                    if (dragging) settle(card)
                    dragging = false
                }
            }
            true
        }
    }

    private fun settle(card: View) {
        card.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(ease).start()
    }

    private fun swapOut(card: View, toLeft: Boolean, action: Runnable) {
        val width = card.width.toFloat()
        card.animate()
            .translationX(if (toLeft) -width else width)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(ease)
            .withEndAction {
                action.run()
                card.translationX = if (toLeft) width * 0.4f else -width * 0.4f
                card.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(ease).start()
            }
            .start()
    }
}
