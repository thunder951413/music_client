package com.eddyizm.tempus.glide;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.Registry;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.eddyizm.tempus.util.Preferences;

import java.io.InputStream;

@GlideModule
public class CustomGlideModule extends AppGlideModule {
    /** The artwork cache limit Glide started with; a new setting applies after a restart. */
    public static volatile int activeCacheSizeMb = -1;

    @Override
    public void applyOptions(@NonNull Context context, GlideBuilder builder) {
        activeCacheSizeMb = Preferences.getImageCacheSize();
        int diskCacheSize = activeCacheSizeMb * 1024 * 1024;
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "cache", diskCacheSize));
        // Full colour depth: RGB_565 bands the smooth gradients of cover art on large screens.
        builder.setDefaultRequestOptions(new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.replace(String.class, InputStream.class, new IPv6StringLoader.Factory());
        registry.prepend(Uri.class, InputStream.class, new IPv6StringLoader.UriFactory());
    }
}
