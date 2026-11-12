package com.eddyizm.tempus.ui.player

import android.graphics.Outline
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import kotlin.math.min

/**
 * Carries the upright album sleeve from the mini player (`10dp` radius) to the full-player
 * record sleeve (`16dp` radius) while the player bottom sheet is dragged.
 */
class CoverMorph(private val root: View, private val flying: ImageView) {
    private val source = RectF()
    private val target = RectF()
    private val sleeveRect = RectF()
    private var hasSource = false
    private var hiddenSource: View? = null
    private var hideToken = 0
    private var currentRadiusPx = 0f

    init {
        flying.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, currentRadiusPx)
            }
        }
        flying.clipToOutline = true
    }

    /**
     * Places the upright rounded-square sleeve overlay for [offset] (0 = mini player, 1 = full player).
     * [labelFraction] represents the sleeve-to-unit width ratio (`1 / 1.34` ≈ `0.746f`) for the
     * Editorial Record Sleeve + Peeking Vinyl composition.
     */
    fun update(offset: Float, sourceView: View, targetView: View?, labelFraction: Float) {
        if (sourceView.isShown && sourceView.width > 0 && offset < 0.2f) {
            hasSource = measure(sourceView, source)
        }
        val inFlight = offset > 0.001f && offset < 0.999f && hasSource &&
            targetView != null && targetView.isAttachedToWindow && measure(targetView, target)
        if (!inFlight) {
            land()
            return
        }

        // Match NowPlayingArtworkPager's centered (sleeve + 1/3 peeking vinyl) geometry:
        // total unit width = sleeveSize / labelFraction, centered in targetView; sleeve sits on left.
        val sleeveSize = min(target.width() * labelFraction, target.height() * 0.94f)
        val unitWidth = sleeveSize / labelFraction.coerceAtLeast(0.5f)
        val unitLeft = target.centerX() - unitWidth / 2f
        val sleeveTop = target.centerY() - sleeveSize / 2f
        sleeveRect.set(unitLeft, sleeveTop, unitLeft + sleeveSize, sleeveTop + sleeveSize)

        val endSize = sleeveSize.toInt().coerceAtLeast(1)
        val params = flying.layoutParams
        if (params.width != endSize || params.height != endSize) {
            flying.layoutParams = (params as FrameLayout.LayoutParams).apply {
                width = endSize
                height = endSize
            }
            val spec = View.MeasureSpec.makeMeasureSpec(endSize, View.MeasureSpec.EXACTLY)
            flying.measure(spec, spec)
            flying.layout(0, 0, endSize, endSize)
        }

        hideToken++
        val density = root.resources.displayMetrics.density
        val size = lerp(source.width(), sleeveRect.width(), offset)
        val scale = (size / endSize).coerceAtLeast(0.01f)
        val desiredScreenRadius = lerp(10f * density, 16f * density, offset)
        currentRadiusPx = desiredScreenRadius / scale
        flying.invalidateOutline()

        flying.pivotX = endSize / 2f
        flying.pivotY = endSize / 2f
        flying.translationX = lerp(source.centerX(), sleeveRect.centerX(), offset) - endSize / 2f
        flying.translationY = lerp(source.centerY(), sleeveRect.centerY(), offset) - endSize / 2f
        flying.scaleX = scale
        flying.scaleY = scale
        flying.rotation = 0f
        flying.isVisible = true
        sourceView.alpha = 0f
        hiddenSource = sourceView
        PlayerMotion.setCoverInFlight(true)
    }

    /** Ends any flight and restores both real covers. */
    fun land() {
        hiddenSource?.alpha = 1f
        hiddenSource = null
        PlayerMotion.setCoverInFlight(false)
        if (!flying.isVisible) return
        val token = ++hideToken
        flying.postOnAnimation {
            flying.postOnAnimation { if (token == hideToken) flying.isVisible = false }
        }
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
