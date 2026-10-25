package com.eddyizm.tempus.ui.player

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
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

/** A restrained cover tint with a stable, theme-aware surface behind the player controls. */
object PlayerBackdrop {
    private const val SAMPLE_SIZE = 128
    private const val FADE_DURATION_MS = 350L

    @JvmStatic
    fun themedInflater(inflater: LayoutInflater): LayoutInflater =
        inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.ThemeOverlay_App_Player))

    @JvmStatic
    fun apply(target: View, coverArtId: String?) {
        val key = coverArtId ?: ""
        if (target.getTag(R.id.tag_player_backdrop_request) == key) return
        target.setTag(R.id.tag_player_backdrop_request, key)
        val base = ContextCompat.getColor(target.context, R.color.player_backdrop_fallback)
        if (key.isEmpty()) {
            fadeTo(target, intArrayOf(base, base, base))
            return
        }
        Glide.with(target.context.applicationContext)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, SAMPLE_SIZE))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(object : CustomTarget<Bitmap>(SAMPLE_SIZE, SAMPLE_SIZE) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // A late cover request must never repaint the next track's background.
                    if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                        val palette = Palette.from(resource).generate()
                        val primary = palette.getMutedColor(palette.getDominantColor(base))
                        val secondary = palette.getVibrantColor(primary)
                        val tint = if (ColorUtils.calculateLuminance(base) > 0.5) 0.18f else 0.22f
                        fadeTo(target, intArrayOf(
                            ColorUtils.blendARGB(base, primary, tint),
                            ColorUtils.blendARGB(base, secondary, tint * 0.45f),
                            base
                        ))
                    }
                    Handler(Looper.getMainLooper()).post {
                        Glide.with(target.context.applicationContext).clear(this)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                        fadeTo(target, intArrayOf(base, base, base))
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }

    private fun fadeTo(target: View, colors: IntArray) {
        (target.getTag(R.id.tag_player_backdrop_animator) as? ValueAnimator)?.cancel()
        val base = ContextCompat.getColor(target.context, R.color.player_backdrop_fallback)
        val from = target.getTag(R.id.tag_player_backdrop_colors) as? IntArray
            ?: intArrayOf(base, base, base)
        val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, from)
        target.background = gradient
        fun draw(fraction: Float) {
            val blended = IntArray(3) { ColorUtils.blendARGB(from[it], colors[it], fraction) }
            gradient.colors = blended
            target.setTag(R.id.tag_player_backdrop_colors, blended)
        }
        if (from.contentEquals(colors) || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            draw(1f)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_DURATION_MS
            addUpdateListener { draw(it.animatedValue as Float) }
        }
        target.setTag(R.id.tag_player_backdrop_animator, animator)
        animator.start()
    }
}
