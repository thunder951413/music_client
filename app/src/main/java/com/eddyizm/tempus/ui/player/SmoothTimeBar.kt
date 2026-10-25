package com.eddyizm.tempus.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import com.eddyizm.tempus.R
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A quiet progress bar for Media3's control view, in the style of Apple Music.
 *
 * At rest it is a thin rounded bar without a thumb; touching it thickens the bar for scrubbing.
 * Between Media3's position updates the played part advances by elapsed time, so it glides
 * instead of stepping once a second.
 */
@UnstableApi
class SmoothTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), TimeBar, PlayerMotion.Listener {

    private val density = resources.displayMetrics.density
    private val listeners = CopyOnWriteArraySet<TimeBar.OnScrubListener>()

    private val foreground = ContextCompat.getColor(context, R.color.player_foreground)
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foreground }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.player_track)
    }
    private val bufferedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(foreground, 0x4D) }
    private val trackPath = Path()

    private var duration = 0L
    private var position = 0L
    private var positionStamp = 0L
    private var buffered = 0L
    private var keyTimeIncrement = C.TIME_UNSET
    private var keyCountIncrement = DEFAULT_KEY_COUNT

    private var scrubbing = false
    private var scrubPosition = 0L
    private var barHeight = BAR_DP * density
    private var lastFrameNanos = 0L

    init {
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PlayerMotion.addListener(this)
    }

    override fun onDetachedFromWindow() {
        PlayerMotion.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onPlayerMotionChanged() {
        // Restart the extrapolation from the last reported position on every play/pause change.
        positionStamp = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (HEIGHT_DP * density).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    // region TimeBar
    override fun addListener(listener: TimeBar.OnScrubListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        listeners.remove(listener)
    }

    override fun setKeyTimeIncrement(time: Long) {
        keyTimeIncrement = time
        keyCountIncrement = C.INDEX_UNSET
    }

    override fun setKeyCountIncrement(count: Int) {
        keyCountIncrement = count
        keyTimeIncrement = C.TIME_UNSET
    }

    override fun setPosition(position: Long) {
        if (this.position == position) return
        this.position = position
        positionStamp = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        if (buffered == bufferedPosition) return
        buffered = bufferedPosition
        invalidate()
    }

    override fun setDuration(duration: Long) {
        if (this.duration == duration) return
        this.duration = duration
        if (scrubbing && duration == C.TIME_UNSET) stopScrubbing(true)
        invalidate()
    }

    override fun getPreferredUpdateDelay(): Long {
        val widthDp = (trackWidth() / density).toLong()
        return if (widthDp <= 0 || duration <= 0 || duration == C.TIME_UNSET) Long.MAX_VALUE else duration / widthDp
    }

    override fun setAdGroupTimesMs(adGroupTimesMs: LongArray?, playedAdGroups: BooleanArray?, adGroupCount: Int) = Unit

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (scrubbing && !enabled) stopScrubbing(true)
        invalidate()
    }
    // endregion

    private fun trackStart() = paddingLeft.toFloat()
    private fun trackEnd() = (width - paddingRight).toFloat()
    private fun trackWidth() = (trackEnd() - trackStart()).coerceAtLeast(0f)

    private fun displayedPosition(): Long {
        if (scrubbing) return scrubPosition
        if (!PlayerMotion.isPlaying || duration <= 0) return position
        // Glide between updates, but never run far ahead of a stalled or buffering player.
        val elapsed = (SystemClock.uptimeMillis() - positionStamp).coerceIn(0L, MAX_EXTRAPOLATION_MS)
        return (position + elapsed).coerceAtMost(duration)
    }

    override fun onDraw(canvas: Canvas) {
        val start = trackStart()
        val end = trackEnd()
        val width = end - start
        if (width <= 0f) return
        val cy = height / 2f
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = now

        val targetHeight = (if (scrubbing) BAR_ACTIVE_DP else BAR_DP) * density
        barHeight = if (PlayerMotion.motionEnabled) {
            barHeight + (targetHeight - barHeight) * (dt * 16f).coerceAtMost(1f)
        } else {
            targetHeight
        }
        val top = cy - barHeight / 2f
        val bottom = cy + barHeight / 2f
        val fraction = if (duration > 0) (displayedPosition().toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

        trackPath.rewind()
        trackPath.addRoundRect(start, top, end, bottom, barHeight / 2f, barHeight / 2f, Path.Direction.CW)
        canvas.withClip(trackPath) {
            drawRect(start, top, end, bottom, trackPaint)
            drawRect(start, top, start + width * bufferedFraction, bottom, bufferedPaint)
            drawRect(start, top, start + width * fraction, bottom, playedPaint)
        }

        val settling = kotlin.math.abs(targetHeight - barHeight) > 0.05f
        if ((settling || (PlayerMotion.isPlaying && !scrubbing)) && isShown) {
            postInvalidateOnAnimation()
        } else {
            lastFrameNanos = 0L
        }
    }

    // region Touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || duration <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                scrubbing = true
                scrubPosition = positionAt(event.x)
                performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                listeners.forEach { it.onScrubStart(this, scrubPosition) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (scrubbing) {
                scrubPosition = positionAt(event.x)
                listeners.forEach { it.onScrubMove(this, scrubPosition) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (scrubbing) {
                scrubPosition = positionAt(event.x)
                performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                stopScrubbing(false)
            }
            MotionEvent.ACTION_CANCEL -> if (scrubbing) stopScrubbing(true)
        }
        return true
    }

    private fun stopScrubbing(canceled: Boolean) {
        scrubbing = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (!canceled) {
            position = scrubPosition
            positionStamp = SystemClock.uptimeMillis()
        }
        listeners.forEach { it.onScrubStop(this, scrubPosition, canceled) }
        invalidate()
    }

    private fun positionAt(x: Float): Long {
        val width = trackWidth()
        if (width <= 0f) return 0L
        val fraction = ((x - trackStart()) / width).coerceIn(0f, 1f)
        return (fraction * duration).toLong()
    }
    // endregion

    // region Accessibility
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        if (duration <= 0) return
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, duration.toFloat(), position.toFloat(),
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (super.performAccessibilityAction(action, arguments)) return true
        if (duration <= 0) return false
        val increment = if (keyTimeIncrement != C.TIME_UNSET) keyTimeIncrement else duration / keyCountIncrement.coerceAtLeast(1)
        val delta = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> increment
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -increment
            else -> return false
        }
        scrubPosition = (position + delta).coerceIn(0L, duration)
        listeners.forEach { it.onScrubStart(this, scrubPosition) }
        stopScrubbing(false)
        return true
    }
    // endregion

    private companion object {
        const val HEIGHT_DP = 28f
        const val BAR_DP = 5f
        const val BAR_ACTIVE_DP = 11f
        const val MAX_EXTRAPOLATION_MS = 1500L
        const val DEFAULT_KEY_COUNT = 20
    }
}
