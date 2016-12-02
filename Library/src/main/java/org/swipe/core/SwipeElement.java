package org.swipe.core;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.swipe.network.SwipeAssetManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeElement extends SwipeView {

    interface Delegate {
        double durationSec();
        JSONObject prototypeWithName(String name);
        Object pathWithName(String name);
        void onAction(SwipeElement element);
        boolean shouldRepeat(SwipeElement element);
        void didStartPlaying(SwipeElement element);
        void didFinishPlaying(SwipeElement element, boolean completed);
        List<SwipeMarkdown.Element> parseMarkdown(Object markdowns);
        URL baseURL();
        URL map(URL url);
        URL makeFullURL(String url);
        /*
        func addedResourceURLs(urls:[NSURL:String], callback:() -> Void)
        */
        boolean isCurrentPage();
        String localizedStringForKey(String key);
        String langId();
    }

    private static final String TAG = "SwElem";
    private SwipeElement.Delegate delegate = null;
    private List<SwipeObjectAnimator> animations = new ArrayList<>();
    private boolean fRepeat = false;
    private boolean fFocusable = false;
    private boolean fEnabled = true;
    private boolean fClip = false;
    private String action = null;
    private SwipeShapeLayer shapeLayer = null;
    private SwipeTextLayer textLayer = null;
    private SwipeImageLayer imageLayer = null;
    private ViewGroup innerLayer = null;
    private SwipeSpriteLayer spriteLayer = null;
    private SwipeBackgroundDrawable bgDrawable = new SwipeBackgroundDrawable();

    // Video Element Specific
    private VideoView videoPlayer = null;
    private boolean fReady = false;
    private boolean fSeeking = false;
    private boolean fNeedRewind = false;
    private boolean fPlaying = false;
    private Float pendingOffset = null;
    private Float videoStart = 0.0f;
    private Float videoDuration = 1.0f;

    String getAction() { return action; }

    List<SwipeObjectAnimator> getAllAnimations() {
        List<SwipeObjectAnimator> allAni = new ArrayList<>();
        allAni.addAll(animations);
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                allAni.addAll(e.getAllAnimations());
            }
        }
        return allAni;
    }

    SwipeElement(Context _context, CGSize _dimension, CGSize _scrDimension, CGSize _scale, JSONObject _info, SwipeNode _parent, SwipeElement.Delegate _delegate) {
        super(_context, _dimension, _scrDimension, _scale, _info, _parent);
        delegate = _delegate;

        String template = info.optString("template", null);
        if (template == null) {
            template = info.optString("element", null);
            if (template != null) {
                SwipeUtil.Log(TAG, "DEPRECATED element; use 'template'");
            }
        }

        super.info = SwipeParser.inheritProperties(info, delegate.prototypeWithName(template));
    }


    public void release() {
        if (imageLayer != null) {
            imageLayer.release();
        }

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                e.release();
            }
        }
    }

    public void prepare() {
        if (imageLayer != null) {
            imageLayer.prepare();
        }

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                e.prepare();
            }
        }
    }

    @Override
    void createViewGroup() {
        viewGroup = new ViewGroup(getContext()) {
            private Paint debugPaint;

            {
                debugPaint = new Paint();
                debugPaint.setStyle(Paint.Style.STROKE);
                debugPaint.setColor(Color.MAGENTA);
                debugPaint.setStrokeWidth(3);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                /*
                Rect bounds = new Rect(getLeft(), getTop(), getRight(), getBottom());
                canvas.save();
                canvas.drawRect(bounds, debugPaint);
                canvas.restore();
                */
            }

            @Override
            public ViewOutlineProvider getOutlineProvider() {
                return null;
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                //SwipeUtil.Log(TAG, "onLayout");
                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    v.measure(View.MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY));
                    //SwipeUtil.Log(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height + " mw:" + v.getMeasuredWidth() + " mh:" + v.getMeasuredHeight());
                    v.layout(0, 0, lp.width, lp.height);
                }
            }
        };

        // No clipping of any sort by default
        viewGroup.setClipChildren(false);
        viewGroup.setClipToPadding(false);
        viewGroup.setClipToOutline(false);
        viewGroup.setClipBounds(null);
    }

    @Override
    ViewGroup loadView() {
        super.loadView();
        String id = info.optString("id");
        SwipeUtil.Log(TAG, "id: " + id, 2);

        setTimeOffsetTo(0);

        int bc = SwipeParser.parseColor(info, "bc", Color.TRANSPARENT);
        bgDrawable.setBackgroundColor(bc);
        viewGroup.setBackground(bgDrawable);

        float x = 0;
        float y = 0;
        float w0 = dimension.width;
        float h0 = dimension.height;
        boolean fNaturalW = true;
        boolean fNaturalH = true;

        boolean fScaleToFill = info.optString("w").equals("fill") || info.optString("h").equals("fill");
        if (fScaleToFill) {
            w0 = dimension.width; // we'll adjust it later
            h0 = dimension.height; // we'll adjust it later
        } else {
            Double dvalue = info.optDouble("w");
            if (!dvalue.isNaN()) {
                w0 = dvalue.floatValue();
                fNaturalW = false;
            } else {
                String value = info.optString("w", null);
                if (value != null) {
                    w0 = SwipeParser.parsePercent(value, dimension.width, dimension.width);
                    fNaturalW = false;
                }
            }
            dvalue = info.optDouble("h");
            if (!dvalue.isNaN()) {
                h0 = dvalue.floatValue();
                fNaturalH = false;
            } else {
                String value = info.optString("h", null);
                if (value != null) {
                    h0 = SwipeParser.parsePercent(value, dimension.height, dimension.height);
                    fNaturalH = false;
                }
            }
        }

        URL imageUrl = null;
        Bitmap imageBitmap = null;
        BitmapFactory.Options imageBitmapOptions = new BitmapFactory.Options();

        String imgUrlStr = info.optString("img", null);
        if (imgUrlStr != null) {
            URL url = delegate.makeFullURL(imgUrlStr);
            imageUrl = delegate.map(url);
            if (imageUrl != null) {
                FileInputStream stream = SwipeAssetManager.sharedInstance().loadLocalAsset(imageUrl);
                try {
                    if (stream != null){
                        imageBitmap = BitmapFactory.decodeStream(stream, null, imageBitmapOptions);
                        stream.close();
                    }
                } catch (OutOfMemoryError e) {
                    SwipeUtil.Log(TAG, "Out of memory " + url + " " + e);
                    Toast.makeText(getContext(), "Out of memory.  Use smaller image " + SwipeUtil.fileName(imageUrl.toString()), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // TODO: imageSrc = CGImageSourceCreateWithURL(url, nil)
            }
        }

        Bitmap maskBitmap = null;
        String maskUrlStr = info.optString("mask", null);
        if (maskUrlStr != null) {
            URL url = delegate.makeFullURL(maskUrlStr);
            URL localUrl = delegate.map(url);
            if (localUrl != null) {
                InputStream stream = SwipeAssetManager.sharedInstance().loadLocalAsset(localUrl);
                if (stream != null){
                    try {
                        maskBitmap = BitmapFactory.decodeStream(stream);
                        stream.close();
                    } catch (OutOfMemoryError e) {
                        SwipeUtil.Log(TAG, "Out of memory " + url + " " + e);
                        Toast.makeText(getContext(), "Out of memory.  Use smaller image " + SwipeUtil.fileName(localUrl.toString()), Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                // TODO: imageSrc = CGImageSourceCreateWithURL(url, nil)
            }
        }

        SwipePath path = parsePath(info.opt("path"), w0, h0, scale, dm);

        // The natural size is determined by the contents (either image or mask)
        CGSize sizeContents = null;
        if (imageBitmap != null) {
            sizeContents = new CGSize(imageBitmap.getWidth(), imageBitmap.getHeight());
        } else if (maskBitmap != null) {
            sizeContents = new CGSize(maskBitmap.getWidth(), imageBitmap.getHeight());
        } else  if (path != null) {
            RectF rc = new RectF();
            path.getPath().computeBounds(rc, false /* unused */);
            sizeContents = new CGSize((rc.left + rc.width()) / dm.density, (rc.top + rc.height()) / dm.density);
        }

        if (sizeContents != null) {
            if (fScaleToFill) {
                if (w0 / sizeContents.width * sizeContents.height > h0) {
                    h0 = w0 / sizeContents.width * sizeContents.height;
                } else {
                    w0 = h0 / sizeContents.height * sizeContents.width;
                }
            } else if (fNaturalW) {
                if (fNaturalH) {
                    w0 = sizeContents.width;
                    h0 = sizeContents.height;
                } else {
                    w0 = h0 / sizeContents.height * sizeContents.width;
                }
            } else {
                if (fNaturalH) {
                    h0 = w0 / sizeContents.width * sizeContents.height;
                }
            }
        }

        float w = w0 * scale.width;
        float h = h0 * scale.height;

        Double dvalue = info.optDouble("x");
        if (!dvalue.isNaN()){
            x = dvalue.floatValue();
        } else {
            String value = info.optString("x", null);
            if (value != null) {
                switch (value) {
                    case "right":
                        x = dimension.width - w0;
                        break;
                    case "left":
                        x = 0;
                        break;
                    case "center":
                        x = (dimension.width - w0) / 2.0f;
                        break;
                    default:
                        x = SwipeParser.parsePercent(value, dimension.width, 0);
                        break;
                }
            }
        }
        dvalue = info.optDouble("y");
        if (!dvalue.isNaN()){
            y = dvalue.floatValue();
        } else  {
            String value = info.optString("y", null);
            if (value != null) {
                switch (value) {
                    case "bottom":
                        y = dimension.height - h0;
                        break;
                    case "top":
                        y = 0;
                        break;
                    case "center":
                        y = (dimension.height - h0) / 2.0f;
                        break;
                    default:
                        y = SwipeParser.parsePercent(value, dimension.height, 0);
                        break;
                }
            }
        }
        //NSLog("SWEleme \(x),\(y),\(w0),\(h0),\(sizeContents),\(dimension),\(scale)")

        x *= scale.width;
        y *= scale.height;

        // TODO let view = InternalView(wrapper: self, frame: frame)
        // Convert DIP to PX
        final int dipX = px2Dip(x);
        final int dipY = px2Dip(y);
        final int dipW = px2Dip(w);
        final int dipH = px2Dip(h);
        final int dipZ = 0; // TODO
        viewGroup.setX(dipX);
        viewGroup.setY(dipY);
        viewGroup.setPivotX(dipW / 2);
        viewGroup.setPivotY(dipH / 2);
        viewGroup.setLayoutParams(new ViewGroup.LayoutParams(dipW, dipH));

        JSONArray anchorValues = info.optJSONArray("anchor");
        if (anchorValues != null && anchorValues.length() == 2 && w0 > 0 && h0 > 0) {
            float posx = SwipeParser.parsePercentAny(anchorValues.opt(0), w0, Float.NaN);
            float posy = SwipeParser.parsePercentAny(anchorValues.opt(1), h0, Float.NaN);
            if (!Float.isNaN(posx) && !Float.isNaN(posy)) {
                viewGroup.setPivotX(px2Dip(posx * scale.width));
                viewGroup.setPivotY(px2Dip(posy * scale.height));
            }
        }

        JSONArray posValues = info.optJSONArray("pos");
        if (posValues != null && posValues.length() == 2) {
            float posx = SwipeParser.parsePercentAny(posValues.opt(0), dimension.width, Float.NaN);
            float posy = SwipeParser.parsePercentAny(posValues.opt(1), dimension.height, Float.NaN);
            if (!Float.isNaN(posx) && !Float.isNaN(posy)) {
                viewGroup.setX(px2Dip(posx * scale.width) - viewGroup.getPivotX());
                viewGroup.setY(px2Dip(posy * scale.height) - viewGroup.getPivotY());
            }
        }

        action = info.optString("action", null);
        if (action != null) {
            Button btn = new Button(getContext());
            btn.setBackgroundColor(Color.TRANSPARENT);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SwipeUtil.Log(TAG, "buttonPressed", 1);
                    viewGroup.setAlpha(0);
                    delegate.onAction(SwipeElement.this);
                }
            });
            btn.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            SwipeUtil.Log(TAG, "touchDown", 1);
                            viewGroup.setAlpha(0.5f);
                            break;
                        case MotionEvent.ACTION_UP:
                            SwipeUtil.Log(TAG, "touchUp", 1);
                            viewGroup.setAlpha(1);
                            break;
                    }

                    return false;
                }
            });
            viewGroup.addView(btn, new ViewGroup.LayoutParams(dipW, dipH));

            if (action.equals("play")) {
                NotificationCenter.defaultCenter().addFunctionForNotification(SwipePage.didStartPlaying, new Runnable() {
                    @Override
                    public void run() {
                        viewGroup.setAlpha(0);
                    }
                });

                NotificationCenter.defaultCenter().addFunctionForNotification(SwipePage.didFinishPlaying, new Runnable() {
                    @Override
                    public void run() {
                        viewGroup.setAlpha(1);
                    }
                });
            }
        } else {
            JSONObject eventsInfo = info.optJSONObject("events");
            if (eventsInfo != null) {
                // TODO eventHandler.parse(eventsInfo)
            }
        }

        fEnabled = info.optBoolean("enabled", true);
        fFocusable = info.optBoolean("focusable", false);

        fClip = info.optBoolean("clip", false);
        if (fClip) {
            viewGroup.setClipToOutline(true); // TODO CLIP works with paths but not nested images :(
        }

        if (imageBitmap != null) {
            imageLayer = new SwipeImageLayer(getContext());
            imageLayer.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageLayer.setImageBitmap(imageBitmap);
            imageLayer.setMaskBitmap(maskBitmap);
            imageLayer.setImageURL(imageUrl, imageBitmapOptions, this);

            if (parent != null && parent instanceof SwipeElement) {
                SwipeElement p = (SwipeElement)parent;
                if (p.fClip) {
                    imageLayer.setIgnoreRelease(true);
                    p.viewGroup.setClipToOutline(false);  // ToDo CLIP doesn't work with images
                    p.viewGroup.setClipBounds(new Rect(0, 0, p.viewGroup.getLayoutParams().width, p.viewGroup.getLayoutParams().height));
                }
            }

            if (!info.optBoolean("tiling", false)) {
                viewGroup.addView(imageLayer, new ViewGroup.LayoutParams(dipW, dipH));
            } else {
                imageLayer.setIgnoreRelease(true);
                ViewGroup hostLayer = new ViewGroup(getContext()) {
                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        //SwipeUtil.Log(TAG, "onLayout");
                        for (int c = 0; c < this.getChildCount(); c++) {
                            View v = this.getChildAt(c);
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            //SwipeUtil.Log(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
                            v.layout(0, 0, lp.width, lp.height);
                        }
                    }
                };

                innerLayer = hostLayer;
                hostLayer.addView(imageLayer, new ViewGroup.LayoutParams(dipW, dipH));
                hostLayer.setClipBounds(new Rect(0, 0, dipW, dipH));
                viewGroup.addView(hostLayer, new ViewGroup.LayoutParams(dipW, dipH));

                float[] xs = new float[]{ -dipW, dipW, 0, 0 };
                float[] ys = new float[]{ 0, 0, -dipH, dipH };

                for (int i = 0; i < xs.length; i++){
                    ImageView subLayer = new ImageView(getContext());
                    subLayer.setImageBitmap(imageBitmap);
                    subLayer.setX(xs[i]);
                    subLayer.setY(ys[i]);
                    hostLayer.addView(subLayer, new ViewGroup.LayoutParams(dipW, dipH));
                }
            }
        }

        String spriteSrc = info.optString("sprite", null);
        JSONArray sliceInfo = info.optJSONArray("slice");
        if (spriteSrc != null && sliceInfo != null) {
            Point slot = new Point(0, 0);
            JSONArray values = info.optJSONArray("slot");
            if (values != null && values.length() == 2) {
                slot = new Point(values.optInt(0, slot.x), values.optInt(1, slot.y));
            }

            CGSize slice = new CGSize(1, 1);
            if (sliceInfo.length() > 0) {
                slice.width = sliceInfo.optInt(0, (int)slice.width);
                if (sliceInfo.length() > 1) {
                    slice.height = sliceInfo.optInt(1, (int)slice.height);
                }
            }

            URL url = delegate.makeFullURL(spriteSrc);
            URL localUrl = delegate.map(url);
            if (localUrl != null) {
                InputStream stream = SwipeAssetManager.sharedInstance().loadLocalAsset(localUrl);
                if (stream != null){
                    Bitmap spriteBitmap = BitmapFactory.decodeStream(stream);
                    if (spriteBitmap != null) {
                        spriteLayer = new SwipeSpriteLayer(getContext(), spriteBitmap, slice, slot);
                        spriteLayer.setClipBounds(new Rect(0, 0, dipW, dipH));
                        viewGroup.addView(spriteLayer, new ViewGroup.LayoutParams(dipW, dipH));
                    }
                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        videoDuration =  (float) info.optDouble("videoDuration", videoDuration);
        videoStart = (float) info.optDouble("videoStart", videoStart);

        bgDrawable.setCornerRadius(px2Dip((float) info.optDouble("cornerRadius", 0) * scale.width));
        bgDrawable.setBorderWidth(px2Dip((float) info.optDouble("borderWidth", 0) * scale.width));
        bgDrawable.setBorderColor(SwipeParser.parseColor(info.opt("borderColor"), Color.BLACK));

        if (path != null) {
            shapeLayer = new SwipeShapeLayer(getContext(), path);
            Matrix xform = SwipePath.parsePathTransform(info, dipW, dipH);
            if (xform != null) {
                shapeLayer.setPathTransform(xform);
            }
            shapeLayer.setFillColor(SwipeParser.parseColor(info, "fillColor", Color.TRANSPARENT));
            shapeLayer.setStrokeColor(SwipeParser.parseColor(info, "strokeColor", Color.BLACK));
            shapeLayer.setLineWidth(px2Dip(SwipeParser.parseFloat(info, "lineWidth", 0) * scale.width));

            JSONObject shadowInfo = info.optJSONObject("shadow");
            if (shadowInfo != null) {
                CGSize shadowOffset = SwipeParser.parseSize(shadowInfo.opt("offset"), new CGSize(1, 1), scale);
                float shadowRadius = SwipeParser.parseFloat(shadowInfo.optDouble("radius"), 1) * scale.width;
                float shadowOpacity = SwipeParser.parseFloat(shadowInfo.optDouble("opacity"), 0.5f);
                int shadowColor = SwipeParser.parseColor(shadowInfo.opt("color"), Color.BLACK);
                shadowColor = Color.argb((int) (Color.alpha(shadowColor) * shadowOpacity), Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor));
                shapeLayer.setShadowRadius(px2Dip(shadowRadius));
                shapeLayer.setShadowOffset(new CGSize(px2Dip(shadowOffset.width), px2Dip(shadowOffset.height)));
                shapeLayer.setShadowColor(shadowColor);
            }

            shapeLayer.setLineCap(Paint.Cap.ROUND);
            shapeLayer.setStrokeStart(SwipeParser.parseFloat(info, "strokeStart", 0));
            shapeLayer.setStrokeEnd(SwipeParser.parseFloat(info, "strokeEnd", 1));

            if (info.optBoolean("tiling", false)) {
                ViewGroup hostLayer = new ViewGroup(getContext()) {
                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        //SwipeUtil.Log(TAG, "onLayout");
                        for (int c = 0; c < this.getChildCount(); c++) {
                            View v = this.getChildAt(c);
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            //SwipeUtil.Log(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
                            v.layout(0, 0, lp.width, lp.height);
                        }
                    }
                };

                innerLayer = hostLayer;
                hostLayer.addView(shapeLayer, new ViewGroup.LayoutParams(dipW, dipH));
                hostLayer.setClipBounds(new Rect(0, 0, dipW, dipH));
                viewGroup.addView(hostLayer, new ViewGroup.LayoutParams(dipW, dipH));

                float[] xs = new float[]{ -dipW, dipW, 0, 0 };
                float[] ys = new float[]{ 0, 0, -dipH, dipH };

                for (int i = 0; i < xs.length; i++){
                    SwipeShapeLayer subLayer = new SwipeShapeLayer(getContext(), shapeLayer.getPath());
                    subLayer.setFillColor(shapeLayer.getFillColor());
                    subLayer.setStrokeColor(shapeLayer.getStrokeColor());
                    subLayer.setLineWidth(shapeLayer.getLineWidth());
                    subLayer.setShadowColor(shapeLayer.getShadowColor());
                    subLayer.setShadowOffset(shapeLayer.getShadowOffset());
                    subLayer.setShadowRadius(shapeLayer.getShadowRadius());
                    subLayer.setLineCap( shapeLayer.getLineCap());
                    subLayer.setStrokeStart(shapeLayer.getStrokeStart());
                    subLayer.setStrokeEnd(shapeLayer.getStrokeEnd());
                    subLayer.setX(xs[i]);
                    subLayer.setY(ys[i]);
                    hostLayer.addView(subLayer, new ViewGroup.LayoutParams(dipW, dipH));
                }
            } else {
                viewGroup.addView(shapeLayer, new ViewGroup.LayoutParams(dipW, dipH));
            }
        } else {
            //    SwipeElement.processShadow(info, scale:scale, layer: layer)
        }

        List<SwipeMarkdown.Element> markdown = delegate.parseMarkdown(info.opt("markdown"));
        if (markdown != null && markdown.size() > 0) {
            ViewGroup wrapper = new ViewGroup(getContext()) {
                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    setClipChildren(false);
                    for (int c = 0; c < this.getChildCount(); c++) {
                        View v = this.getChildAt(c);
                        ViewGroup.LayoutParams lp = v.getLayoutParams();
                        //SwipeUtil.Log(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
                        v.layout(0, 0, lp.width, lp.height);
                    }
                }
            };

            float nextY = 0;
            for (SwipeMarkdown.Element e : markdown) {
                TextView tv = new TextView(getContext());
                tv.setText(e.text);
                tv.setTextSize(e.fontSize);
                tv.setTextColor(e.textColor);
                tv.setGravity(e.textAlignment);
                tv.setX(0);
                tv.setY(nextY);
                tv.measure(View.MeasureSpec.makeMeasureSpec(dipW, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dipH, View.MeasureSpec.AT_MOST));
                int mh = tv.getMeasuredHeight();
                nextY = nextY + mh + e.lineSpacing;
                wrapper.addView(tv, new ViewGroup.LayoutParams(dipW, mh));
            }

            wrapper.setY((dipH - nextY)/2); // center vertically in self
            viewGroup.addView(wrapper, new ViewGroup.LayoutParams(dipW, (int)nextY));
        }

        /* TODO
        if let value = info["textArea"] as? [String:AnyObject] {
            let textView = SwipeTextArea(parent: self, info: value, frame: view.bounds, screenDimension: self.screenDimension)
            helper = textView
            view.addSubview(helper!.view!)
        } else if let value = info["textField"] as? [String:AnyObject] {
            let textView = SwipeTextField(parent: self, info: value, frame: view.bounds, screenDimension: self.screenDimension)
            helper = textView
            view.addSubview(helper!.view!)
        } else if let value = info["list"] as? [String:AnyObject] {
            let list = SwipeList(parent: self, info: value, scale:self.scale, frame: view.bounds, screenDimension: self.screenDimension, delegate: self.delegate)
            helper = list
            view.addSubview(list.tableView)
            list.tableView.reloadData()
        }
        */
        String text = parseText(this, info, "text");
        if (text != null){
            // TODO if self.helper == nil || !self.helper!.setText(text, scale:self.scale, info: info, dimension:screenDimension, layer: layer) {
                textLayer = SwipeTextLayer.parse(getContext(), text, info, scale, scrDimension);
                if (textLayer != null) {
                    textLayer.measure(View.MeasureSpec.makeMeasureSpec(dipW, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(dipH, View.MeasureSpec.EXACTLY));
                    viewGroup.addView(textLayer, new ViewGroup.LayoutParams(dipW, dipH));
                }
            //    SwipeElement.processShadow(info, scale:scale, layer: layer)
            //}
        }

        boolean fStream = info.optBoolean("stream", false);

        String mediaSrc = info.optString("video", null);
        if (mediaSrc == null) {
            mediaSrc = info.optString("radio", null);
        }

        if (mediaSrc != null) {
            videoPlayer = new VideoView(getContext());
            videoPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    SwipeUtil.Log(TAG, "onError what:" + what + " extra:" + extra);
                    return true;
                }
            });

            videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    SwipeUtil.Log(TAG, "prepared w:" + mp.getVideoWidth() + " h:" + mp.getVideoHeight());
                    videoPlayer.measure(View.MeasureSpec.makeMeasureSpec(dipW, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(dipH, View.MeasureSpec.AT_MOST));
                    SwipeUtil.Log(TAG, "vp mw:" + videoPlayer.getWidth() + " mh:" + videoPlayer.getHeight());
                    fReady = true;
                }
            });

            videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    SwipeUtil.Log(TAG, "play to end!", 1);
                    if (delegate != null && delegate.shouldRepeat(SwipeElement.this)) {
                        videoPlayer.seekTo(0);
                        videoPlayer.start();
                        ;
                    } else {
                        fNeedRewind = true;
                        if (fPlaying) {
                            fPlaying = false;
                            delegate.didFinishPlaying(SwipeElement.this, true);
                        }
                    }

                }
            });

            URL mediaUrl = delegate.makeFullURL(mediaSrc);
            URL localUrl = delegate.map(mediaUrl);
            if (localUrl != null) {
                mediaUrl = localUrl;
            }
            videoPlayer.setVideoURI(Uri.parse(mediaUrl.toString()));

            RelativeLayout rl = new RelativeLayout(getContext());
            rl.setBackgroundColor(Color.GREEN);
            RelativeLayout.LayoutParams lp;

            if (fScaleToFill) {
                lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            } else {
                lp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            }

            rl.addView(videoPlayer, lp);
            viewGroup.addView(rl, new ViewGroup.LayoutParams(dipW, dipH));

            NotificationCenter center = NotificationCenter.defaultCenter();
            center.addFunctionForNotification(SwipePage.shouldPauseAutoPlay, new Runnable() {
                @Override
                public void run() {
                    if (fPlaying) {
                        SwipeUtil.Log(TAG, "shouldPauseAutoPlay", 2);
                        fPlaying = false;
                        delegate.didFinishPlaying(SwipeElement.this, false);
                        videoPlayer.pause();
                    }
                }
            });
            center.addFunctionForNotification(SwipePage.shouldStartAutoPlay, new Runnable() {
                @Override
                public void run() {
                    if (!fPlaying && delegate.isCurrentPage()) {
                        SwipeUtil.Log(TAG, "shouldStartAutoPlay", 2);
                        fPlaying = true;
                        delegate.didStartPlaying(SwipeElement.this);
                        if (fNeedRewind) {
                            videoPlayer.seekTo(0);
                        }
                        videoPlayer.start();
                        fNeedRewind = false;
                    }
                }
            });
        }

        Double dopt;

        JSONObject transform = SwipeParser.parseTransform(info, null, false, shapeLayer != null);
        if (transform != null) {
            dopt = transform.optDouble("rotate");
            if (!dopt.isNaN()) {
                viewGroup.setRotation(dopt.floatValue());
            }

            JSONArray rots = transform.optJSONArray("rotate");
            if (rots != null && rots.length() == 3) {
                Double rot0 = rots.optDouble(0);
                Double rot1 = rots.optDouble(1);
                Double rot2 = rots.optDouble(2);
                if (!rot0.isNaN()) {
                    viewGroup.setRotationX(rot0.floatValue());
                }

                if (!rot1.isNaN()) {
                    viewGroup.setRotationX(rot1.floatValue());
                }

                if (!rot2.isNaN()) {
                    viewGroup.setRotation(rot2.floatValue());
                }
            }

            dopt = transform.optDouble("scale");
            if (!dopt.isNaN()) {
                viewGroup.setScaleX(dopt.floatValue());
                viewGroup.setScaleY(dopt.floatValue());
            }

            JSONArray scales = transform.optJSONArray("scale");
            if (scales != null && scales.length() == 2) {
                Double d0 = scales.optDouble(0);
                Double d1 = scales.optDouble(1);
                if (!d0.isNaN()) {
                    viewGroup.setScaleX(d0.floatValue());
                }

                if (!d1.isNaN()) {
                    viewGroup.setScaleY(d1.floatValue());
                }
            }

            JSONArray translate = transform.optJSONArray("translate");
            if (translate != null && translate.length() >= 2) {
                Double translate0 = translate.optDouble(0);
                Double translate1 = translate.optDouble(1);
                Double translate2 = translate.optDouble(2);
                if (!translate0.isNaN()) {
                    viewGroup.setTranslationX(px2Dip(translate0.floatValue() * scale.width) + dipX);
                }
                if (!translate1.isNaN()) {
                    viewGroup.setTranslationY(px2Dip(translate1.floatValue() * scale.height) + dipY);
                }
                if (!translate2.isNaN()) {
                    viewGroup.setTranslationZ(px2Dip(translate2.floatValue() * scale.height) + dipZ);
                }
            }
        }

        float opacity = SwipeParser.parseFloat(info.opt("opacity"), 1);
        if (!info.optBoolean("visible", true)) {
            opacity = 0;
        }

        if (shapeLayer != null) {
            // See SwipeShapeLayer comments
            shapeLayer.setAlpha(opacity);
        } else {
            viewGroup.setAlpha(opacity);
        }

        final int aniCnt = animations.size();
        boolean aniAlpha = false;

        JSONObject to = info.optJSONObject("to");
        if (to != null) {
            double start = 0;
            double duration = 1.0;

            JSONArray timingJA = to.optJSONArray("timing");
            if (timingJA != null && (timingJA.length() == 2)) {
                double timing[] = { timingJA.optDouble(0), timingJA.optDouble(1) };
                if (!Double.isNaN(timing[0]) && !Double.isNaN(timing[1]) && timing[0] >= 0 && timing[0] <= timing[1] && timing[1] <= 1) {
                    start = timing[0] == 0 ? 0 : timing[0];
                    duration = timing[1] - start;
                }
            }

            SwipePath posPath = parsePath(to.opt("pos"), w0, h0, scale, dm);

            transform = SwipeParser.parseTransform(to, info, posPath != null, shapeLayer != null);
            if (transform != null) {
                dopt = transform.optDouble("rotate");
                if (!dopt.isNaN()) {
                    ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "rotation", dopt.floatValue());
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }

                JSONArray rots = transform.optJSONArray("rotate");
                if (rots != null && rots.length() == 3) {
                    Double rot0 = rots.optDouble(0);
                    Double rot1 = rots.optDouble(1);
                    Double rot2 = rots.optDouble(2);
                    if (!rot0.isNaN()) {
                        ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "rotationX", rot0.floatValue());
                        animations.add(new SwipeObjectAnimator(ani, start, duration));
                    }

                    if (!rot1.isNaN()) {
                        ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "rotationY", rot1.floatValue());
                        animations.add(new SwipeObjectAnimator(ani, start, duration));
                    }

                    if (!rot2.isNaN()) {
                        ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "rotation", rot2.floatValue());
                        animations.add(new SwipeObjectAnimator(ani, start, duration));
                    }
                }

                if (shapeLayer == null) {
                    dopt = transform.optDouble("scale");
                    if (!dopt.isNaN()) {
                        ObjectAnimator aniX = ObjectAnimator.ofFloat(viewGroup, "scaleX", dopt.floatValue());
                        animations.add(new SwipeObjectAnimator(aniX, start, duration));
                        ObjectAnimator aniY = ObjectAnimator.ofFloat(viewGroup, "scaleY", dopt.floatValue());
                        animations.add(new SwipeObjectAnimator(aniY, start, duration));
                    }

                    JSONArray scales = transform.optJSONArray("scale");
                    if (scales != null && scales.length() == 2) {
                        Double d0 = scales.optDouble(0);
                        Double d1 = scales.optDouble(1);
                        if (!d0.isNaN()) {
                            ObjectAnimator aniX = ObjectAnimator.ofFloat(viewGroup, "scaleX", d0.floatValue());
                            animations.add(new SwipeObjectAnimator(aniX, start, duration));
                        }

                        if (!d1.isNaN()) {
                            ObjectAnimator aniY = ObjectAnimator.ofFloat(viewGroup, "scaleY", d1.floatValue());
                            animations.add(new SwipeObjectAnimator(aniY, start, duration));
                        }
                    }
                }

                if (posPath == null) {
                    JSONArray translate = transform.optJSONArray("translate");
                    if (translate != null && translate.length() >= 2) {
                        Double translate0 = translate.optDouble(0);
                        Double translate1 = translate.optDouble(1);
                        Double translate2 = translate.optDouble(2);

                        if (!translate0.isNaN()) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "translationX", px2Dip(translate0.floatValue() * scale.width) + dipX);
                            animations.add(new SwipeObjectAnimator(ani, start, duration));
                        }
                        if (!translate1.isNaN()) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "translationY", px2Dip(translate1.floatValue() * scale.height) + dipY);
                            animations.add(new SwipeObjectAnimator(ani, start, duration));
                        }
                        if (!translate2.isNaN()) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "translationZ", px2Dip(translate2.floatValue() * scale.height) + dipZ);
                            animations.add(new SwipeObjectAnimator(ani, start, duration));
                        }
                    }
                }
            }

            if (posPath != null){
                Matrix xform = new Matrix();
                xform.setTranslate(viewGroup.getTranslationX(), viewGroup.getTranslationY());  // Default if no translate
                if (transform != null) {
                    JSONArray translate = transform.optJSONArray("translate");
                    if (translate != null && translate.length() >= 2) {
                        Double translate0 = translate.optDouble(0);
                        Double translate1 = translate.optDouble(1);
                        //Double translate2 = translate.optDouble(2);

                        if (!translate0.isNaN() && !translate1.isNaN()) {
                            xform = new Matrix();
                            xform.setTranslate(px2Dip(translate0.floatValue() * scale.width) + dipX, px2Dip(translate1.floatValue() * scale.height) + dipY);
                        }
                    }
                }

                posPath.getPath().transform(xform);
                ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "x", "y", posPath.getPath());
                animations.add(new SwipeObjectAnimator(ani, start, duration));

                String mode = to.optString("mode");
                switch(mode) {
                    case "auto": {
                        ObjectAnimator aniR = ObjectAnimator.ofObject(viewGroup, "rotation", new SwipePath.RotationEvaluator(posPath.getPath()), 0);
                        animations.add(new SwipeObjectAnimator(aniR, start, duration));
                        break;
                    }
                    case "reverse": {
                        ObjectAnimator aniR = ObjectAnimator.ofObject(viewGroup, "rotation", new SwipePath.ReverseRotationEvaluator(posPath.getPath()), 0);
                        animations.add(new SwipeObjectAnimator(aniR, start, duration));
                        break;
                    }
                    default: // or "none"
                        break;
                }
            }

            dopt = to.optDouble("opacity");
            if (!dopt.isNaN()){
                Object obj = viewGroup;
                if (shapeLayer != null) {
                    // See SwipeShapeLayer comments
                    obj = shapeLayer;
                }
                ObjectAnimator ani = ObjectAnimator.ofFloat(obj, "alpha",  dopt.floatValue());
                animations.add(new SwipeObjectAnimator(ani, start, duration));
                aniAlpha = true;
            }

            if (to.has("bc")) {
                ObjectAnimator ani = ObjectAnimator.ofObject(bgDrawable, "backgroundColor", new ArgbEvaluator(), bgDrawable.getBackgroundColor(), SwipeParser.parseColor(to, "bc", Color.TRANSPARENT));
                animations.add(new SwipeObjectAnimator(ani, start, duration));
            }

            Object opt = to.opt("borderColor");
            if (opt != null){
                ObjectAnimator ani = ObjectAnimator.ofObject(bgDrawable, "borderColor", new ArgbEvaluator(), bgDrawable.getBorderColor(), SwipeParser.parseColor(opt));
                animations.add(new SwipeObjectAnimator(ani, start, duration));
            }

            dopt = to.optDouble("borderWidth");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(bgDrawable, "borderWidth",  px2Dip(dopt.floatValue() * scale.width));
                animations.add(new SwipeObjectAnimator(ani, start, duration));
            }
            dopt = to.optDouble("cornerRadius");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(bgDrawable, "cornerRadius",  px2Dip(dopt.floatValue() * scale.width));
                animations.add(new SwipeObjectAnimator(ani, start, duration));
            }

            /*
            if let textLayer = self.textLayer {
                if let textColor:AnyObject = to["textColor"] {
                    let ani = CABasicAnimation(keyPath: "foregroundColor")
                    ani.fromValue = textLayer.foregroundColor
                    ani.toValue = SwipeParser.parseColor(textColor)
                    ani.beginTime = start
                    ani.duration = duration
                    ani.fillMode = kCAFillModeBoth
                    textLayer.addAnimation(ani, forKey: "foregroundColor")
                }
            }
            if let srcs = to["img"] as? [String] {
                var images = [CGImage]()
                for src in srcs {
                    if let url = NSURL.url(src, baseURL: baseURL),
                    urlLocal = self.delegate.map(url),
                            image = CGImageSourceCreateWithURL(urlLocal, nil) {
                        if CGImageSourceGetCount(image) > 0 {
                            images.append(CGImageSourceCreateImageAtIndex(image, 0, nil)!)
                        }
                    }
                    //if let image = SwipeParser.imageWith(src) {
                    //images.append(image.CGImage!)
                    //}
                }
                if let imageLayer = self.imageLayer {
                    let ani = CAKeyframeAnimation(keyPath: "contents")
                    ani.values = images
                    ani.beginTime = start
                    ani.duration = duration
                    ani.fillMode = kCAFillModeBoth
                    imageLayer.addAnimation(ani, forKey: "contents")
                }
            }
            */

            if (shapeLayer != null) {
                JSONArray params = to.optJSONArray("path");
                if (params != null) {
                    /* ToDo
                    var values = [shapeLayer.path!]
                    for param in params {
                        if let path = parsePath(param, w: w0, h: h0, scale:scale) {
                            values.append(path)
                        }
                    }
                    let ani = CAKeyframeAnimation(keyPath: "path")
                    ani.values = values
                    ani.beginTime = start
                    ani.duration = duration
                    ani.fillMode = kCAFillModeBoth
                    shapeLayer.addAnimation(ani, forKey: "path")
                    */
                } else {
                    SwipePath toPath = parsePath(to.opt("path"), w0, h0, scale, dm);
                    if (toPath != null) {
                         ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "path", new SwipePath.Evaluator(), toPath);
                        animations.add(new SwipeObjectAnimator(ani, start, duration));
                    } else {
                        class MatrixEvaluator implements TypeEvaluator<Matrix> {
                            public Matrix evaluate(float fraction, Matrix startValue, Matrix endValue) {
                                float[] startEntries = new float[9];
                                float[] endEntries = new float[9];
                                float[] currentEntries = new float[9];

                                startValue.getValues(startEntries);
                                endValue.getValues(endEntries);

                                for (int i=0; i<9; i++) {
                                    currentEntries[i] = (1 - fraction) * startEntries[i] + fraction * endEntries[i];
                                }
                                Matrix matrix = new Matrix();
                                matrix.setValues(currentEntries);
                                return matrix;
                            }
                        }

                        Matrix toMatrix = SwipePath.parsePathTransform(to, dipW, dipH);
                        if (toMatrix != null) {
                            ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "pathTransform", new MatrixEvaluator(), toMatrix);
                            animations.add(new SwipeObjectAnimator(ani, start, duration));
                        }
                    }
                }

                opt = to.opt("fillColor");
                if (opt != null){
                    ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "fillColor", new ArgbEvaluator(), SwipeParser.parseColor(opt));
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }
                opt = to.opt("strokeColor");
                if (opt != null){
                    ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "strokeColor", new ArgbEvaluator(), SwipeParser.parseColor(opt));
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }

                dopt = to.optDouble("lineWidth");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "lineWidth",  px2Dip(dopt.floatValue() * scale.width));
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }
                dopt = to.optDouble("strokeStart");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "strokeStart", dopt.floatValue());
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }
                dopt = to.optDouble("strokeEnd");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "strokeEnd", dopt.floatValue());
                    animations.add(new SwipeObjectAnimator(ani, start, duration));
                }
            }
        }

        fRepeat = info.optBoolean("repeat", false);

        JSONObject animation = info.optJSONObject("loop");
        if (animation != null) {
            String style = animation.optString("style", null);
            if (style != null) {
                //
                // (iOS) Note:  Use the inner layer (either image or shape) for the loop animation
                // to avoid any conflict with other transformation if it is available.
                // In this case, the loop animation does not effect child elements (because
                // we use UIView hierarchy instead of CALayer hierarchy.
                //
                // (iOS) It means the loop animation on non-image/non-shape element does not work well
                // with other transformation.
                //

                View loopLayer = viewGroup;
                if (imageLayer != null) {
                    loopLayer = imageLayer;
                } else if (shapeLayer != null) {
                    loopLayer = shapeLayer;
                }

                double start = 0;
                double duration = 1.0;

                JSONArray timing = animation.optJSONArray("timing");
                if (timing != null && timing.length() == 2) {
                    Double t0 = timing.optDouble(0);
                    Double t1 = timing.optDouble(1);
                    if (!t0.isNaN() && !t1.isNaN() && t0 >= 0 && t0 <= t1 && t1 <= 1) {
                        start = t0 == 0 ? 0 : t0;
                        duration = t1 - start;
                    }
                }

                final int repeatCount = SwipeParser.parseInt(animation.opt("count"), 1);
                final double repeatInterval = duration / repeatCount;

                switch (style) {
                    case "shift":
                        if (innerLayer == null) {
                            for (int r = 0; r < repeatCount; r++) {
                                ObjectAnimator ani;
                                String dir = animation.optString("direction");
                                switch (dir) {
                                    case "n":
                                        ani = ObjectAnimator.ofFloat(viewGroup, "translationY", viewGroup.getY(), viewGroup.getY() - dipH);
                                        break;
                                    case "e":
                                        ani = ObjectAnimator.ofFloat(viewGroup, "translationX", viewGroup.getX(), viewGroup.getX() + dipW);
                                        break;
                                    case "w":
                                        ani = ObjectAnimator.ofFloat(viewGroup, "translationX", viewGroup.getX(), viewGroup.getX() - dipH);
                                        break;
                                    default:
                                        ani = ObjectAnimator.ofFloat(viewGroup, "translationY", viewGroup.getY(), viewGroup.getY() + dipH);
                                        break;
                                }

                                animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                            }
                        } else {
                            for (int r = 0; r < repeatCount; r++) {
                                for (int i = 0; i < innerLayer.getChildCount(); i++) {
                                    View subLayer = innerLayer.getChildAt(i);
                                    ObjectAnimator ani;
                                    String dir = animation.optString("direction");
                                    switch (dir) {
                                        case "n":
                                            ani = ObjectAnimator.ofFloat(subLayer, "translationY", subLayer.getY(), subLayer.getY() - dipH);
                                            break;
                                        case "e":
                                            ani = ObjectAnimator.ofFloat(subLayer, "translationX", subLayer.getX(), subLayer.getX() + dipW);
                                            break;
                                        case "w":
                                            ani = ObjectAnimator.ofFloat(subLayer, "translationX", subLayer.getX(), subLayer.getX() - dipH);
                                            break;
                                        default:
                                            ani = ObjectAnimator.ofFloat(subLayer, "translationY", subLayer.getY(), subLayer.getY() + dipH);
                                            break;
                                    }

                                    animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                                }
                            }
                        }
                        break;
                    case "vibrate": {
                        final float delta = px2Dip(SwipeParser.parseFloat(animation.opt("delta"), 10));
                        final float tx = loopLayer.getTranslationX();
                        for (int r = 0; r < repeatCount; r++) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(loopLayer, "translationX", tx, tx + delta, tx - delta, tx);
                            animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                        }
                        break;
                    }
                    case "blink": {
                        String propertyName = "alpha";

                        if (shapeLayer == loopLayer) {
                            propertyName = "blink";
                        }

                        for (int r = 0; r < repeatCount; r++) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(loopLayer, propertyName, 1f, 0f, 1f);
                            animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                        }
                        aniAlpha = true;
                        break;
                    }
                    case "wiggle": {
                        final float degree = SwipeParser.parseFloat(animation.opt("delta"), 15);
                        final float rot = loopLayer.getRotation();
                        for (int r = 0; r < repeatCount; r++) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(loopLayer, "rotation", rot, rot + degree, loopLayer.getRotation(), rot - degree, rot);
                            animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                        }
                        break;
                    }
                    case "spin": {
                        final boolean fClockwise = animation.optBoolean("clockwise", true);
                        final int degree = (fClockwise ? 120 : -120);
                        final float startRotation = loopLayer.getRotation();
                        for (int r = 0; r < repeatCount; r++) {
                            ObjectAnimator ani = ObjectAnimator.ofFloat(loopLayer, "rotation", loopLayer.getRotation(), degree, degree * 2, degree * 3);
                            animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                        }
                        break;
                    }
                    case "path": {
                        /*
                        if let shapeLayer = self.shapeLayer {
                            var values =[shapeLayer.path !]
                            if let params = animation["path"] as ?[AnyObject]{
                                for param in params {
                                    if let path = parsePath(param, w:w0, h:h0, scale:scale){
                                        values.append(path)
                                    }
                                }
                            }else if let path = parsePath(animation["path"], w:w0, h:h0, scale:
                            scale){
                                values.append(path)
                            }
                            if values.count >= 2 {
                                values.append(shapeLayer.path !)
                                let ani = CAKeyframeAnimation(keyPath:"path")
                                ani.values = values
                                ani.repeatCount = repeatCount
                                ani.beginTime = start
                                ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                                ani.fillMode = kCAFillModeBoth
                                shapeLayer.addAnimation(ani, forKey:"path")
                            }
                        }
                        break;
                        */
                    }
                    case "sprite":
                        if (spriteLayer != null) {
                            for (int r = 0; r < repeatCount; r++) {
                                ObjectAnimator ani = ObjectAnimator.ofFloat(spriteLayer, "animationPercent", 0, 1);
                                animations.add(new SwipeObjectAnimator(ani, start + (r * repeatInterval), repeatInterval));
                            }
                        }

                        break;
                }
            }
        }


        if (aniCnt != animations.size() && !aniAlpha) {
            // animating.  optimize
            if (imageLayer != null) {
                imageLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }

        // Nested Elements
        JSONArray elementsInfo = info.optJSONArray("elements");
        if (elementsInfo != null) {
            for (int e = 0; e < elementsInfo.length(); e++) {
                SwipeElement element = new SwipeElement(getContext(), new CGSize(w0, h0), scrDimension, scale, elementsInfo.optJSONObject(e), this, delegate);
                viewGroup.addView(element.loadView());
                children.add(element);
            }
        }

        /*
        setupGestureRecognizers()

        if let actions = eventHandler.actionsFor("load") {
            execute(self, actions: actions)
        }
        */

        return viewGroup;
    }

    public void onGifLoaded(SwipeImageLayer gifLayer) {
        ObjectAnimator ani = ObjectAnimator.ofFloat(gifLayer, "animationPercent", 0, 1);
        animations.add(new SwipeObjectAnimator(ani, 0, 1));
    }

    void resetAnimation(final boolean fForward) {
        for (SwipeObjectAnimator ani : animations) {
            ani.reset(fForward);
        }

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement element = (SwipeElement) c;
                element.resetAnimation(fForward);
            }
        }

        viewGroup.invalidate();

        if (videoPlayer != null) {
            final float offset = fForward ? 0 : 1;
            if (fSeeking) {
                pendingOffset = offset;
                return;
            }
            if (fReady) {
                //fSeeking = true;
                int timeMsec = (int)((videoStart + offset * videoDuration) * 1000);
                videoPlayer.seekTo(timeMsec);
                /*videoPlayer.setOnSeekListener(
                    assert(NSThread.currentThread() == NSThread.mainThread(), "thread error")
                    SwipeElement.objectCount += 1
                    self.fSeeking = false
                    if let pendingOffset = self.pendingOffset {
                        self.pendingOffset = nil
                        self.setTimeOffsetTo(pendingOffset, fAutoPlay: false, fElementRepeat: fElementRepeat)
                    }
                */
            }
        }
    }

    void setTimeOffsetTo(final float offset) {
        setTimeOffsetTo(offset, false, false);
    }

    void setTimeOffsetTo(final float offset, final boolean fResetForRepeat, final boolean fAutoPlay) {
        if (offset < 0.0 || offset > 1.0) {
            return;
        }

        if ((!fRepeat && !fResetForRepeat) || (fRepeat)) {
            for (SwipeObjectAnimator ani : animations) {
                ani.setCurrentFraction(offset);
                SwipeUtil.Log(TAG, "ani.setCurrentFraction("+offset+")", 10);
            }
        } else {
            //SwipeUtil.Log(TAG, "ani.setCurrentFraction("+offset+") SKIPPED", 1);
        }

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement element = (SwipeElement) c;
                element.setTimeOffsetTo(offset, fResetForRepeat, fAutoPlay);
            }
        }

        //viewGroup.invalidate();

        if (videoPlayer != null) {
            if (fAutoPlay) {
                return;
            }
            if (fSeeking) {
                pendingOffset = offset;
                return;
            }

            if (fReady) {
                int timeMsec = (int)((videoStart + offset * videoDuration) * 1000);
                SwipeUtil.Log(TAG, "seekTo: " + timeMsec + " of " + (int)(videoDuration * 1000));
                videoPlayer.seekTo(timeMsec);
                videoPlayer.invalidate();
                /* TODO
                if player.status == AVPlayerStatus.ReadyToPlay {
                    self.fSeeking = true
                    SwipeElement.objectCount -= 1 // to avoid false memory leak detection
                    player.seekToTime(time, toleranceBefore: tolerance, toleranceAfter: tolerance) { (_:Bool) -> Void in
                        assert(NSThread.currentThread() == NSThread.mainThread(), "thread error")
                        SwipeElement.objectCount += 1
                        self.fSeeking = false
                        if let pendingOffset = self.pendingOffset {
                            self.pendingOffset = nil
                            self.setTimeOffsetTo(pendingOffset, fAutoPlay: false, fElementRepeat: fElementRepeat)
                        }
                    }
                */
            }
        }
    }

    boolean isVideoElement() {
        if (videoPlayer != null) {
            return true;
        }
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                if (((SwipeElement) c).isVideoElement()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isRepeatElement() {
        if (fRepeat) {
            return true;
        }
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                if (((SwipeElement) c).isRepeatElement()) {
                    return true;
                }
            }
        }
        return false;
    }

    private SwipePath parsePath(Object shape, float w, float h, CGSize scale, DisplayMetrics dm) {
        if (shape == null) {
            return null;
        }
        Object shape0 = shape;
        if (shape0 instanceof JSONObject) {
            String name = ((JSONObject) shape0).optString("ref");
            if (!name.isEmpty()) {
                shape0 = delegate.pathWithName(name);
            }
        }

        if (shape0 instanceof String) {
            return SwipePath.parse((String) shape0, w, h, scale, dm);
        } else {
            return null;
        }
    }

    private String parseText(SwipeNode originator, JSONObject info, String key) {
        if (info == null ) {
            return null;
        }

        Object value = info.opt(key);
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        JSONObject params = info.optJSONObject(key);
        if (params == null) {
            return null;
        }

        JSONObject valInfo = params.optJSONObject("valueOf");
        if (valInfo != null){
            /* TODO
            if let text = originator.getValue(originator, info: valInfo) as? String {
                return text
            }
            */
            return null;
        } else {
            String ref = params.optString("ref", null);
            if (ref != null) {
                return delegate.localizedStringForKey(ref);
            }
        }

        return SwipeParser.localizedString(params, delegate.langId());
    }

    @Override
    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();

            final String[] mediaKeys = {"img", "mask", "video", "sprite" };
            for (String key : mediaKeys) {
                String src = info.optString(key, null);
                if (src != null) {
                    URL url = delegate.makeFullURL(src);
                    if (info.optBoolean("stream")) {
                        SwipeUtil.Log(TAG, "no need to cache streaming video " + url, 5);
                    } else {
                        resourceURLs.add(url);
                    }
                }
            }

            JSONArray elementsInfo = info.optJSONArray("elements");
            if (elementsInfo != null) {
                final CGSize scaleDummy = new CGSize(1, 1);
                for (int e = 0; e < elementsInfo.length(); e++) {
                    SwipeElement element = new SwipeElement(getContext(), dimension, scrDimension, scaleDummy, elementsInfo.optJSONObject(e), this, delegate);
                    resourceURLs.addAll(element.getResourceURLs());
                }
            }

            JSONObject listInfo = info.optJSONObject("list");
            if (listInfo != null) {
                JSONArray itemsInfo = listInfo.optJSONArray("items");
                if (itemsInfo != null) {
                    for (int i = 0; i < itemsInfo.length(); i++) {
                        JSONObject itemInfo = itemsInfo.optJSONObject(i);
                        if (itemInfo != null) {
                            elementsInfo = itemInfo.optJSONArray("elements");
                            if (elementsInfo != null) {
                                final CGSize scaleDummy = new CGSize(1, 1);
                                for (int e = 0; e < elementsInfo.length(); e++) {
                                    SwipeElement element = new SwipeElement(getContext(), dimension, scrDimension, scaleDummy, elementsInfo.optJSONObject(e), this, delegate);
                                    resourceURLs.addAll(element.getResourceURLs());
                                }
                            }
                        }
                    }
                }
            }
        }

        return resourceURLs;
    }
}