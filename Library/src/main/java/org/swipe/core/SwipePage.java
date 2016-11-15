package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.swipe.browser.SwipeBrowserActivity;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 *
 *
 */

class SwipePage extends SwipeView implements SwipeElement.Delegate {

    interface Delegate {
        /* TODO
        func dimension(page:SwipePage) -> CGSize
        func scale(page:SwipePage) -> CGSize
        */
        JSONObject prototypeWith(String name);
        SwipePageTemplate pageTemplateWith(String name);
        /*
        func pathWith(name:String?) -> AnyObject?
        func speak(utterance:AVSpeechUtterance)
        func stopSpeaking()
        */
        int currentPageIndex();
        List<SwipeMarkdown.Element> parseMarkdown(Object markdowns);
        URL baseURL();
        URL map(URL url);
        URL makeFullURL(String url);
        /*
        func voice(k:String?) -> [String:AnyObject]
        func languageIdentifier() -> String?
        func tapped()
        */
    }

    static final String didStartPlaying = "SwipePageDidStartPlaying";
    static final String didFinishPlaying = "SwipePageDidFinishPlaying";
    static final String shouldStartAutoPlay = "SwipePageShouldStartAutoPlay";
    static final String shouldPauseAutoPlay = "SwipePageShouldPauseAutoPlay";

    private static final String TAG = "SwPage";
    private Delegate delegate = null;
    private SwipePageTemplate pageTemplate = null;
    private float duration = 0.2f;
    private String animation = "auto";
    private String transition = null;
    private int index = -1;
    private int fps = 60;
    private int cPlaying = 0;
    private boolean vibrate = false;
    private boolean fRepeat = false;
    private boolean rewind = false;
    private boolean autoplay = false;
    private boolean always = false;
    private boolean scroll = false;
    boolean fixed = false;
    boolean replace = false;
    private boolean fSeeking = false;
    private boolean fEntered = false;
    private boolean fPausing = false;
    private Float offsetPaused = null;

    SwipePage(Context _context, CGSize _dimension, CGSize _scrDimension, CGSize _scale, int _index, JSONObject _info, SwipePage.Delegate _delegate) {
        super(_context, _dimension, _scrDimension, _scale, _info, /* parent */ null);
        index = _index;
        delegate = _delegate;

        pageTemplate = delegate.pageTemplateWith(info.optString("template"));
        if (pageTemplate == null) {
            pageTemplate = delegate.pageTemplateWith(info.optString("scene"));
            if (pageTemplate != null) {
                Log.w(TAG, "DEPRECATED 'scene'; use 'template'");
            }
        }

        super.info = SwipeParser.inheritProperties(_info, pageTemplate != null ? pageTemplate.info : null);
    }

    int getIndex() { return index; }
    SwipePageTemplate getPageTemplate() { return pageTemplate; }

