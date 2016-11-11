package org.swipe.core;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.Log;
import android.widget.ImageView;

import org.swipe.network.SwipeAssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import jp.tomorrowkey.android.gifplayer.GifDecoder;

/**
 * Created by pete on 11/3/16.
 */

public class SwipeImageLayer extends ImageView {
    private static final String TAG = "SwImageLayer";
    private Rect bounds = new Rect(getLeft(), getTop(), getRight(), getBottom());
    private GifDecoder gifDecoder = null;
    private Bitmap mask = null;
    private float animationPercent = 0;
    private float animationFramePercent = 1;
    private Paint maskPaint;

    public SwipeImageLayer(Context context) {
        super(context);
        maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        maskPaint.setFilterBitmap(false);
    }

    public void setMaskBitmap(Bitmap mask) {
        this.mask = mask;
    }

    public void setStream(final InputStream stream, final SwipeElement delegate) {
        // TODO
        // Decoding GIFs to individual frames takes a bit of time so we use a background thread and let
        // our delegate now when finished.
        // The PROBLEM is that we could attempt to animate before fully decoded.  Really need to do this in the
        // resource loading phase.
        
        if (stream != null){
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        stream.reset();
                        GifDecoder gifDecoder = new GifDecoder();
                        int status = gifDecoder.read(stream);
                        if (status == GifDecoder.STATUS_OK && gifDecoder.getFrameCount() > 0) {
                            Log.d(TAG, "image is a GIF with " + gifDecoder.getFrameCount() + " frames " + gifDecoder.getLoopCount() + " loops");
                            setGifDecoder(gifDecoder);

                            SwipeImageLayer.this.post(new Runnable() {
                                @Override
                                public void run() {
                                    delegate.onGifLoaded(SwipeImageLayer.this);
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.start();
        }
    }

    public void setGifDecoder(GifDecoder gifDecoder) {
        this.gifDecoder = gifDecoder;
        animationFramePercent = 1.0f / (gifDecoder.getFrameCount() - 1);
    }

    public float getAnimationPercent() {
        return animationPercent;
    }

    public void setAnimationPercent(float animationPercent) {
        this.animationPercent = animationPercent;
        int current = (int) (animationPercent / animationFramePercent);
        setImageBitmap(gifDecoder.getFrame(current));
        invalidate();
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

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
    }
}
