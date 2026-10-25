package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils

/**
 * A slowly flowing mesh of the current cover's colours, drawn with an AGSL shader.
 *
 * Four soft colour fields drift on independent orbits through a domain-warped space, similar to
 * the animated artwork background of Apple Music. Time only advances while the view is shown, and
 * it slows down while playback is paused. With system animations off the field stays still.
 */
class FluidBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PlayerMotion.Listener, Choreographer.FrameCallback {

    private val shader = RuntimeShader(SHADER)
    private val paint = Paint().apply { shader = this@FluidBackdropView.shader }

    private var from = PlayerMotion.scheme.blobs.toIntArray()
    private var to = from
    private val current = from.copyOf()
    private var colorAnimator: ValueAnimator? = null

    private var time = 0f
    private var speed = 0f
    private var lastFrameNanos = 0L
    private var frameScheduled = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PlayerMotion.addListener(this)
        onPlayerMotionChanged()
    }

    override fun onDetachedFromWindow() {
        PlayerMotion.removeListener(this)
        colorAnimator?.cancel()
        stopFrames()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) scheduleFrame() else stopFrames()
    }

    override fun onPlayerMotionChanged() {
        val target = PlayerMotion.scheme.blobs.toIntArray()
        if (!target.contentEquals(to)) animateColorsTo(target)
        scheduleFrame()
    }

    private fun animateColorsTo(target: IntArray) {
        colorAnimator?.cancel()
        from = current.copyOf()
        to = target
        if (!PlayerMotion.motionEnabled || !isAttachedToWindow) {
            target.copyInto(current)
            invalidate()
            return
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_FADE_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                for (i in current.indices) current[i] = ColorUtils.blendARGB(from[i], to[i], fraction)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("time", time)
        shader.setColorUniform("c0", current[0])
        shader.setColorUniform("c1", current[1])
        shader.setColorUniform("c2", current[2])
        shader.setColorUniform("c3", current[3])
        canvas.drawPaint(paint)
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!isAttachedToWindow || !isShown) return
        val target = if (PlayerMotion.isPlaying) PLAYING_SPEED else PAUSED_SPEED
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = frameTimeNanos
        // Ease the speed so pausing settles the motion instead of freezing it.
        speed += (target - speed) * (dt * 1.5f).coerceAtMost(1f)
        time += dt * speed
        invalidate()
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (frameScheduled || !isAttachedToWindow || !isShown) return
        if (!PlayerMotion.motionEnabled) {
            invalidate()
            return
        }
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopFrames() {
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
        lastFrameNanos = 0L
    }

    private companion object {
        const val COLOR_FADE_MS = 900L
        const val PLAYING_SPEED = 1f
        const val PAUSED_SPEED = 0.25f

        val SHADER = """
            uniform float2 size;
            uniform float time;
            layout(color) uniform half4 c0;
            layout(color) uniform half4 c1;
            layout(color) uniform half4 c2;
            layout(color) uniform half4 c3;

            float field(float2 p, float2 center, float radius) {
                float2 d = p - center;
                return exp(-dot(d, d) / (radius * radius));
            }

            half4 main(float2 coord) {
                float2 uv = coord / size;
                float aspect = size.x / size.y;
                float2 p = float2(uv.x * aspect, uv.y);
                float t = time * 0.11;

                // Two passes of domain warping turn round blobs into slow, folding currents.
                p += 0.10 * float2(sin(p.y * 3.1 + t * 1.7), cos(p.x * 2.6 - t * 1.3));
                p += 0.06 * float2(sin(p.y * 5.3 - t * 2.3), cos(p.x * 4.7 + t * 1.9));

                float2 a = float2(aspect * (0.25 + 0.20 * sin(t * 0.9)), 0.22 + 0.16 * cos(t * 1.1));
                float2 b = float2(aspect * (0.78 + 0.18 * cos(t * 0.7)), 0.30 + 0.22 * sin(t * 0.8 + 1.7));
                float2 c = float2(aspect * (0.30 + 0.22 * cos(t * 0.6 + 2.1)), 0.74 + 0.16 * sin(t * 1.0));
                float2 d = float2(aspect * (0.72 + 0.20 * sin(t * 0.5 + 4.0)), 0.82 + 0.14 * cos(t * 0.9 + 0.6));

                float wa = field(p, a, 0.55);
                float wb = field(p, b, 0.50);
                float wc = field(p, c, 0.60);
                float wd = field(p, d, 0.48);
                float total = wa + wb + wc + wd + 0.0001;
                half3 color = (c0.rgb * wa + c1.rgb * wb + c2.rgb * wc + c3.rgb * wd) / total;

                // Darken towards the controls at the bottom so labels keep their contrast.
                color *= half(mix(1.0, 0.70, smoothstep(0.45, 1.0, uv.y)));

                // Fine grain hides banding in the very smooth gradients.
                float grain = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
                color += half((grain - 0.5) / 160.0);
                return half4(color, 1.0);
            }
        """.trimIndent()
    }
}
