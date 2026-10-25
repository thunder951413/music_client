package com.eddyizm.tempus.ui.player

import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import com.eddyizm.tempus.util.Preferences

/**
 * Carries the cover from the mini player to the full player while the sheet is dragged.
 *
 * A single overlay image follows the drag between the two covers' bounds, so the artwork appears
 * to grow out of the mini player instead of cross-fading. Both real covers are hidden while the
 * overlay is in flight and shown again once the sheet settles.
 */
class CoverMorph(private val root: View, private val flying: ImageView) {
    private val source = RectF()
    private val target = RectF()
    private var hasSource = false
    private var radius = 0f
    private var hiddenTarget: View? = null
    private var hiddenSource: View? = null

    init {
        flying.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        flying.clipToOutline = true
        flying.pivotX = 0f
        flying.pivotY = 0f
    }

    /**
     * Places the overlay for [offset] (0 = mini player, 1 = full player). A null or unmeasured
     * [targetView] (lyrics or queue showing) skips the flight and leaves both covers alone.
     */
    fun update(offset: Float, sourceView: View, targetView: View?, coverScale: Float) {
        val density = root.resources.displayMetrics.density
        // The mini player fades out early in the drag, so its last laid-out bounds are kept.
        if (sourceView.isShown && sourceView.width > 0 && offset < 0.2f) {
            hasSource = measure(sourceView, source)
        }
        val inFlight = offset > 0.001f && offset < 0.999f && hasSource &&
                targetView != null && targetView.isAttachedToWindow && measure(targetView, target)
        if (!inFlight || targetView == null) {
            land()
            return
        }
        // The paused full-size cover is drawn smaller around its centre.
        if (coverScale != 1f) {
            val inset = target.width() * (1f - coverScale) / 2f
            target.inset(inset, target.height() * (1f - coverScale) / 2f)
        }
        val width = lerp(source.width(), target.width(), offset)
        val endWidth = target.width().toInt().coerceAtLeast(1)
        val endHeight = target.height().toInt().coerceAtLeast(1)
        val params = flying.layoutParams
        if (params.width != endWidth || params.height != endHeight) {
            flying.layoutParams = (params as FrameLayout.LayoutParams).apply {
                this.width = endWidth
                this.height = endHeight
            }
        }
        val scale = width / endWidth
        val endRadius = if (Preferences.isCornerRoundingEnabled()) Preferences.getRoundedCornerSize() * density else 0f
        // The outline lives in unscaled view space, so the radius is divided by the current scale.
        radius = lerp(SOURCE_RADIUS_DP * density, endRadius, offset) / scale
        flying.invalidateOutline()
        flying.translationX = lerp(source.left, target.left, offset)
        flying.translationY = lerp(source.top, target.top, offset)
        flying.scaleX = scale
        flying.scaleY = scale
        flying.isVisible = true
        hide(sourceView, targetView)
    }

    /** Ends any flight and restores both real covers. */
    fun land() {
        flying.isVisible = false
        hiddenSource?.alpha = 1f
        hiddenTarget?.alpha = 1f
        hiddenSource = null
        hiddenTarget = null
    }

    private fun hide(sourceView: View, targetView: View) {
        if (hiddenTarget !== targetView) hiddenTarget?.alpha = 1f
        sourceView.alpha = 0f
        targetView.alpha = 0f
        hiddenSource = sourceView
        hiddenTarget = targetView
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

    companion object {
        /** Corner radius of the mini player cover, which the overlay starts from. */
        const val SOURCE_RADIUS_DP = 8f
    }
}
