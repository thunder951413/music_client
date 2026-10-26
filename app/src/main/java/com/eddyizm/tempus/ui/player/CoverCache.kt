package com.eddyizm.tempus.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.util.Preferences
import java.util.function.Consumer

/**
 * Decoded now-playing covers, shared by the mini player, the flying cover and the record label.
 *
 * Each place used to load the artwork on its own, at full server resolution by default, so the
 * cover appeared only after a download and decode. One bounded request per cover now fills this
 * cache, and a cached cover shows on the very first frame.
 */
object CoverCache {
    /** Big enough for the record label on large phones, small enough to decode quickly. */
    private const val MAX_SIZE_PX = 1080

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val waiting = HashMap<String, MutableList<Consumer<Bitmap>>>()

    @JvmStatic
    fun get(coverArtId: String?): Bitmap? = coverArtId?.let { cache.get(it) }

    /**
     * Delivers the cover to [onReady] on the main thread: at once when cached, otherwise after one
     * shared load. Requests for a cover already loading join that load.
     */
    @JvmStatic
    fun load(context: Context, coverArtId: String?, onReady: Consumer<Bitmap>?) {
        if (coverArtId.isNullOrEmpty()) return
        cache.get(coverArtId)?.let { onReady?.accept(it); return }
        val pending = waiting[coverArtId]
        if (pending != null) {
            onReady?.let { pending.add(it) }
            return
        }
        waiting[coverArtId] = mutableListOf<Consumer<Bitmap>>().also { list -> onReady?.let { list.add(it) } }
        val preferred = Preferences.getImageSize()
        val size = if (preferred in 1..MAX_SIZE_PX) preferred else MAX_SIZE_PX
        Glide.with(context.applicationContext)
            .asBitmap()
            .load(CustomGlideRequest.createUrl(coverArtId, size))
            .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            .into(object : CustomTarget<Bitmap>(size, size) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Glide recycles its bitmaps when the target is cleared, so keep a copy.
                    val copy = resource.copy(resource.config ?: Bitmap.Config.ARGB_8888, false)
                    cache.put(coverArtId, copy)
                    waiting.remove(coverArtId)?.forEach { it.accept(copy) }
                    // Hand Glide's original back to its pool; the cache owns the copy.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Glide.with(context.applicationContext).clear(this)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    waiting.remove(coverArtId)
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            })
    }

    /** Warms the cache for covers likely to show next, such as the neighbouring queue tracks. */
    @JvmStatic
    fun preload(context: Context, coverArtIds: Collection<String?>) {
        coverArtIds.forEach { load(context, it, null) }
    }
}
