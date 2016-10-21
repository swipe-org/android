package org.swipe.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;

/**
 * Created by pete on 10/21/16.
 */

class SwipeShapeDrawable extends ShapeDrawable {
    private Path path = null;
    private float dipW = 0;
    private float dipH = 0;
    private int fillColor = Color.TRANSPARENT;
    private int strokeColor = Color.BLACK;
    private float lineWidth = 1;
    private Paint.Cap lineCap = Paint.Cap.ROUND;
    private float strokeStart = 0;
    private float strokeEnd = 1;

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

    @Override
    public void draw(Canvas canvas) {
        Paint p = getPaint();
        p.reset();

        if (fillColor != Color.TRANSPARENT) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(fillColor);
            canvas.drawPath(path, p);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lineWidth);
        p.setStrokeCap(lineCap);
        p.setColor(strokeColor);

        canvas.drawPath(path, p);
    }
}
