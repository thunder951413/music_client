package com.eddyizm.tempus.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import java.util.Random

/**
 * True low-power Static Aura backdrop for the Editorial Warm Dark ("唱片店夜色") full-screen player.
 *
 * - **Lazy AGSL**: [RuntimeShader] is compiled lazily on first draw rather than during XML inflate.
 * - **1/4-Resolution Aura Cache**: Radial cover mesh is rendered into a 1/4-width × 1/4-height
 *   offscreen [Bitmap] ONLY when colors change (during the 600ms crossfade or immediate snap) or
 *   view bounds change. Once settled, window redraws triggered by progress bars, lyrics, or vinyl
 *   blit the cached bitmap with **0 AGSL shader invocations**.
 * - **Tiled 64×64 Static Film Grain**: Uses a reusable 64×64 [BitmapShader] (`REPEAT`) instead of
 *   per-pixel `sin` trig in AGSL, avoiding Mali FP16 precision artifacts and GPU ALU work.
 */
class FluidBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PlayerMotion.Listener {

    private var auraShader: RuntimeShader? = null
    private val shaderPaint = Paint()
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val grainPaint = Paint().apply { shader = sharedGrainShader }
    private val dstRect = RectF()

    private var cachedAuraBitmap: Bitmap? = null
    private var cachedAuraCanvas: Canvas? = null
    private var cacheDirty: Boolean = true

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
            cacheDirty = true
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        PlayerMotion.removeListener(this)
        colorAnimator?.cancel()
        colorAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        dstRect.set(0f, 0f, w.toFloat(), h.toFloat())
        cacheDirty = true
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
            cacheDirty = true
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
                cacheDirty = true
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    to.copyInto(current)
                    cacheDirty = true
                    invalidate()
                }
            })
            start()
        }
    }

    private fun ensureCachedAura(viewWidth: Int, viewHeight: Int): Bitmap? {
        if (viewWidth <= 0 || viewHeight <= 0) return null
        val targetW = (viewWidth / CACHE_DIVISOR).coerceAtLeast(16)
        val targetH = (viewHeight / CACHE_DIVISOR).coerceAtLeast(16)

        var bitmap = cachedAuraBitmap
        if (bitmap == null || bitmap.width != targetW || bitmap.height != targetH) {
            bitmap?.recycle()
            bitmap = createBitmap(targetW, targetH)
            cachedAuraBitmap = bitmap
            cachedAuraCanvas = Canvas(bitmap)
            cacheDirty = true
        }

        if (cacheDirty) {
            val shader = auraShader ?: RuntimeShader(SHADER).also {
                auraShader = it
                shaderPaint.shader = it
            }
            shader.setFloatUniform("size", targetW.toFloat(), targetH.toFloat())
            shader.setColorUniform("c0", current[0])
            shader.setColorUniform("c1", current[1])
            shader.setColorUniform("c2", current[2])
            shader.setColorUniform("c3", current[3])
            cachedAuraCanvas?.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), shaderPaint)
            cacheDirty = false
        }
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        dstRect.set(0f, 0f, w.toFloat(), h.toFloat())

        val auraBitmap = ensureCachedAura(w, h)
        if (auraBitmap != null) {
            canvas.drawBitmap(auraBitmap, null, dstRect, blitPaint)
        } else {
            canvas.drawColor(BASE_COLOR)
        }
        // Subtle ~3% tiled 64×64 static film grain over the smooth bilinear-upscaled aura
        canvas.drawRect(dstRect, grainPaint)
    }

    private companion object {
        const val COLOR_FADE_MS = 600L
        const val CACHE_DIVISOR = 4
        const val GRAIN_TILE_PX = 64
        const val BASE_COLOR = 0xFF0E0D0C.toInt()
        val FADE_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)

        val sharedGrainShader: BitmapShader by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val pixels = IntArray(GRAIN_TILE_PX * GRAIN_TILE_PX)
            val rng = Random(0x54454D50L)
            for (i in pixels.indices) {
                // ~3% amplitude neutral warm grain alternating subtly light/dark alpha
                val signed = rng.nextInt(17) - 8 // -8..+8 out of 255 (~3.1%)
                val alpha = kotlin.math.abs(signed)
                val rgb = if (signed >= 0) 0xFFF2ECE3.toInt() else 0xFF000000.toInt()
                pixels[i] = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            }
            val bmp = Bitmap.createBitmap(pixels, GRAIN_TILE_PX, GRAIN_TILE_PX, Bitmap.Config.ARGB_8888)
            BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }

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

                float g0 = radialGlow(uv, float2(0.32, 0.24), float2(0.56, 0.42));
                float g1 = radialGlow(uv, float2(0.72, 0.30), float2(0.52, 0.38));
                float g2 = radialGlow(uv, float2(0.46, 0.50), float2(0.62, 0.44));
                float g3 = radialGlow(uv, float2(0.24, 0.66), float2(0.50, 0.36));

                float weightSum = g0 + g1 + g2 + g3 + 0.0001;
                half3 mesh = (c0.rgb * half(g0) + c1.rgb * half(g1) + c2.rgb * half(g2) + c3.rgb * half(g3)) / half(weightSum);

                float auraMask = clamp(max(max(g0, g1), g2 * 0.85) * 0.92, 0.0, 0.88);
                auraMask *= 1.0 - smoothstep(0.52, 0.98, uv.y) * 0.72;

                half3 color = mix(base, mesh, half(auraMask));
                return half4(clamp(color, half3(0.0), half3(1.0)), 1.0);
            }
        """.trimIndent()
    }
}
