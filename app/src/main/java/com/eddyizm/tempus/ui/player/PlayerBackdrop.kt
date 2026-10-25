package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest

/**
 * Apple Music style now-playing backdrop: the expanded player takes a dark colour picked
 * from the current cover, and its content is inflated with light-on-dark colours.
 */
object PlayerBackdrop {
    private const val SAMPLE_SIZE = 128
    private const val MAX_LUMINANCE = 0.12
    private const val FADE_DURATION_MS = 450L

    /** Inflater whose views resolve theme colours as light content on the dark backdrop. */
    @JvmStatic
    fun themedInflater(inflater: LayoutInflater): LayoutInflater =
        inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.ThemeOverlay_App_Player))

    /** Fades [target]'s background to a colour from the cover, or to the neutral fallback. */
    @JvmStatic
    fun apply(target: View, coverArtId: String?) {
        if (coverArtId.isNullOrEmpty()) {
            fadeTo(target, fallbackColor(target))
            return
        }

        Glide.with(target.context.applicationContext)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, SAMPLE_SIZE))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(object : CustomTarget<Bitmap>(SAMPLE_SIZE, SAMPLE_SIZE) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    fadeTo(target, pickColor(Palette.from(resource).generate(), fallbackColor(target)))
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    fadeTo(target, fallbackColor(target))
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private fun pickColor(palette: Palette, fallback: Int): Int {
        val swatch = palette.darkVibrantSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: return fallback

        // Darken until white text stays readable on top of it.
        var color = swatch.rgb
        repeat(12) {
            if (ColorUtils.calculateLuminance(color) <= MAX_LUMINANCE) return color
            color = ColorUtils.blendARGB(color, Color.BLACK, 0.15f)
        }
        return color
    }

    private fun fallbackColor(target: View): Int =
        ContextCompat.getColor(target.context, R.color.player_backdrop_fallback)

    private fun fadeTo(target: View, color: Int) {
        (target.getTag(R.id.tag_player_backdrop_animator) as? ValueAnimator)?.cancel()

        val from = (target.background as? ColorDrawable)?.color ?: fallbackColor(target)
        if (from == color) return

        val animator = ValueAnimator.ofArgb(from, color).apply {
            duration = FADE_DURATION_MS
            addUpdateListener { target.setBackgroundColor(it.animatedValue as Int) }
        }
        target.setTag(R.id.tag_player_backdrop_animator, animator)
        animator.start()
    }
}