    @Override
    void createViewGroup() {
        viewGroup = new ViewGroup(getContext()) {
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
        int bc = SwipeParser.parseColor(info, "bc", Color.WHITE);
        viewGroup.setBackgroundColor(bc);

        duration = (float)info.optDouble("duration", 0.2f);
        fps = info.optInt("fps", 60);
        vibrate = info.optBoolean("vibrate", false);
        fRepeat = info.optBoolean("repeat", false);
        rewind = info.optBoolean("rewind", false);

        String oldAnimation = info.optString("animation", null);
        if (oldAnimation != null) {
            Log.w(TAG, "DEPRECATED 'animation'; use 'play'");
            animation = oldAnimation;
        } else {
            animation = info.optString("play", animation);
        }

        transition = info.optString("transition", null);
        if (transition == null) {
            transition = (animation.equals("scroll")) ? "replace" : "scroll"; // default
        }

        autoplay = animation.equals("auto") || animation.equals("always");
        always = animation.equals("always");
        scroll = animation.equals("scroll");
        fixed = !transition.equals("scroll");
        replace = transition.equals("replace");

        JSONArray elementsInfo = info.optJSONArray("elements");
        if (elementsInfo != null) {
            for (int i = 0; i < elementsInfo.length(); i++) {
                SwipeElement element = new SwipeElement(getContext(), dimension, scrDimension, scale, elementsInfo.optJSONObject(i), this, this);
                children.add(element);
                viewGroup.addView(element.loadView());
            }
        }

        return viewGroup;
    }

    void setTimeOffsetWhileDragging(float offset) {
        if (scroll) {
            fEntered = false; // stops the element animation
            for (SwipeNode c : children) {
                if (c instanceof SwipeElement) {
                    ((SwipeElement) c).setTimeOffsetTo(offset);
                }
            }
        }
    }
    void willLeave(boolean fAdvancing) {
        MyLog(TAG, "willLeave " + (index) + " " + fAdvancing, 2);
        /* TODO
        if let _ = self.utterance {
            delegate.stopSpeaking()
            prepareUtterance() // recreate a new utterance to avoid reusing itt
        }
        */
    }

    void pause(boolean fForceRewind) {
        MyLog(TAG, "pause " + (index) + " " + fForceRewind, 2);

        fPausing = true;
        /* TODO
        if let player = self.audioPlayer {
            player.stop()
        }
        */

        NotificationCenter.defaultCenter().postNotification(SwipePage.shouldPauseAutoPlay);

        // auto rewind
        if (rewind || fForceRewind) {
            prepareToPlay(true);
        }
    }

    void didLeave(boolean fGoingBack) {
        MyLog(TAG, "didLeave " + (index) + " " + fGoingBack, 2);
        fEntered = false;
        pause(fGoingBack);
    }

    void willEnter(boolean fForward) {
        MyLog(TAG, "willEnter " + index + " " + fForward, 2);
        if (autoplay && fForward || always) {
            prepareToPlay(true);
        }
        if (fForward && scroll) {
            playAudio();
        }
    }

    private void playAudio() {
        /* TODO
        if let player = audioPlayer {
            player.currentTime = 0.0
            player.play()
        }
        if let utterance = self.utterance {
            delegate.speak(utterance)
        }
        if self.vibrate {
            AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_Vibrate))
        }
        */
    }

    void didEnter(boolean fForward) {
        MyLog(TAG, "didEnter " + index + " " + fForward, 2);
        fEntered = true;
        if ((fForward && autoplay) || always || fRepeat) {
            autoPlay(false);
        } else if (hasRepeatElement()) {
            autoPlay(true);
        }
    }

    void prepare() {
        MyLog(TAG, "prepare " + (index), 2);

        if (scroll) {
            prepareToPlay(index > delegate.currentPageIndex());
        } else {
            if (index < delegate.currentPageIndex()) {
                prepareToPlay(rewind);
            }
        }
    }

    private void prepareToPlay(boolean fForward) {
        MyLog(TAG, "prepareToPlay " + (index) + " " + fForward, 2);

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                e.resetAnimation(fForward);
            }
        }

