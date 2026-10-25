package com.eddyizm.tempus.ui.browse

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.ui.player.CoverScheme

/** Tints a page header with its cover's colour fading into the page, as Apple Music and Spotify do. */
object CoverTint {
    private const val SAMPLE_SIZE = 96

    @JvmStatic
    fun apply(header: View, coverArtId: String?) {
        if (coverArtId.isNullOrEmpty()) return
        val context = header.context
        Glide.with(header)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, SAMPLE_SIZE))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(object : CustomTarget<Bitmap>(SAMPLE_SIZE, SAMPLE_SIZE) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val scheme = CoverScheme.fromBitmap(resource)
                    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                    val tone = if (night) scheme.miniDark else scheme.miniLight
                    header.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(tone, ColorUtils.setAlphaComponent(tone, 0)),
                    )
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }
}
