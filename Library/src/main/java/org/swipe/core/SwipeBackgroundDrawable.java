package org.swipe.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.support.annotation.Nullable;

/**
 * Created by pete on 10/24/16.
 */

public class SwipeBackgroundDrawable extends PaintDrawable {

    private int backgroundColor = Color.TRANSPARENT;
    private float cornerRadious = 0;
    private float borderWidth = 0;
    private int borderColor = Color.BLACK;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public float getBorderWidth() {
        return borderWidth;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public void setBorderWidth(float borderWidth) {
        this.borderWidth = borderWidth;
    }

    @Override
    public void setCornerRadius(float cornerRadious) {
        this.cornerRadious = cornerRadious;
        super.setCornerRadius(cornerRadious);
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    @Override
    public void draw(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(backgroundColor);
        Rect rect = getBounds();
        canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, cornerRadious, cornerRadious, paint);
        if (borderWidth > 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderWidth);
            paint.setColor(borderColor);
            canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, cornerRadious, cornerRadious, paint);
        }
    }
}
