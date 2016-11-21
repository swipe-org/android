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
import android.widget.Toast;

import org.swipe.network.SwipeAssetManager;

import java.io.FileInputStream;
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
    private URL url = null;
    private Bitmap mask = null;
    private float animationPercent = 0;
    private float animationFramePercent = 1;
    private Paint maskPaint;
    private boolean prepared = true;
    private boolean ignoreRelease = false;
    private int currentFrame = 0;

    public SwipeImageLayer(Context context) {
        super(context);
        maskPaint = new Paint();
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        maskPaint.setFilterBitmap(false);
    }

    public void setMaskBitmap(Bitmap mask) {
        this.mask = mask;
    }

    public void setIgnoreRelease(boolean ignoreRelease) {
        this.ignoreRelease = ignoreRelease;
    }

    public void release() {
        if (ignoreRelease) return;

        if (prepared) {
            this.setImageBitmap(null);
            prepared = false;
        }
    }

    public void prepare() {
        if (!prepared) {
            prepared = true;

            FileInputStream imageStream = SwipeAssetManager.sharedInstance().loadLocalAsset(url);
            if (imageStream != null) {
                try {
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    this.setImageBitmap(BitmapFactory.decodeStream(imageStream, null, options));
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, "Out of memory " + url + " " + e);
                    Toast.makeText(getContext(), "Out of memory.  Use smaller image " + SwipeUtil.fileName(url.toString()), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public void setImageURL(URL url, BitmapFactory.Options opts,  SwipeElement delegate) {
        this.url = url;
        if (opts.outMimeType.indexOf("gif") >= 0) {
            FileInputStream imageStream = SwipeAssetManager.sharedInstance().loadLocalAsset(url);
            if (imageStream != null) {

                setStream(imageStream, delegate);
            }
        }
    }

    public void setStream(final InputStream stream, final SwipeElement delegate) {
        // TODO
        // Decoding GIFs to individual frames takes a bit of time so we use a background thread and let
        // our delegate know when finished.
        //
        // The PROBLEM is that we could attempt to display/animate before fully decoded.  Need to do this in the
        // resource loading phase and save the frames as bitmap files the first time.

        if (stream != null){
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
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
        animationFramePercent = 1.0f / (gifDecoder.getFrameCount());
    }

    public float getAnimationPercent() {
        return animationPercent;
    }

    public void setAnimationPercent(float animationPercent) {
        this.animationPercent = animationPercent;
        int frame = Math.min((int) (animationPercent / animationFramePercent), gifDecoder.getFrameCount());
        if (frame != currentFrame) {
            currentFrame = frame;
            setImageBitmap(gifDecoder.getFrame(currentFrame));
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //Log.d(TAG, "onDraw");
        super.onDraw(canvas);

        if (mask != null) {
            canvas.save();
            bounds.set(getLeft(), getTop(), getRight(), getBottom());
            canvas.drawBitmap(mask, null, bounds, maskPaint);
            canvas.restore();
        }
    }
}