        offsetPaused = null;
    }

    void play() {
        // REVIEW: Remove this block once we detect the end of speech
        /* TOD)
        if (utterance != null) {
            delegate.stopSpeaking()
            prepareUtterance() // recreate a new utterance to avoid reusing it
        }
        */
        autoPlay(false);
    }

    private void autoPlay(boolean fElementRepeat) {
        MyLog(TAG, "autoplay " + fElementRepeat, 2);
        fPausing = false;
        if (!fElementRepeat) {
            playAudio();
            NotificationCenter.defaultCenter().postNotification(SwipePage.shouldStartAutoPlay);
        }
        if (offsetPaused != null) {
            timerTick(offsetPaused.floatValue(), fElementRepeat);
        } else {
            timerTick(0.0f, fElementRepeat);
        }
        didStartPlayingInternal();
    }

    private void timerTick(final float offset, final boolean fElementRepeat) {
        // During the shutdown sequence, the loop will stop when didLeave was called.
        viewGroup.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean fElementRepeatNext = fElementRepeat;
                Float offsetForNextTick = null;
                if (fEntered && !fPausing) {
                    float nextOffset = offset + 1.0f / duration / fps;
                    if (nextOffset < 1.0f) {
                        offsetForNextTick = nextOffset;
                    } else {
                        nextOffset = 1.0f;
                        if (fRepeat) {
                            playAudio();
                            offsetForNextTick = 0.0f;
                        } else if (hasRepeatElement()) {
                            offsetForNextTick = 0.0f;
                            fElementRepeatNext = true;
                        }
                    }

                    for (SwipeNode c : children) {
                        if (c instanceof SwipeElement) {
                            ((SwipeElement) c).setTimeOffsetTo(nextOffset, fElementRepeat, /* autoPlay */ true);
                        }
                    }
                }
                if (offsetForNextTick != null) {
                    timerTick(offsetForNextTick, fElementRepeatNext);
                } else {
                    offsetPaused = fPausing ? offset : null;
                    didFinishPlayingInternal();
                }

            }
        }, 1000 / fps);
    }

    private void didStartPlayingInternal() {
        cPlaying += 1;
        if (cPlaying == 1) {
            MyLog(TAG, "didStartPlaying " + index, 5);
            NotificationCenter.defaultCenter().postNotification(SwipePage.didStartPlaying);
        }
    }

    private void didFinishPlayingInternal() {
        cPlaying -= 1;
        if (cPlaying < 0) throw new AssertionError( "didFinishPlaying going negative! " + index);
        if (cPlaying == 0) {
            NotificationCenter.defaultCenter().postNotification(SwipePage.didFinishPlaying);
        }
    }

    private boolean isPlaying() { return cPlaying > 0; }

    private boolean hasRepeatElement() {
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                if (((SwipeElement) c).isRepeatElement()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();
            URL baseURL = delegate.baseURL();

            final String[] mediaKeys = {"audio" };
            for (String key : mediaKeys) {
                String src = info.optString(key, null);
                if (src != null) {
                    URL url = SwipeBrowserActivity.makeFullURL(src, baseURL);
                    resourceURLs.add(url);
                }
            }

            JSONArray elementsInfo = info.optJSONArray("elements");
            if (elementsInfo != null) {
                CGSize scaleDummy = new CGSize(0.1f, 0.1f);
                for (int i = 0; i < elementsInfo.length(); i++) {
                    SwipeElement element = new SwipeElement(getContext(), dimension, scrDimension, scaleDummy, elementsInfo.optJSONObject(i), this, this);
                    resourceURLs.addAll(element.getResourceURLs());
                }
            }

            if (pageTemplate != null) {
                resourceURLs.addAll(pageTemplate.getResourceURLs());
            }
        }

        return resourceURLs;
    }

    // SwipeElement.Delegate

    @Override
    public double durationSec() { return duration; }

    @Override
    public JSONObject prototypeWith(String name) { return delegate.prototypeWith(name); }

    @Override
    public List<SwipeMarkdown.Element> parseMarkdown(Object markdowns) {
        return delegate.parseMarkdown(markdowns);
    }

    @Override
    public URL baseURL() {
        return delegate.baseURL();
    }

    @Override
    public URL makeFullURL(String url) { return delegate.makeFullURL(url); }

    @Override
    public URL map(URL url) { return delegate.map(url); }

    @Override
    public String localizedStringForKey(String key) {
        // TODO
        return null;
    }

    @Override
    public String languageIdentifier() {
        // TODO
        return null;
    }

    @Override
    public boolean isCurrentPage() {
        return delegate.currentPageIndex() == index;
    }

    @Override
    public boolean shouldRepeat(SwipeElement element) {
        return fEntered && fRepeat;
    }


    @Override
    public void didStartPlaying(SwipeElement element) {
        didStartPlayingInternal();
    }

    @Override
    public void didFinishPlaying(SwipeElement element, boolean completed) {
        didFinishPlayingInternal();
    }

    @Override
    public void onAction(SwipeElement element) {
        String action = element.getAction();
        if (action != null) {
            MyLog(TAG, "onAction " + action, 2);
            if (action.equals("play")) {
                //prepareToPlay()
                //autoPlay()
                play();
            }
        }
    }
}
