package org.swipe.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.widget.ImageView;

/**
 * Created by pete on 11/3/16.
 */

public class SwipeImageLayer extends ImageView {
    private Rect bounds = new Rect(getLeft(), getTop(), getRight(), getBottom());
    private Bitmap mask = null;
    private Paint maskPaint;
    {
        maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        maskPaint.setFilterBitmap(false);
    }

    public SwipeImageLayer(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mask != null) {
            canvas.save();
            bounds.set(getLeft(), getTop(), getRight(), getBottom());
            canvas.drawBitmap(mask, null, bounds, maskPaint);
            canvas.restore();
        }
    }

    public void setMaskBitmap(Bitmap mask) { this.mask = mask; }
}
