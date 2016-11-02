package org.swipe.core;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;

import org.json.JSONObject;

/**
 * Created by pete on 10/21/16.
 */

class SwipeShapeDrawable extends ShapeDrawable {
    private Path path = null;
    private float dipW = 0;
    private float dipH = 0;
    private int fillColor = Color.TRANSPARENT;
    private int strokeColor = Color.BLACK;
    private float lineWidth = 0;
    private Paint.Cap lineCap = Paint.Cap.ROUND;
    private float strokeStart = 0;
    private float strokeEnd = 1;
    private CGSize shadowOffset = null;
    private float shadowRadius = 1;
    private int shadowColor = Color.BLACK;

    SwipeShapeDrawable(Path path, float dipW, float dipH) {
        this.path = path;
        this.dipW = dipW;
        this.dipH = dipH;
    }
    public Path getPath() { return path; }
    public void setPath(Path path) {
        this.path = path;
    }

    public int getFillColor() { return this.fillColor; }
    public void setFillColor(int color) { this.fillColor = color; }

    public int getStrokeColor() { return this.strokeColor; }
    public void setStrokeColor(int color ) { this.strokeColor = color; }

    public float getLineWidth() { return this.lineWidth; }
    public void setLineWidth(float width) { this.lineWidth = width; }

    public Paint.Cap getLineCap() { return this.lineCap; }
    public void setLineCap(Paint.Cap lineCap) { this.lineCap = lineCap; }

    public float getStrokeStart() { return this.strokeStart; }
    public void setStrokeStart(float strokeStart) { this.strokeStart = strokeStart; }

    public float getStrokeEnd () { return this.strokeEnd; }
    public void setStrokeEnd(float strokeEnd) { this.strokeEnd = strokeEnd; }

    public int getShadowColor() { return shadowColor; }
    public void setShadowColor(int shadowColor) { this.shadowColor = shadowColor; }

    public CGSize getShadowOffset() { return shadowOffset; }
    public void setShadowOffset(CGSize shadowOffset) { this.shadowOffset = shadowOffset; }

    public float getShadowRadius() { return shadowRadius; }
    public void setShadowRadius(float shadowRadius) { this.shadowRadius = shadowRadius; }

    @Override
    public void draw(Canvas canvas) {
        Paint p = getPaint();
        p.reset();
        p.setAntiAlias(true);

        if (shadowOffset != null) {
            p.setShadowLayer(shadowRadius, shadowOffset.width, shadowOffset.height, shadowColor);
        }

        if (fillColor != Color.TRANSPARENT) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(fillColor);
            canvas.drawPath(path, p);
        }

        if (lineWidth > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lineWidth);
            p.setStrokeCap(lineCap);
            p.setColor(strokeColor);
            canvas.drawPath(path, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(shadowColor);
            canvas.drawPath(path, p);
        }
    }
}
