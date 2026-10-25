package com.eddyizm.tempus.ui.player

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Colours derived from one cover for the immersive player.
 *
 * [blobs] are four deep tones for the fluid backdrop, kept dark enough for light text on top.
 * [miniLight] / [miniDark] tint the mini player in the light and dark app themes.
 */
data class CoverScheme(
    val blobs: List<Int>,
    val miniLight: Int,
    val miniDark: Int,
) {
    companion object {
        @JvmField
        val FALLBACK = fromSeed(0xFF2C343F.toInt())

        private const val MIN_HUE_DISTANCE = 18f

        @JvmStatic
        fun fromBitmap(bitmap: Bitmap): CoverScheme {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val swatches = listOfNotNull(
                palette.dominantSwatch,
                palette.vibrantSwatch,
                palette.darkVibrantSwatch,
                palette.mutedSwatch,
                palette.darkMutedSwatch,
                palette.lightVibrantSwatch,
                palette.lightMutedSwatch,
            ).sortedByDescending { it.population }
            if (swatches.isEmpty()) return FALLBACK
            return fromColors(swatches.map { it.rgb })
        }

        @JvmStatic
        fun fromSeed(seed: Int): CoverScheme = fromColors(listOf(seed))

        /** Builds a scheme from colours ordered by importance; the first one is the seed. */
        @JvmStatic
        fun fromColors(colors: List<Int>): CoverScheme {
            val seedHsl = hsl(colors.first())
            val picked = ArrayList<FloatArray>(4)
            for (color in colors) {
                val candidate = hsl(color)
                if (picked.none { hueDistance(it, candidate) < MIN_HUE_DISTANCE && abs(it[2] - candidate[2]) < 0.12f }) {
                    picked.add(candidate)
                }
                if (picked.size == 4) break
            }
            // Monochrome or single-colour covers still get movement by turning the seed hue slightly.
            val turns = floatArrayOf(28f, -24f, 52f)
            var turn = 0
            while (picked.size < 4) {
                val base = picked[0]
                picked.add(floatArrayOf((base[0] + turns[turn++] + 360f) % 360f, base[1], base[2] * 0.85f))
            }
            val depth = floatArrayOf(0.34f, 0.26f, 0.19f, 0.30f)
            val blobs = picked.mapIndexed { index, value -> deepen(value, depth[index]) }
            val seedSaturation = seedHsl[1]
            return CoverScheme(
                blobs = blobs,
                miniLight = tone(seedHsl[0], min(seedSaturation, 0.45f), 0.90f),
                miniDark = tone(seedHsl[0], min(seedSaturation, 0.40f), 0.20f),
            )
        }

        /** Hue in degrees, saturation and lightness in 0..1; plain maths so it runs in JVM tests. */
        @JvmStatic
        fun hsl(color: Int): FloatArray {
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val high = max(r, max(g, b))
            val low = min(r, min(g, b))
            val lightness = (high + low) / 2f
            val delta = high - low
            if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
            val saturation = delta / (1f - abs(2f * lightness - 1f))
            val hue = when (high) {
                r -> ((g - b) / delta).mod(6f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * 60f
            return floatArrayOf(hue.mod(360f), saturation.coerceIn(0f, 1f), lightness)
        }

        private fun hueDistance(a: FloatArray, b: FloatArray): Float {
            // Hue means little for near-grey colours, so they only differ by lightness.
            if (a[1] < 0.12f || b[1] < 0.12f) return if (a[1] < 0.12f && b[1] < 0.12f) 0f else 180f
            val d = abs(a[0] - b[0]) % 360f
            return if (d > 180f) 360f - d else d
        }

        /** Keeps the hue, limits saturation and sets a dark lightness that keeps white text legible. */
        private fun deepen(value: FloatArray, lightness: Float): Int {
            val saturation = if (value[1] < 0.08f) value[1] else value[1].coerceIn(0.22f, 0.78f)
            val shifted = (lightness + (value[2] - 0.5f) * 0.12f).coerceIn(0.13f, 0.40f)
            return tone(value[0], saturation, shifted)
        }

        @JvmStatic
        fun tone(hue: Float, saturation: Float, lightness: Float): Int {
            val chroma = (1f - abs(2f * lightness - 1f)) * saturation
            val segment = hue.mod(360f) / 60f
            val x = chroma * (1f - abs(segment.mod(2f) - 1f))
            val (r, g, b) = when (segment.toInt()) {
                0 -> Triple(chroma, x, 0f)
                1 -> Triple(x, chroma, 0f)
                2 -> Triple(0f, chroma, x)
                3 -> Triple(0f, x, chroma)
                4 -> Triple(x, 0f, chroma)
                else -> Triple(chroma, 0f, x)
            }
            val m = lightness - chroma / 2f
            fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
            return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
    }
}
