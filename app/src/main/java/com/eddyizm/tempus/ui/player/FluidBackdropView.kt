package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Low-power Static Aura backdrop for the Editorial Warm Dark ("唱片店夜色") full-screen player.
 *
 * Draws a soft, static radial cover mesh over a `#0E0D0C` warm charcoal base with a subtle ~3%
 * static film-grain texture in AGSL. There is NO advancing time uniform and NO Choreographer loop:
 * [invalidate] runs only during the 600ms crossfade when [PlayerMotion.scheme] changes, then sits
 * completely idle at 0fps.
 */
class FluidBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PlayerMotion.Listener {

    private val shader = RuntimeShader(SHADER)
    private val paint = Paint().apply { shader = this@FluidBackdropView.shader }

    private var from = PlayerMotion.scheme.blobs.toIntArray()
    private var to = from.copyOf()
    private val current = from.copyOf()
    private var colorAnimator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        PlayerMotion.addListener(this)
        val target = PlayerMotion.scheme.blobs.toIntArray()
        if (!target.contentEquals(current)) {
            target.copyInto(from)
            target.copyInto(to)
            target.copyInto(current)
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        PlayerMotion.removeListener(this)
        colorAnimator?.cancel()
        colorAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onPlayerMotionChanged() {
        val target = PlayerMotion.scheme.blobs.toIntArray()
        if (!target.contentEquals(to)) {
            animateColorsTo(target)
        }
    }

    private fun animateColorsTo(target: IntArray) {
        colorAnimator?.cancel()
        from = current.copyOf()
        to = target.copyOf()
        if (!PlayerMotion.motionEnabled || !isAttachedToWindow || !isShown) {
            target.copyInto(current)
            invalidate()
            return
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_FADE_MS
            interpolator = FADE_INTERPOLATOR
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                for (i in current.indices) {
                    current[i] = ColorUtils.blendARGB(from[i], to[i], fraction)
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        shader.setFloatUniform("size", w, h)
        shader.setColorUniform("c0", current[0])
        shader.setColorUniform("c1", current[1])
        shader.setColorUniform("c2", current[2])
        shader.setColorUniform("c3", current[3])
        canvas.drawPaint(paint)
    }

    private companion object {
        const val COLOR_FADE_MS = 600L
        val FADE_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)

        val SHADER = """
            uniform float2 size;
            layout(color) uniform half4 c0;
            layout(color) uniform half4 c1;
            layout(color) uniform half4 c2;
            layout(color) uniform half4 c3;

            float radialGlow(float2 p, float2 center, float2 radii) {
                float2 d = (p - center) / radii;
                return exp(-dot(d, d));
            }

            half4 main(float2 coord) {
                float2 uv = coord / size;
                half3 base = half3(0.0549, 0.0510, 0.0471); // #0E0D0C warm charcoal

                // Static, organic radial cover aura concentrated behind the vinyl sleeve,
                // settling calmly into #0E0D0C around the transport and bottom capsule.
                float g0 = radialGlow(uv, float2(0.32, 0.24), float2(0.56, 0.42));
                float g1 = radialGlow(uv, float2(0.72, 0.30), float2(0.52, 0.38));
                float g2 = radialGlow(uv, float2(0.46, 0.50), float2(0.62, 0.44));
                float g3 = radialGlow(uv, float2(0.24, 0.66), float2(0.50, 0.36));

                float weightSum = g0 + g1 + g2 + g3 + 0.0001;
                half3 mesh = (c0.rgb * half(g0) + c1.rgb * half(g1) + c2.rgb * half(g2) + c3.rgb * half(g3)) / half(weightSum);

                float auraMask = clamp(max(max(g0, g1), g2 * 0.85) * 0.92, 0.0, 0.88);
                auraMask *= 1.0 - smoothstep(0.52, 0.98, uv.y) * 0.72;

                half3 color = mix(base, mesh, half(auraMask));

                // Subtle ~3% static film-grain texture (no time uniform) to eliminate gradient banding.
                float grain = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
                color += half((grain - 0.5) * 0.03);

                return half4(clamp(color, half3(0.0), half3(1.0)), 1.0);
            }
        """.trimIndent()
    }
}
