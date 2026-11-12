package com.eddyizm.tempus.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
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
 * - **Zero RuntimeShader**: Renders 4 LAB-clamped cover color fields onto a 1/4-resolution
 *   offscreen [Bitmap] using soft elliptical [RadialGradient]s (`SRC_OVER` over `#0E0D0C`),
 *   eliminating `Software rendering doesn't support RuntimeShader` crashes on `Canvas(bitmap)`
 *   and removing all per-frame GPU shader compilation/execution overhead.
 * - **1/4-Resolution Aura Cache**: Re-rendered ONLY when colors change (during the 600ms crossfade
 *   or immediate snap) or view bounds change. Settled frames blit the cached bitmap directly.
 * - **Tiled 64×64 Static Film Grain**: Reusable 64×64 [BitmapShader] (`REPEAT`) drawn over the
 *   bilinear-upscaled aura on the view [Canvas].
 */
class FluidBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs), PlayerMotion.Listener {

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }
    private val settlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val grainPaint = Paint().apply { shader = sharedGrainShader }
    private val gradMatrix = Matrix()
    private val dstRect = RectF()

    private var cachedAuraBitmap: Bitmap? = null
    private var cachedAuraCanvas: Canvas? = null
    private var cachedSettleHeight: Int = -1
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

    private fun drawAuraBlob(
        auraCanvas: Canvas,
        w: Float,
        h: Float,
        color: Int,
        cxNorm: Float,
        cyNorm: Float,
        rxNorm: Float,
        ryNorm: Float,
        peakAlpha: Int,
        midAlpha: Int,
    ) {
        val cPeak = ColorUtils.setAlphaComponent(color, peakAlpha)
        val cMid = ColorUtils.setAlphaComponent(color, midAlpha)
        val cEdge = ColorUtils.setAlphaComponent(color, 0)
        val gradient = RadialGradient(
            0f,
            0f,
            1f,
            intArrayOf(cPeak, cMid, cEdge),
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        gradMatrix.setScale((w * rxNorm).coerceAtLeast(1f), (h * ryNorm).coerceAtLeast(1f))
        gradMatrix.postTranslate(w * cxNorm, h * cyNorm)
        gradient.setLocalMatrix(gradMatrix)
        auraPaint.shader = gradient
        auraCanvas.drawRect(0f, 0f, w, h, auraPaint)
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
            cachedSettleHeight = -1
            cacheDirty = true
        }

        if (cachedSettleHeight != targetH) {
            settlePaint.shader = LinearGradient(
                0f,
                targetH * 0.52f,
                0f,
                targetH.toFloat(),
                0x000E0D0C,
                0xB00E0D0C.toInt(),
                Shader.TileMode.CLAMP,
            )
            cachedSettleHeight = targetH
        }

        if (cacheDirty) {
            val auraCanvas = cachedAuraCanvas
            if (auraCanvas != null) {
                val wf = targetW.toFloat()
                val hf = targetH.toFloat()
                auraCanvas.drawColor(BASE_COLOR)
                drawAuraBlob(auraCanvas, wf, hf, current[0], 0.30f, 0.22f, 0.78f, 0.56f, 224, 116)
                drawAuraBlob(auraCanvas, wf, hf, current[1], 0.74f, 0.28f, 0.72f, 0.52f, 196, 100)
                drawAuraBlob(auraCanvas, wf, hf, current[2], 0.48f, 0.48f, 0.82f, 0.58f, 172, 88)
                drawAuraBlob(auraCanvas, wf, hf, current[3], 0.24f, 0.66f, 0.68f, 0.48f, 144, 72)
                auraPaint.shader = null
                auraCanvas.drawRect(0f, 0f, wf, hf, settlePaint)
            }
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
        val GRADIENT_STOPS = floatArrayOf(0f, 0.52f, 1f)
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
    }
}
