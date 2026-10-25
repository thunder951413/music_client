package com.eddyizm.tempus.ui.player

import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible

/**
 * Carries the cover from the mini player to the full player while the sheet is dragged.
 *
 * A single round overlay follows the drag from the mini cover to the label of the full-player
 * record, turning with [VinylClock], so the artwork grows out of the mini player instead of
 * cross-fading. The mini cover and the record label are hidden while it is in flight.
 */
class CoverMorph(private val root: View, private val flying: ImageView) {
    private val source = RectF()
    private val target = RectF()
    private var hasSource = false
    private var hiddenSource: View? = null

    init {
        flying.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        flying.clipToOutline = true
    }

    /**
     * Places the overlay for [offset] (0 = mini player, 1 = full player). A null or unmeasured
     * [targetView] (lyrics or queue showing) skips the flight. [labelFraction] is the share of the
     * target taken by the record label, where the artwork lands.
     */
    fun update(offset: Float, sourceView: View, targetView: View?, labelFraction: Float) {
        // The mini player fades out early in the drag, so its last laid-out bounds are kept.
        if (sourceView.isShown && sourceView.width > 0 && offset < 0.2f) {
            hasSource = measure(sourceView, source)
        }
        val inFlight = offset > 0.001f && offset < 0.999f && hasSource &&
                targetView != null && targetView.isAttachedToWindow && measure(targetView, target)
        if (!inFlight) {
            land()
            return
        }
        target.inset(target.width() * (1f - labelFraction) / 2f, target.height() * (1f - labelFraction) / 2f)
        val endSize = target.width().toInt().coerceAtLeast(1)
        val params = flying.layoutParams
        if (params.width != endSize || params.height != endSize) {
            flying.layoutParams = (params as FrameLayout.LayoutParams).apply {
                width = endSize
                height = endSize
            }
        }
        val size = lerp(source.width(), target.width(), offset)
        flying.pivotX = endSize / 2f
        flying.pivotY = endSize / 2f
        flying.translationX = lerp(source.centerX(), target.centerX(), offset) - endSize / 2f
        flying.translationY = lerp(source.centerY(), target.centerY(), offset) - endSize / 2f
        flying.scaleX = size / endSize
        flying.scaleY = size / endSize
        flying.rotation = VinylClock.angle()
        flying.isVisible = true
        sourceView.alpha = 0f
        hiddenSource = sourceView
        PlayerMotion.setCoverInFlight(true)
    }

    /** Ends any flight and restores both real covers. */
    fun land() {
        flying.isVisible = false
        hiddenSource?.alpha = 1f
        hiddenSource = null
        PlayerMotion.setCoverInFlight(false)
    }

    private fun measure(view: View, out: RectF): Boolean {
        if (view.width == 0 || view.height == 0) return false
        val rootLocation = IntArray(2).also { root.getLocationInWindow(it) }
        val location = IntArray(2).also { view.getLocationInWindow(it) }
        val left = (location[0] - rootLocation[0]).toFloat()
        val top = (location[1] - rootLocation[1]).toFloat()
        out.set(left, top, left + view.width, top + view.height)
        return true
    }

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction
}
