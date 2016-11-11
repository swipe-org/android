package org.swipe.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 11/10/16.
 */

public class SwipeSpriteLayer extends View {
    private static final String TAG = "SwSpriteLayer";
    private List<Bitmap> sprites = new ArrayList<>();
    private int current = 0;
    private float animationPercent = 0;
    private float animationSpritePercent = 0;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect src;
    private Rect dst = new Rect(0, 0, getRight(), getBottom());

    public SwipeSpriteLayer(Context context, Bitmap bitmap, CGSize slice, Point slot) {
        super(context);
        final int w = bitmap.getWidth() / (int)slice.width;
        final int h = bitmap.getHeight() / (int)slice.height;
        final int y = slot.y * h;
        final int spriteCnt = (int)(slice.width - slot.x);
        src = new Rect(0, 0, w, h);
        animationSpritePercent = 1.0f / spriteCnt;

        for (int i = 0; i < spriteCnt; i++) {
            Bitmap sprite = Bitmap.createBitmap(bitmap, (slot.x + i) * w, y, w, h);
            sprites.add(sprite);
        }
    }

    public float getAnimationPercent() {
        return animationPercent;
    }

    public void setAnimationPercent(float animationPercent) {
        this.animationPercent = animationPercent;
        this.current = (int)(animationPercent / animationSpritePercent);
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        dst.right = getRight();
        dst.bottom = getBottom();

        canvas.drawBitmap(sprites.get(current), src, dst, paint);
    }
}
