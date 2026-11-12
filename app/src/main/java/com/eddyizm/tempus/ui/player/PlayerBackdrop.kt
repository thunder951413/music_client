package com.eddyizm.tempus.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import java.util.WeakHashMap

/** Derives the immersive player's [CoverScheme] from a 32x32 downsampled cover and publishes it. */
object PlayerBackdrop {
    private const val REQUEST_SIZE = 128
    private const val SAMPLE_SIZE = 32
    private val inFlightTargets = WeakHashMap<View, CustomTarget<Bitmap>>()

    @JvmStatic
    fun themedInflater(inflater: LayoutInflater): LayoutInflater =
        inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.ThemeOverlay_App_Player))

    /**
     * Cancels any previous Glide request on [target] before issuing a new 32x32 cover load so
     * rapid track skipping never piles up stale bitmap decodes or recolours a newer track.
     */
    @JvmStatic
    fun apply(target: View, coverArtId: String?) {
        val key = coverArtId ?: ""
        if (target.getTag(R.id.tag_player_backdrop_request) == key) return
        target.setTag(R.id.tag_player_backdrop_request, key)

        val appContext = target.context.applicationContext
        inFlightTargets.remove(target)?.let { previous ->
            Glide.with(appContext).clear(previous)
        }

        if (key.isEmpty()) {
            PlayerMotion.setScheme(CoverScheme.FALLBACK)
            return
        }

        val glideTarget = object : CustomTarget<Bitmap>(SAMPLE_SIZE, SAMPLE_SIZE) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                    PlayerMotion.setScheme(CoverScheme.fromBitmap(resource))
                }
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                    PlayerMotion.setScheme(CoverScheme.FALLBACK)
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) = Unit
        }
        inFlightTargets[target] = glideTarget

        Glide.with(appContext)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, REQUEST_SIZE))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(glideTarget)
    }
}
