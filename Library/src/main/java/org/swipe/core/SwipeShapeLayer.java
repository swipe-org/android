package org.swipe.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;

/**
 * Created by pete on 10/21/16.
 *
 * TODO: Alpha Clipping
 * There is an issue where the View is clipped to it's parent's outline when alpha is < 1.0. It acts
 * as if the parent has clipToOutline == true but that does not seem to be the case.  So, as a
 * workaround, we don't let the View see anything other than alpha == 1 and we handle the alpha
 * ourselves in onDraw.
 */

class SwipeShapeLayer extends View {
    private static final String TAG = "SwShapeLayer";
    private SwipePath path = null;
    private Path drawPath = null;
    private int fillColor = Color.TRANSPARENT;
    private int strokeColor = Color.BLACK;
    private float lineWidth = 0;
    private float alpha = 1;
    private Paint.Cap lineCap = Paint.Cap.ROUND;
    private float strokeStart = 0;
    private float strokeEnd = 1;
    private CGSize shadowOffset = null;
    private float shadowRadius = 1;
    private int shadowColor = Color.BLACK;
    private Paint p = new Paint();
    private PathMeasure measure = null;
    private float pathLength = 0;

    SwipeShapeLayer(Context context, SwipePath path) {
        super(context);
        setPath(path);
    }

    @Override
    public float getAlpha() { return alpha; }
    @Override
    public void setAlpha(float alpha) { this.alpha = alpha; invalidate(); }

    private void updateDrawPath() {
        final float startD = pathLength * strokeStart;
        final float endD = pathLength * strokeEnd;
        Path dst = new Path();

        if (path.getPathCount() == 1 && measure.getSegment(startD, endD, dst, /* startWithMoveTo */ true)) {
            drawPath = dst;
        } else {
            drawPath = path.getPath();
        }

        invalidate();
    }

    private void measureAndUpdate() {
        measure = new PathMeasure(this.path.getPath(), false);
        pathLength = measure.getLength();
        updateDrawPath();
    }

    public Matrix getPathTransform() { return path.getTransform(); }
    public void setPathTransform(Matrix transform) {
        path.setTransform(transform);
        setPath(path);
    }

    public SwipePath getPath() { return path; }
    public void setPath(SwipePath path) {
        this.path = path;
        measureAndUpdate();
    }

    public float getStrokeStart() { return this.strokeStart; }
    public void setStrokeStart(float strokeStart) {
        this.strokeStart = strokeStart;
        updateDrawPath();
    }

    public float getStrokeEnd () { return this.strokeEnd; }
    public void setStrokeEnd(float strokeEnd) {
        this.strokeEnd = strokeEnd;
        updateDrawPath();
    }

    public int getFillColor() { return this.fillColor; }
    public void setFillColor(int color) { this.fillColor = color; invalidate(); }

    public int getStrokeColor() { return this.strokeColor; }
    public void setStrokeColor(int color ) { this.strokeColor = color; invalidate(); }

    public float getLineWidth() { return this.lineWidth; }
    public void setLineWidth(float width) { this.lineWidth = width; invalidate(); }

    public Paint.Cap getLineCap() { return this.lineCap; }
    public void setLineCap(Paint.Cap lineCap) { this.lineCap = lineCap; invalidate(); }

    public int getShadowColor() { return shadowColor; }
    public void setShadowColor(int shadowColor) { this.shadowColor = shadowColor; invalidate(); }

    public CGSize getShadowOffset() { return shadowOffset; }
    public void setShadowOffset(CGSize shadowOffset) { this.shadowOffset = shadowOffset; invalidate(); }

    public float getShadowRadius() { return shadowRadius; }
    public void setShadowRadius(float shadowRadius) { this.shadowRadius = shadowRadius; invalidate(); }

    @Override
    public void draw(Canvas canvas) {
        p.reset();

        if (shadowOffset != null) {
            p.setShadowLayer(shadowRadius, shadowOffset.width, shadowOffset.height, shadowColor);
        }

        if (fillColor == strokeColor) {
            // Optimize by drawing both at the same time
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            int realFillColor = Color.argb((int) (Color.alpha(fillColor) * alpha), Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor));
            p.setColor(realFillColor);
            p.setStrokeWidth(lineWidth);
            p.setStrokeCap(lineCap);
            canvas.drawPath(drawPath, p);
        } else {
            if (Color.alpha(fillColor) != 0) {
                p.setStyle(Paint.Style.FILL);
                int realFillColor = Color.argb((int) (Color.alpha(fillColor) * alpha), Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor));
                p.setColor(realFillColor);
                canvas.drawPath(drawPath, p);
            }

            if (lineWidth > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(lineWidth);
                p.setStrokeCap(lineCap);
                int realStrokeColor = Color.argb((int) (Color.alpha(strokeColor) * alpha), Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor));
                p.setColor(realStrokeColor);
                canvas.drawPath(drawPath, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1);
                p.setColor(shadowColor);
                canvas.drawPath(drawPath, p);
            }
        }
    }
}
