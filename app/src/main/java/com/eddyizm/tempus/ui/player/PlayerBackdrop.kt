package com.eddyizm.tempus.ui.player

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest

/** Derives the immersive player's [CoverScheme] from the current cover and publishes it. */
object PlayerBackdrop {
    private const val SAMPLE_SIZE = 128

    @JvmStatic
    fun themedInflater(inflater: LayoutInflater): LayoutInflater =
        inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.ThemeOverlay_App_Player))

    /** [target] only scopes the request: a late cover result must never recolour the next track. */
    @JvmStatic
    fun apply(target: View, coverArtId: String?) {
        val key = coverArtId ?: ""
        if (target.getTag(R.id.tag_player_backdrop_request) == key) return
        target.setTag(R.id.tag_player_backdrop_request, key)
        if (key.isEmpty()) {
            PlayerMotion.setScheme(CoverScheme.FALLBACK)
            return
        }
        Glide.with(target.context.applicationContext)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, SAMPLE_SIZE))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(object : CustomTarget<Bitmap>(SAMPLE_SIZE, SAMPLE_SIZE) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                        PlayerMotion.setScheme(CoverScheme.fromBitmap(resource))
                    }
                    Handler(Looper.getMainLooper()).post {
                        Glide.with(target.context.applicationContext).clear(this)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (target.getTag(R.id.tag_player_backdrop_request) == key) {
                        PlayerMotion.setScheme(CoverScheme.FALLBACK)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }
}
