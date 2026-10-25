package com.eddyizm.tempus.collection;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;

public final class CollectionIconDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String icon;
    private final int color;
    public CollectionIconDrawable(String icon) {
        this.icon=icon;
        int[] colors={0xff2b4856,0xff54463a,0xff483b59,0xff34514c,0xff554255};
        color=colors[Math.floorMod(icon.hashCode(),colors.length)];
    }
    public static boolean bind(ImageView view,String name) {
        String icon=CollectionName.icon(name);if (icon.isEmpty()) return false;
        Glide.with(view).clear(view);view.setImageDrawable(new CollectionIconDrawable(icon));return true;
    }
    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds=getBounds();float size=Math.min(bounds.width(),bounds.height());
        paint.setColor(color);canvas.drawRoundRect(new RectF(bounds),size*.1f,size*.1f,paint);
        paint.setColor(Color.WHITE);paint.setTextSize(size*.48f);paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics=paint.getFontMetrics();
        canvas.drawText(icon,bounds.exactCenterX(),bounds.exactCenterY()-(metrics.ascent+metrics.descent)/2,paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
