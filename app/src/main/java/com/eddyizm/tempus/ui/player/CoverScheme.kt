package com.eddyizm.tempus.ui.player

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colours derived from one cover for the Editorial Warm Dark ("唱片店夜色") immersive player.
 *
 * [blobs] are four deep, low-chroma tones clamped strictly in CIE 1976 `L*a*b*` space
 * (`L* 16–26`, `C* = hypot(a*, b*) <= 22`) so neon yellow-green or electric blue covers never
 * exceed luminance or chroma budgets.
 * [miniLight] / [miniDark] tint the mini player in Warm Paper light mode and Warm Dark night mode.
 */
data class CoverScheme(
    val blobs: List<Int>,
    val miniLight: Int,
    val miniDark: Int,
) {
    companion object {
        @JvmField
        val FALLBACK = CoverScheme(
            blobs = listOf(
                0xFF1A1612.toInt(),
                0xFF241D15.toInt(),
                0xFF0E0D0C.toInt(),
                0xFF161412.toInt(),
            ),
            miniLight = 0xFFF5F1EA.toInt(),
            miniDark = 0xFF161412.toInt(),
        )

        private const val MIN_HUE_DISTANCE = 18f
        private const val MIN_L_STAR = 16.0
        private const val MAX_L_STAR = 26.0
        private const val MAX_CHROMA = 22.0

        // D65 reference white
        private const val REF_X = 95.047
        private const val REF_Y = 100.000
        private const val REF_Z = 108.883

        @JvmStatic
        fun fromBitmap(bitmap: Bitmap): CoverScheme {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val swatches = listOfNotNull(
                palette.dominantSwatch,
                palette.darkMutedSwatch,
                palette.mutedSwatch,
                palette.darkVibrantSwatch,
                palette.vibrantSwatch,
                palette.lightMutedSwatch,
                palette.lightVibrantSwatch,
            ).sortedByDescending { it.population }
            if (swatches.isEmpty()) return FALLBACK
            return fromColors(swatches.map { it.rgb })
        }

        @JvmStatic
        fun fromSeed(seed: Int): CoverScheme = fromColors(listOf(seed))

        /** Builds a scheme from colours ordered by importance; the first one is the seed. */
        @JvmStatic
        fun fromColors(colors: List<Int>): CoverScheme {
            if (colors.isEmpty()) return FALLBACK
            val seedHsl = hsl(colors.first())
            val picked = ArrayList<FloatArray>(4)
            for (color in colors) {
                val candidate = hsl(color)
                if (picked.none { hueDistance(it, candidate) < MIN_HUE_DISTANCE && abs(it[2] - candidate[2]) < 0.12f }) {
                    picked.add(candidate)
                }
                if (picked.size == 4) break
            }
            val turns = floatArrayOf(28f, -24f, 52f)
            var turn = 0
            while (picked.size < 4) {
                val base = picked[0]
                picked.add(floatArrayOf((base[0] + turns[turn++] + 360f) % 360f, base[1], base[2] * 0.85f))
            }
            val targetLStars = doubleArrayOf(25.0, 21.5, 16.5, 19.0)
            val blobs = picked.mapIndexed { index, value -> deepenLab(value, targetLStars[index]) }
            val seedSaturation = seedHsl[1]
            return CoverScheme(
                blobs = blobs,
                miniLight = tone(seedHsl[0], min(seedSaturation, 0.20f), 0.93f),
                miniDark = clampColorLab(tone(seedHsl[0], min(seedSaturation, 0.24f), 0.12f), 8.0, 14.0, 14.0),
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
            if (a[1] < 0.12f || b[1] < 0.12f) return if (a[1] < 0.12f && b[1] < 0.12f) 0f else 180f
            val d = abs(a[0] - b[0]) % 360f
            return if (d > 180f) 360f - d else d
        }

        /**
         * Generates a candidate tone preserving hue, then clamps strictly in CIE 1976 `L*a*b*`
         * (`L* in 16..26`, `hypot(a*, b*) <= 22`) so high-luminance yellow-greens and high-chroma
         * blues stay calm and dark.
         */
        private fun deepenLab(value: FloatArray, targetLStar: Double): Int {
            val sat = if (value[1] < 0.06f) value[1] else value[1].coerceIn(0.18f, 0.45f)
            val seedRgb = tone(value[0], sat, 0.24f)
            val shiftedL = (targetLStar + (value[2] - 0.5f) * 2.0).coerceIn(MIN_L_STAR, MAX_L_STAR)
            return clampColorLab(seedRgb, shiftedL, shiftedL, MAX_CHROMA)
        }

        internal fun rgbToLab(color: Int): DoubleArray {
            fun linearize(channel: Int): Double {
                val v = channel / 255.0
                return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            val r = linearize((color shr 16) and 0xFF)
            val g = linearize((color shr 8) and 0xFF)
            val b = linearize(color and 0xFF)

            val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) * 100.0
            val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) * 100.0
            val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) * 100.0

            fun pivot(t: Double): Double =
                if (t > 0.008856) cbrt(t) else (7.787 * t) + (16.0 / 116.0)

            val fx = pivot(x / REF_X)
            val fy = pivot(y / REF_Y)
            val fz = pivot(z / REF_Z)

            val l = (116.0 * fy) - 16.0
            val a = 500.0 * (fx - fy)
            val bStar = 200.0 * (fy - fz)
            return doubleArrayOf(l, a, bStar)
        }

        private fun labToRgb(l: Double, a: Double, bStar: Double): Int {
            val fy = (l + 16.0) / 116.0
            val fx = (a / 500.0) + fy
            val fz = fy - (bStar / 200.0)

            fun unpivot(f: Double): Double {
                val cubed = f * f * f
                return if (cubed > 0.008856) cubed else (f - 16.0 / 116.0) / 7.787
            }

            val x = (unpivot(fx) * REF_X) / 100.0
            val y = (unpivot(fy) * REF_Y) / 100.0
            val z = (unpivot(fz) * REF_Z) / 100.0

            val rLin = x * 3.2404542 + y * -1.5371385 + z * -0.4985314
            val gLin = x * -0.9692660 + y * 1.8760108 + z * 0.0415560
            val bLin = x * 0.0556434 + y * -0.2040259 + z * 1.0572252

            fun gamma(c: Double): Int {
                val clamped = c.coerceIn(0.0, 1.0)
                val encoded = if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
                return (encoded * 255.0).roundToInt().coerceIn(0, 255)
            }

            return (0xFF shl 24) or (gamma(rLin) shl 16) or (gamma(gLin) shl 8) or gamma(bLin)
        }

        private fun clampColorLab(color: Int, minL: Double, maxL: Double, maxChroma: Double): Int {
            val lab = rgbToLab(color)
            val clampedL = lab[0].coerceIn(minL, maxL)
            var a = lab[1]
            var b = lab[2]
            val chroma = hypot(a, b)
            if (chroma > maxChroma && chroma > 1e-6) {
                val scale = maxChroma / chroma
                a *= scale
                b *= scale
            }
            return labToRgb(clampedL, a, b)
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
