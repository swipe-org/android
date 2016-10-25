package org.swipe.core;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.swipe.browser.SwipeBrowserActivity;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.R.attr.defaultValue;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeElement extends SwipeView {
    interface Delegate {
        double durationSec();
        JSONObject prototypeWith(String name);
        /* TODO
        func pathWith(name:String?) -> AnyObject?
        func shouldRepeat(element:SwipeElement) -> Bool
        func onAction(element:SwipeElement)
        func didStartPlaying(element:SwipeElement)
        func didFinishPlaying(element:SwipeElement, completed:Bool)
        func parseMarkdown(element:SwipeElement, markdowns:[String]) -> NSAttributedString
        */
        URL baseURL();
        /*
        func map(url:NSURL) -> NSURL?
        func addedResourceURLs(urls:[NSURL:String], callback:() -> Void)
        func pageIndex() -> Int // for debugging
        func localizedStringForKey(key:String) -> String?
        func languageIdentifier() -> String?
         */
    }

    private static final String TAG = "SwElem";
    private SwipeElement.Delegate delegate = null;
    private List<ObjectAnimator> animations = new ArrayList<>();
    private boolean fRepeat = false;
    private SwipeShapeDrawable shapeLayer = null;

    private SwipeBackgroundDrawable bgDrawable = new SwipeBackgroundDrawable();

    // Video Element Specific
    private Object videoPlayer = null;
    private boolean fSeeking = false;
    private boolean fNeedRewind = false;
    private boolean fPlaying = false;
    private Float pendingOffset = null;
    private Float videoStart = 0.0f;
    private Float videoDuration = 1.0f;

    List<ObjectAnimator> getAllAnimations() {
        List<ObjectAnimator> allAni = new ArrayList<>();
        allAni.addAll(animations);
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                allAni.addAll(e.getAllAnimations());
            }
        }
        return allAni;
    }

    SwipeElement(Context _context, CGSize _dimension, CGSize _scale, JSONObject _info, SwipeNode parent, SwipeElement.Delegate _delegate) {
        super(_context, _dimension, _scale, _info);
        delegate = _delegate;
    }

    private class PathRotationEvaluator implements TypeEvaluator<Number> {
        private PathMeasure pm = null;
        private float[] tan = new float[2];
        private float[] pos = new float[2];

        private float len = 0;

        PathRotationEvaluator(Path path) {
            pm = new PathMeasure(path, false);
            len = pm.getLength();
        }

        public Number evaluate(float fraction, Number startValue, Number endValue) {
            pm.getPosTan(fraction * len, pos, tan);
            return(float) (Math.atan2(tan[1], tan[0]) * 180.0 / Math.PI);
        }
    }

    private class PathReverseRotationEvaluator implements TypeEvaluator<Number> {
        private PathMeasure pm = null;
        private float[] tan = new float[2];
        private float[] pos = new float[2];

        private float len = 0;

        PathReverseRotationEvaluator(Path path) {
            pm = new PathMeasure(path, false);
            len = pm.getLength();
        }

        public Number evaluate(float fraction, Number startValue, Number endValue) {
            pm.getPosTan(fraction * len, pos, tan);
            float degrees = (float) (Math.atan2(tan[1], tan[0]) * 180.0 / Math.PI);
            return ((int)degrees + 180) % 360;
        }
    }

    @Override
    void createViewGroup() {
        viewGroup = new ViewGroup(getContext()) {

            @Override
            protected void onDraw(Canvas canvas) {

                if (shapeLayer != null) {
                    shapeLayer.draw(canvas);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                //Log.d(TAG, "onLayout");
                setClipChildren(false);
                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    //Log.d(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
                    v.layout(0, 0, lp.width, lp.height);
                }
            }
        };
    }

    @Override
    ViewGroup loadView() {
        super.loadView();

        int bc = SwipeParser.parseColor(info, "bc", Color.TRANSPARENT);
        bgDrawable.setBackgroundColor(bc);
        viewGroup.setBackground(bgDrawable);

        float x = 0;
        float y = 0;
        float w0 = dimension.width;
        float h0 = dimension.height;
        boolean fNaturalW = true;
        boolean fNaturalH = true;
        /* TODO
        var imageRef:CGImage?
        var imageSrc:CGImageSourceRef?
        var maskSrc:CGImage?
        var pathSrc:CGPath?
        var innerLayer:CALayer? // for loop shift
        */
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

        /*
        if let src = info["img"] as? String {
            //imageSrc = SwipeParser.imageSourceWith(src)
            if let url = NSURL.url(src, baseURL: baseURL) {
                if let urlLocal = self.delegate.map(url) {
                    imageSrc = CGImageSourceCreateWithURL(urlLocal, nil)
                } else {
                    imageSrc = CGImageSourceCreateWithURL(url, nil)
                }
                if imageSrc != nil && CGImageSourceGetCount(imageSrc!) > 0 {
                    imageRef = CGImageSourceCreateImageAtIndex(imageSrc!, 0, nil)
                }
            }
        }

        if let src = info["mask"] as? String {
            //maskSrc = SwipeParser.imageWith(src)
            if let url = NSURL.url(src, baseURL: baseURL),
            urlLocal = self.delegate.map(url),
                    image = CGImageSourceCreateWithURL(urlLocal, nil) {
                if CGImageSourceGetCount(image) > 0 {
                    maskSrc = CGImageSourceCreateImageAtIndex(image, 0, nil)
                }
            }
        }
        */
        Path path = parsePath(info.opt("path"), w0, h0, scale, dm);

        // The natural size is determined by the contents (either image or mask)
        CGSize sizeContents = null;

        /*
        if imageRef != nil {
            sizeContents = CGSizeMake(CGFloat(CGImageGetWidth(imageRef!)),
            CGFloat(CGImageGetHeight(imageRef!)))
        } else if maskSrc != nil {
            sizeContents = CGSizeMake(CGFloat(CGImageGetWidth(maskSrc!)),
            CGFloat(CGImageGetHeight(maskSrc!)))
        } else  */ if (path != null) {
            RectF rc = new RectF();
            path.computeBounds(rc, false /* unused */);
            sizeContents = new CGSize(rc.left + rc.width(), rc.top + rc.height());
            sizeContents.width /= dm.density;
            sizeContents.height /= dm.density;
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
            String value = info.optString("y");
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
        final float dipX = px2Dip(x);
        final float dipY = px2Dip(y);
        final float dipW = px2Dip(w);
        final float dipH = px2Dip(h);
        viewGroup.setX(dipX);
        viewGroup.setY(dipY);
        viewGroup.setLayoutParams(new ViewGroup.LayoutParams((int)dipW,(int)dipH));
        /*
        #if os(OSX)
        let layer = view.makeBackingLayer()
        #else
        let layer = view.layer
        #endif
        self.layer = layer
        
        if let values = info["anchor"] as? [AnyObject] where values.count == 2 && w0 > 0 && h0 > 0,
                let posx = SwipeParser.parsePercentAny(values[0], full: w0, defaultValue: 0),
        let posy = SwipeParser.parsePercentAny(values[1], full: h0, defaultValue: 0) {
            layer.anchorPoint = CGPointMake(posx / w0, posy / h0)
        }

        if let values = info["pos"] as? [AnyObject] where values.count == 2,
                let posx = SwipeParser.parsePercentAny(values[0], full: dimension.width, defaultValue: 0),
        let posy = SwipeParser.parsePercentAny(values[1], full: dimension.height, defaultValue: 0) {
            layer.position = CGPointMake(posx * scale.width, posy * scale.height)
        }

        if let value = info["action"] as? String {
            action = value
            #if os(iOS) // tvOS has some focus issue with UIButton, figure out OSX later
            let btn = UIButton(type: UIButtonType.Custom)
            btn.frame = view.bounds
            btn.addTarget(self, action: #selector(SwipeElement.buttonPressed), forControlEvents: UIControlEvents.TouchUpInside)
            btn.addTarget(self, action: #selector(SwipeElement.touchDown), forControlEvents: UIControlEvents.TouchDown)
            btn.addTarget(self, action: #selector(SwipeElement.touchUpOutside), forControlEvents: UIControlEvents.TouchUpOutside)
            view.addSubview(btn)
            self.btn = btn
            #endif
            if action == "play" {
                notificationManager.addObserverForName(SwipePage.didStartPlaying, object: self.delegate, queue: NSOperationQueue.mainQueue()) {
                    /[unowned self]/ (_: NSNotification!) -> Void in
                    // NOTE: Animation does not work because we are performing animation using the parent layer
                    //UIView.animateWithDuration(0.2, animations: { () -> Void in
                    layer.opacity = 0.0
                    //})
                }
                notificationManager.addObserverForName(SwipePage.didFinishPlaying, object: self.delegate, queue: NSOperationQueue.mainQueue()) {
                    /[unowned self]/ (_: NSNotification!) -> Void in
                    // NOTE: Animation does not work because we are performing animation using the parent layer
                    //UIView.animateWithDuration(0.2, animations: { () -> Void in
                    layer.opacity = 1.0
                    //})
                }
            }
        } else if let eventsInfo = info["events"] as? [String:AnyObject] {
            eventHandler.parse(eventsInfo)
        }

        if let enabled = info["enabled"] as? Bool {
            self.fEnabled = enabled
        }

        if let focusable = info["focusable"] as? Bool {
            self.fFocusable = focusable
        }

        if let value = info["clip"] as? Bool {
            //view.clipsToBounds = value
            layer.masksToBounds = value
        }

        if let image = imageRef {
            let rc = view.bounds
            let imageLayer = CALayer()
            imageLayer.contentsScale = contentScale
            imageLayer.frame = rc
            imageLayer.contents = image
            imageLayer.contentsGravity = kCAGravityResizeAspectFill
            imageLayer.masksToBounds = true
            layer.addSublayer(imageLayer)
            if let tiling = info["tiling"] as? Bool where tiling {
                let hostLayer = CALayer()
                innerLayer = hostLayer
                //rc.origin = CGPointZero
                //imageLayer.frame = rc
                hostLayer.addSublayer(imageLayer)
                layer.addSublayer(hostLayer)
                layer.masksToBounds = true

                var rcs = [rc, rc, rc, rc]
                rcs[0].origin.x -= rc.size.width
                rcs[1].origin.x += rc.size.width
                rcs[2].origin.y -= rc.size.height
                rcs[3].origin.y += rc.size.height
                for rc in rcs {
                    let subLayer = CALayer()
                    subLayer.contentsScale = contentScale
                    subLayer.frame = rc
                    subLayer.contents = image
                    subLayer.contentsGravity = kCAGravityResizeAspectFill
                    subLayer.masksToBounds = true
                    hostLayer.addSublayer(subLayer)
                }
            }

            // Handling GIF animation
            if let isrc = imageSrc {
                self.step = 0
                var images = [CGImageRef]()
                // NOTE: Using non-main thread has some side-effect
                //let queue = dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_LOW, 0)
                //dispatch_async(queue) { () -> Void in
                let count = CGImageSourceGetCount(isrc)
                for i in 1..<count {
                    if let image = CGImageSourceCreateImageAtIndex(isrc, i, nil) {
                        images.append(image)
                    }
                }
                let ani = CAKeyframeAnimation(keyPath: "contents")
                ani.values = images
                ani.beginTime = 1e-10
                ani.duration = 1.0
                ani.fillMode = kCAFillModeBoth
                imageLayer.addAnimation(ani, forKey: "contents")
                //}
            }
            self.imageLayer = imageLayer
        }

        if let src = info["sprite"] as? String,
                let slice = info["slice"] as? [Int] {
            //view.clipsToBounds = true
            layer.masksToBounds = true
            if let values = self.info["slot"] as? [Int] where values.count == 2 {
                slot = CGPointMake(CGFloat(values[0]), CGFloat(values[1]))
            }
            if let url = NSURL.url(src, baseURL: baseURL),
            urlLocal = self.delegate.map(url),
                    imageSource = CGImageSourceCreateWithURL(urlLocal, nil) where CGImageSourceGetCount(imageSource) > 0,
                    let image = CGImageSourceCreateImageAtIndex(imageSource, 0, nil) {
                let imageLayer = CALayer()
                imageLayer.contentsScale = contentScale
                imageLayer.frame = view.bounds
                imageLayer.contents = image
                if slice.count > 0 {
                    self.slice.width = CGFloat(slice[0])
                    if slice.count > 1 {
                        self.slice.height = CGFloat(slice[1])
                    }
                }
                contentsRect = CGRectMake(slot.x/self.slice.width, slot.y/self.slice.height, 1.0/self.slice.width, 1.0/self.slice.height)
                imageLayer.contentsRect = contentsRect
                layer.addSublayer(imageLayer)
                spriteLayer = imageLayer
            }
        }
        layer.backgroundColor = SwipeParser.parseColor(info["bc"])

        if let value = self.info["videoDuration"] as? CGFloat {
            videoDuration = value
        }
        if let value = self.info["videoStart"] as? CGFloat {
            videoStart = value
        }
        if let image = maskSrc {
            let imageLayer = CALayer()
            imageLayer.contentsScale = contentScale
            imageLayer.frame = CGRectMake(0,0,w,h)
            imageLayer.contents = image
            layer.mask = imageLayer
        }
        */

        bgDrawable.setCornerRadius(px2Dip((float) info.optDouble("cornerRadius", 0) * scale.width));
        bgDrawable.setBorderWidth(px2Dip((float) info.optDouble("borderWidth", 0) * scale.width));
        bgDrawable.setBorderColor(SwipeParser.parseColor(info.opt("borderColor"), Color.BLACK));

        if (path != null) {
            //shapeLayer.contentsScale = contentScale
            Path xpath = SwipeParser.transformedPath(path, info, "scale", dipW, dipH);
            if (xpath != null){
                shapeLayer = new SwipeShapeDrawable(xpath, dipW, dipH);
            } else {
                shapeLayer = new SwipeShapeDrawable(path, dipW, dipH);
            }
            shapeLayer.setFillColor(SwipeParser.parseColor(info, "fillColor", Color.TRANSPARENT));
            shapeLayer.setStrokeColor(SwipeParser.parseColor(info, "strokeColor", Color.BLACK));
            shapeLayer.setLineWidth(px2Dip(SwipeParser.parseFloat(info, "lineWidth", 1) * scale.width));

            // TODO SwipeElement.processShadow(info, scale:scale, layer: shapeLayer);

            shapeLayer.setLineCap(Paint.Cap.ROUND);
            shapeLayer.setStrokeStart(SwipeParser.parseFloat(info, "strokeStart", 0));
            shapeLayer.setStrokeEnd(SwipeParser.parseFloat(info, "strokeEnd", 1));

            //layer.addSublayer(shapeLayer)
            /*
            if let tiling = info["tiling"] as? Bool where tiling {
                let hostLayer = CALayer()
                innerLayer = hostLayer
                let rc = view.bounds
                hostLayer.addSublayer(shapeLayer)
                layer.addSublayer(hostLayer)
                layer.masksToBounds = true

                var rcs = [rc, rc, rc, rc]
                rcs[0].origin.x -= rc.size.width
                rcs[1].origin.x += rc.size.width
                rcs[2].origin.y -= rc.size.height
                rcs[3].origin.y += rc.size.height
                for rc in rcs {
                    let subLayer = CAShapeLayer()
                    subLayer.frame = rc
                    subLayer.contentsScale = shapeLayer.contentsScale
                    subLayer.path = shapeLayer.path
                    subLayer.fillColor = shapeLayer.fillColor
                    subLayer.strokeColor = shapeLayer.strokeColor
                    subLayer.lineWidth = shapeLayer.lineWidth
                    subLayer.shadowColor = shapeLayer.shadowColor
                    subLayer.shadowOffset = shapeLayer.shadowOffset
                    subLayer.shadowOpacity = shapeLayer.shadowOpacity
                    subLayer.shadowRadius = shapeLayer.shadowRadius
                    subLayer.lineCap = shapeLayer.lineCap
                    subLayer.strokeStart = shapeLayer.strokeStart
                    subLayer.strokeEnd = shapeLayer.strokeEnd
                    hostLayer.addSublayer(subLayer)
                }
            */
            }
        /*
        } else {
            SwipeElement.processShadow(info, scale:scale, layer: layer)
        }

        var mds = info["markdown"]
        if let md = mds as? String {
            mds = [md]
        }
        if let markdowns = mds as? [String] {
            #if !os(OSX) // REVIEW
            let attrString = self.delegate.parseMarkdown(self, markdowns: markdowns)
            let rcLabel = view.bounds
            let label = UILabel(frame: rcLabel)
            label.attributedText = attrString
            label.numberOfLines = 999
            view.addSubview(label)
            #endif
        }

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

        if let text = parseText(self, info: info, key:"text") {
            if self.helper == nil || !self.helper!.setText(text, scale:self.scale, info: info, dimension:screenDimension, layer: layer) {
                self.textLayer = SwipeElement.addTextLayer(text, scale:scale, info: info, dimension: screenDimension, layer: layer)
            }
        }

        // http://stackoverflow.com/questions/9290972/is-it-possible-to-make-avurlasset-work-without-a-file-extension
        var fStream:Bool = {
        if let fStream = info["stream"] as? Bool {
            return fStream
        }
        return false
        }()
        let urlVideoOrRadio:NSURL? = {
        if let src = info["video"] as? String,
                let url = NSURL.url(src, baseURL: baseURL) {
            return url
        }
        if let src = info["radio"] as? String,
                let url = NSURL.url(src, baseURL: baseURL) {
            fStream = true
            return url
        }
        return nil
        }()
        if let url = urlVideoOrRadio {
            let videoPlayer = AVPlayer()
            self.videoPlayer = videoPlayer
            let videoLayer = XAVPlayerLayer(player: videoPlayer)
            videoLayer.frame = CGRectMake(0.0, 0.0, w, h)
            if fScaleToFill {
                videoLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
            }
            layer.addSublayer(videoLayer)

            let urlLocalOrStream:NSURL?
            if fStream {
                MyLog("SWElem stream=\(url)", level:2)
                urlLocalOrStream = url
            } else if let urlLocal = self.delegate.map(url) {
                urlLocalOrStream = urlLocal
            } else {
                urlLocalOrStream = nil
            }

            if let urlVideo = urlLocalOrStream {
                let playerItem = AVPlayerItem(URL: urlVideo)
                videoPlayer.replaceCurrentItemWithPlayerItem(playerItem)

                notificationManager.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, object: playerItem, queue: NSOperationQueue.mainQueue()) {
                    [unowned self] (_:NSNotification!) -> Void in
                    MyLog("SWElem play to end!", level: 1)
                    if self.delegate != nil && self.delegate!.shouldRepeat(self) {
                        videoPlayer.seekToTime(kCMTimeZero)
                        videoPlayer.play()
                    } else {
                        self.fNeedRewind = true
                        if self.fPlaying {
                            self.fPlaying = false
                            self.delegate.didFinishPlaying(self, completed:true)
                        }
                    }
                }
            }

            notificationManager.addObserverForName(SwipePage.shouldPauseAutoPlay, object: delegate, queue: NSOperationQueue.mainQueue()) {
                [unowned self] (_:NSNotification!) -> Void in
                if self.fPlaying {
                    self.fPlaying = false
                    self.delegate.didFinishPlaying(self, completed:false)
                    videoPlayer.pause()
                }
            }
            notificationManager.addObserverForName(SwipePage.shouldStartAutoPlay, object: delegate, queue: NSOperationQueue.mainQueue()) {
                [unowned self] (_:NSNotification!) -> Void in
                if !self.fPlaying && layer.opacity > 0 {
                    self.fPlaying = true
                    self.delegate.didStartPlaying(self)
                    MyLog("SWElem videoPlayer.state = \(videoPlayer.status.rawValue)", level: 1)
                    if self.fNeedRewind {
                        videoPlayer.seekToTime(kCMTimeZero)
                    }
                    videoPlayer.play()
                    self.fNeedRewind = false
                }
            }
        }

        if let transform = SwipeParser.parseTransform(info, scaleX:scale.width, scaleY:scale.height, base: nil, fSkipTranslate: false, fSkipScale: self.shapeLayer != nil) {
            layer.transform = transform
        }

        layer.opacity = SwipeParser.parseFloat(info["opacity"])

        if let visible = info["visible"] as? Bool where !visible {
            layer.opacity = 0.0
        }
        */

        JSONObject to = info.optJSONObject("to");
        if (to != null) {
            double start = 1e-10;
            double duration = 1.0;

            JSONArray timingJA = to.optJSONArray("timing");
            if (timingJA != null && (timingJA.length() == 2)) {
                double timing[] = { timingJA.optDouble(0), timingJA.optDouble(1) };
                if (!Double.isNaN(timing[0]) && !Double.isNaN(timing[1]) && timing[0] >= 0 && timing[0] <= timing[1] && timing[1] <= 1) {
                    start = timing[0] == 0 ? 1e-10 : timing[0];
                    duration = timing[1] - start;
                }
            }

            final int kStartDelay = (int)(start * delegate.durationSec() * 1000);
            final int kDuration = (int)(duration * delegate.durationSec() * 1000);

            boolean fSkipTranslate = false;
            Path posPath = parsePath(to.opt("pos"), w0, h0, scale, dm);
            if (posPath != null){
                ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "x", "y", posPath);
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);

                String mode = to.optString("mode");
                switch(mode) {
                    case "auto": {
                        ObjectAnimator aniR = ObjectAnimator.ofObject(viewGroup, "rotation", new PathRotationEvaluator(posPath), 0);
                        aniR.setStartDelay(kStartDelay);
                        aniR.setDuration(kDuration);
                        animations.add(aniR);
                        break;
                    }
                    case "reverse": {
                        ObjectAnimator aniR = ObjectAnimator.ofObject(viewGroup, "rotation", new PathReverseRotationEvaluator(posPath), 0);
                        aniR.setStartDelay(kStartDelay);
                        aniR.setDuration(kDuration);
                        animations.add(aniR);
                        break;
                    }
                    default: // or "none"
                        break;
                }

                fSkipTranslate = true;
            }

            Double dopt = to.optDouble("rotate");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "rotation",  dopt.floatValue());
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
            }

            dopt = to.optDouble("scale");
            if (!dopt.isNaN()){
                ObjectAnimator aniX = ObjectAnimator.ofFloat(viewGroup, "scaleX",  dopt.floatValue());
                aniX.setStartDelay(kStartDelay);
                aniX.setDuration(kDuration);
                animations.add(aniX);
                ObjectAnimator aniY = ObjectAnimator.ofFloat(viewGroup, "scaleY",  dopt.floatValue());
                aniY.setStartDelay(kStartDelay);
                aniY.setDuration(kDuration);
                animations.add(aniY);
            }

            JSONArray translate = to.optJSONArray("translate");
            if (translate != null && translate.length() == 2 && !fSkipTranslate) {
                Double translate0 = translate.optDouble(0);
                Double translate1 = translate.optDouble(1);
                if (!translate0.isNaN()) {
                    ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "translationX", px2Dip(translate0.floatValue() * scale.width) + dipX);
                    ani.setStartDelay((int) (start * delegate.durationSec() * 1000));
                    ani.setDuration((int) (duration * delegate.durationSec() * 1000));
                    animations.add(ani);
                }
                if (!translate1.isNaN()) {
                    ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "translationY", px2Dip(translate1.floatValue() * scale.height) + dipY);
                    ani.setStartDelay((int) (start * delegate.durationSec() * 1000));
                    ani.setDuration((int) (duration * delegate.durationSec() * 1000));
                    animations.add(ani);
                }
            }

            dopt = to.optDouble("opacity");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(viewGroup, "alpha",  dopt.floatValue());
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
            }

            if (to.has("bc")) {
                ObjectAnimator ani = ObjectAnimator.ofObject(bgDrawable, "backgroundColor", new ArgbEvaluator(), bgDrawable.getBackgroundColor(), SwipeParser.parseColor(to, "bc", Color.TRANSPARENT));
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
            }

            Object opt = to.opt("borderColor");
            if (opt != null){
                ObjectAnimator ani = ObjectAnimator.ofObject(bgDrawable, "borderColor", new ArgbEvaluator(), bgDrawable.getBorderColor(), SwipeParser.parseColor(opt));
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
            }

            dopt = to.optDouble("borderWidth");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(bgDrawable, "borderWidth",  px2Dip(dopt.floatValue() * scale.width));
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
            }
            dopt = to.optDouble("cornerRadius");
            if (!dopt.isNaN()){
                ObjectAnimator ani = ObjectAnimator.ofFloat(bgDrawable, "cornerRadius",  px2Dip(dopt.floatValue() * scale.width));
                ani.setStartDelay(kStartDelay);
                ani.setDuration(kDuration);
                animations.add(ani);
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
                /*
                if let params = to["path"] as? [AnyObject] {
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
                } else if let path = parsePath(to["path"], w: w0, h: h0, scale:scale) {
                    let ani = CABasicAnimation(keyPath: "path")
                    ani.fromValue = shapeLayer.path
                    ani.toValue = path
                    ani.beginTime = start
                    ani.duration = duration
                    ani.fillMode = kCAFillModeBoth
                    shapeLayer.addAnimation(ani, forKey: "path")
                } else if let path = SwipeParser.transformedPath(pathSrc!, param: to, size:frame.size) {
                    let ani = CABasicAnimation(keyPath: "path")
                    ani.fromValue = shapeLayer.path
                    ani.toValue = path
                    ani.beginTime = start
                    ani.duration = duration
                    ani.fillMode = kCAFillModeBoth
                    shapeLayer.addAnimation(ani, forKey: "path")
                }
                */

                opt = to.opt("fillColor");
                if (opt != null){
                    ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "fillColor", new ArgbEvaluator(), SwipeParser.parseColor(opt));
                    ani.setStartDelay(kStartDelay);
                    ani.setDuration(kDuration);
                    animations.add(ani);
                }
                opt = to.opt("strokeColor");
                if (opt != null){
                    ObjectAnimator ani = ObjectAnimator.ofObject(shapeLayer, "strokeColor", new ArgbEvaluator(), SwipeParser.parseColor(opt));
                    ani.setStartDelay(kStartDelay);
                    ani.setDuration(kDuration);
                    animations.add(ani);
                }

                dopt = to.optDouble("lineWidth");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "lineWidth",  px2Dip(dopt.floatValue() * scale.width));
                    ani.setStartDelay(kStartDelay);
                    ani.setDuration(kDuration);
                    animations.add(ani);
                }
                dopt = to.optDouble("strokeStart");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "strokeStart", dopt.floatValue());
                    ani.setStartDelay(kStartDelay);
                    ani.setDuration(kDuration);
                    animations.add(ani);
                }
                dopt = to.optDouble("fillColor");
                if (!dopt.isNaN()){
                    ObjectAnimator ani = ObjectAnimator.ofFloat(shapeLayer, "strokeEnd", dopt.floatValue());
                    ani.setStartDelay(kStartDelay);
                    ani.setDuration(kDuration);
                    animations.add(ani);
                }
            }
        }
        /*
        if let fRepeat = info["repeat"] as? Bool where fRepeat {
            //NSLog("SE detected an element with repeat")
            self.fRepeat = fRepeat
            layer.speed = 0 // Independently animate it
        }

        if let animation = info["loop"] as? [String:AnyObject],
        let style = animation["style"] as? String {
            //
            // Note: Use the inner layer (either image or shape) for the loop animation
            // to avoid any conflict with other transformation if it is available.
            // In this case, the loop animation does not effect chold elements (because
            // we use UIView hierarchy instead of CALayer hierarchy.
            //
            // It means the loop animation on non-image/non-shape element does not work well
            // with other transformation.
            //
            var loopLayer = layer
            if let l = imageLayer {
                loopLayer = l
            } else if let l = shapeLayer {
                loopLayer = l
            }

            let start, duration:Double
            if let timing = animation["timing"] as? [Double]
            where timing.count == 2 && timing[0] >= 0 && timing[0] <= timing[1] && timing[1] <= 1 {
                start = timing[0] == 0 ? 1e-10 : timing[0]
                duration = timing[1] - start
            } else {
                start = 1e-10
                duration = 1.0
            }
            let repeatCount = Float(valueFrom(animation, key: "count", defaultValue: 1))

            switch(style) {
                case "vibrate":
                    let delta = valueFrom(animation, key: "delta", defaultValue: 10.0)
                    let ani = CAKeyframeAnimation(keyPath: "transform")
                    ani.values = [NSValue(CATransform3D:loopLayer.transform),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeTranslation(delta, 0.0, 0.0), loopLayer.transform)),
                    NSValue(CATransform3D:loopLayer.transform),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeTranslation(-delta, 0.0, 0.0), loopLayer.transform)),
                    NSValue(CATransform3D:loopLayer.transform)]
                    ani.repeatCount = repeatCount
                    ani.beginTime = start
                    ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                    ani.fillMode = kCAFillModeBoth
                    loopLayer.addAnimation(ani, forKey: "transform")
                case "shift":
                    let shiftLayer = (innerLayer == nil) ? layer : innerLayer!
                        let ani = CAKeyframeAnimation(keyPath: "transform")
                    let shift:CGSize = {
                    if let dir = animation["direction"] as? String {
                    switch(dir) {
                        case "n":
                            return CGSizeMake(0, -h)
                        case "e":
                            return CGSizeMake(w, 0)
                        case "w":
                            return CGSizeMake(-w, 0)
                        default:
                            return CGSizeMake(0, h)
                    }
                } else {
                    return CGSizeMake(0, h)
                }
                }()
                ani.values = [NSValue(CATransform3D:shiftLayer.transform),
                NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeTranslation(shift.width, shift.height, 0.0), shiftLayer.transform))]
                ani.repeatCount = repeatCount
                ani.beginTime = start
                ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                ani.fillMode = kCAFillModeBoth
                shiftLayer.addAnimation(ani, forKey: "transform")
                case "blink":
                    let ani = CAKeyframeAnimation(keyPath: "opacity")
                    ani.values = [1.0, 0.0, 1.0]
                    ani.repeatCount = repeatCount
                    ani.beginTime = start
                    ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                    ani.fillMode = kCAFillModeBoth
                    loopLayer.addAnimation(ani, forKey: "opacity")
                case "spin":
                    let fClockwise = booleanValueFrom(animation, key: "clockwise", defaultValue: true)
                    let degree = (fClockwise ? 120 : -120) * CGFloat(M_PI / 180.0)
                    let ani = CAKeyframeAnimation(keyPath: "transform")
                    ani.values = [NSValue(CATransform3D:loopLayer.transform),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeRotation(degree, 0.0, 0.0, 1.0), loopLayer.transform)),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeRotation(degree * 2, 0.0, 0.0, 1.0), loopLayer.transform)),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeRotation(degree * 3, 0.0, 0.0, 1.0), loopLayer.transform))]
                    ani.repeatCount = repeatCount
                    ani.beginTime = start
                    ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                    ani.fillMode = kCAFillModeBoth
                    loopLayer.addAnimation(ani, forKey: "transform")
                case "wiggle":
                    let delta = valueFrom(animation, key: "delta", defaultValue: 15) * CGFloat(M_PI / 180.0)
                    let ani = CAKeyframeAnimation(keyPath: "transform")
                    ani.values = [NSValue(CATransform3D:loopLayer.transform),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeRotation(delta, 0.0, 0.0, 1.0), loopLayer.transform)),
                    NSValue(CATransform3D:loopLayer.transform),
                    NSValue(CATransform3D:CATransform3DConcat(CATransform3DMakeRotation(-delta, 0.0, 0.0, 1.0), loopLayer.transform)),
                    NSValue(CATransform3D:loopLayer.transform)]
                    ani.repeatCount = repeatCount
                    ani.beginTime = start
                    ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                    ani.fillMode = kCAFillModeBoth
                    loopLayer.addAnimation(ani, forKey: "transform")
                case "path":
                    if let shapeLayer = self.shapeLayer {
                    var values = [shapeLayer.path!]
                    if let params = animation["path"] as? [AnyObject] {
                        for param in params {
                            if let path = parsePath(param, w: w0, h: h0, scale:scale) {
                                values.append(path)
                            }
                        }
                    } else if let path = parsePath(animation["path"], w: w0, h: h0, scale:scale) {
                        values.append(path)
                    }
                    if values.count >= 2 {
                        values.append(shapeLayer.path!)
                        let ani = CAKeyframeAnimation(keyPath: "path")
                        ani.values = values
                        ani.repeatCount = repeatCount
                        ani.beginTime = start
                        ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                        ani.fillMode = kCAFillModeBoth
                        shapeLayer.addAnimation(ani, forKey: "path")
                    }
                }
                case "sprite":
                    if let targetLayer = spriteLayer {
                    let ani = CAKeyframeAnimation(keyPath: "contentsRect")
                    let rc0 = CGRectMake(0, slot.y/self.slice.height, 1.0/self.slice.width, 1.0/self.slice.height)
                    ani.values = Array(0..<Int(slice.width)).map() { (index:Int) -> NSValue in
                        NSValue(CGRect: CGRect(origin: CGPointMake(CGFloat(index) / self.slice.width, rc0.origin.y), size: rc0.size))
                    }
                    ani.repeatCount = repeatCount
                    ani.beginTime = start
                    ani.duration = CFTimeInterval(duration / Double(ani.repeatCount))
                    ani.fillMode = kCAFillModeBoth
                    ani.calculationMode = kCAAnimationDiscrete
                    targetLayer.addAnimation(ani, forKey: "contentsRect")
                }
                //self.dir = (1,0)
                //self.repeatCount = CGFloat(repeatCount)
                default:
                    break
            }
        }
        */

        // Nested Elements
        JSONArray elementsInfo = info.optJSONArray("elements");
        if (elementsInfo != null) {
            for (int e = 0; e < elementsInfo.length(); e++) {
                SwipeElement element = new SwipeElement(getContext(), new CGSize(w0, h0), scale, elementsInfo.optJSONObject(e), this, delegate);
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

    void setTimeOffsetTo(final float offset) {
        setTimeOffsetTo(offset, false, false);
    }

    void setTimeOffsetTo(final float offset, final boolean fAutoPlay) {
        setTimeOffsetTo(offset, fAutoPlay, false);
    }

    void setTimeOffsetTo(final float offset, final boolean fAutoPlay, final boolean fElementRepeat) {
        if (offset < 0.0 || offset > 1.0) {
            return;
        }

        // TODO if let layer = self.layer where layer.speed == 0 {
            // independently animated
            for (ObjectAnimator ani : animations) {
                ani.setCurrentFraction(offset);
            }
        //}

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement element = (SwipeElement) c;
                element.setTimeOffsetTo(offset, fAutoPlay, fElementRepeat);
            }
        }

        viewGroup.invalidate();

        if (fElementRepeat && !fRepeat) {
            return;
        }

        if (videoPlayer != null) {
            if (fAutoPlay) {
                return;
            }
            if (fSeeking) {
                pendingOffset = offset;
                return;
            }
            double timeSec = videoStart + offset * videoDuration;
            /* TODO
            double time = CMTimeMakeWithSeconds(Float64(timeSec), 600)
            let tolerance = CMTimeMake(10, 600) // 1/60sec
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
            }
            */
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

    private Path parsePath(Object shape, float w, float h, CGSize scale, DisplayMetrics dm) {
        if (shape == null) {
            return null;
        }
        Object shape0 = shape;
        if (shape0 instanceof JSONObject) {
            String key = ((JSONObject) shape0).optString("ref");
            if (!key.isEmpty()) {
                // TODO shape0 = delegate.pathWith(key)
            }
        }

        if (shape0 instanceof String) {
            return SwipePath.parse((String) shape0, w, h, scale, dm);
        } else {
            return null;
        }
    }

    @Override
    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();
            URL baseURL = delegate.baseURL();

            final String[] mediaKeys = {"img", "mask", "video", "sprite" };
            for (String key : mediaKeys) {
                String src = info.optString(key, null);
                if (src != null) {
                    URL url = SwipeBrowserActivity.makeFullURL(src, baseURL);
                    if (info.optBoolean("stream")) {
                        Log.e(TAG, "no need to cache streaming video " + url);
                    } else {
                        resourceURLs.add(url);
                    }
                }
            }

            JSONArray elementsInfo = info.optJSONArray("elements");
            if (elementsInfo != null) {
                final CGSize scaleDummy = new CGSize(1, 1);
                for (int e = 0; e < elementsInfo.length(); e++) {
                    SwipeElement element = new SwipeElement(getContext(), dimension, scaleDummy, elementsInfo.optJSONObject(e), this, delegate);
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
                                    SwipeElement element = new SwipeElement(getContext(), dimension, scaleDummy, elementsInfo.optJSONObject(e), this, delegate);
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