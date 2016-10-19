package org.swipe.core;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.swipe.browser.SwipeBrowserActivity;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
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
        func prototypeWith(name:String?) -> [String:AnyObject]?
        */
        SwipePageTemplate pageTemplateWith(String name);
        /*
        func pathWith(name:String?) -> AnyObject?
        func speak(utterance:AVSpeechUtterance)
        func stopSpeaking()
        */
        int currentPageIndex();
        /*
        func parseMarkdown(markdowns:[String]) -> NSAttributedString
        */
        URL baseURL();
        /*
        func voice(k:String?) -> [String:AnyObject]
        func languageIdentifier() -> String?
        func tapped()
        */
    }

    private class SwipeAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
        }
    }

    private class SwipeAnimatorListener implements ValueAnimator.AnimatorListener {

        @Override
        public void onAnimationStart(Animator animation) {

        }

        @Override
        public void onAnimationEnd(Animator animation) {

        }

        @Override
        public void onAnimationCancel(Animator animation) {

        }

        @Override
        public void onAnimationRepeat(Animator animation) {

        }
    }

    private class SwipeAnimatorPauseListener implements Animator.AnimatorPauseListener {
        private static final String TAG = "SwBook animator";
        @Override
        public void onAnimationPause(Animator animation) {
            Log.d(TAG, "pause");
            //cnt = animator.getChildAnimations().size();
            //animator.start();
        }

        @Override
        public void onAnimationResume(Animator animation) {

        }
    }

    private static final String TAG = "SwPage";
    private Delegate delegate = null;
    private SwipePageTemplate pageTemplate = null;
    private float duration = 0.2f;
    private String animation = "auto";
    private String transition = null;
    private int index = -1;
    private int fps = 60;
    private int cDebug = 0;
    private int cPlaying = 0;
    private boolean vibrate = false;
    private boolean repeat = false;
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
    private AnimatorSet animator = new AnimatorSet();

    SwipePage(Context _context, CGSize _dimension, CGSize _scale, int _index, JSONObject _info, SwipePage.Delegate _delegate) {
        super(_context, _dimension, _scale, _info);
        index = _index;
        delegate = _delegate;

        pageTemplate = delegate.pageTemplateWith(info.optString("template"));
        if (pageTemplate == null) {
            pageTemplate = delegate.pageTemplateWith(info.optString("scene"));
            if (pageTemplate != null) {
                Log.w(TAG, "DEPRECATED 'scene'; use 'template'");
            }
        }

        super.info = SwipeParser.inheritProperties(_info, pageTemplate != null ? pageTemplate.pageTemplateInfo : null);
    }

    int getIndex() { return index; }
    @Override
    ViewGroup loadView() {
        super.loadView();
        duration = (float)info.optDouble("duration", duration);
        fps = info.optInt("fps", fps);
        vibrate = info.optBoolean("vibrate", vibrate);
        repeat = info.optBoolean("repeat", repeat);
        rewind = info.optBoolean("rewind", rewind);

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

        List<Animator> animations = new ArrayList<>();

        JSONArray elementsInfo = info.optJSONArray("elements");
        if (elementsInfo != null) {
            for (int i = 0; i < elementsInfo.length(); i++) {
                SwipeElement element = new SwipeElement(getContext(), dimension, scale, elementsInfo.optJSONObject(i), this, this);
                children.add(element);
                viewGroup.addView(element.loadView());
                List<ObjectAnimator> eAnimations = element.getAllAnimations();
                for (ObjectAnimator ani : eAnimations) {
                    ani.addUpdateListener(new SwipeAnimatorUpdateListener());
                }
                animations.addAll(eAnimations);
            }
        }

        animator.playTogether(animations);
        animator.addListener(new SwipeAnimatorListener());
        animator.addPauseListener(new SwipeAnimatorPauseListener());
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

        NSNotificationCenter.defaultCenter().postNotificationName(SwipePage.shouldPauseAutoPlay, object: self)
        */

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
        if ((fForward && autoplay) || always || repeat) {
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
            final float offset = fForward ? 0.0f : 1.0f;

            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;

                e.setTimeOffsetTo(offset);
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
        fPausing = false;
        if (!fElementRepeat) {
            playAudio();
            // TOD) NSNotificationCenter.defaultCenter().postNotificationName(SwipePage.shouldStartAutoPlay, object: self)
        }
        if (offsetPaused != null) {
            timerTick(offsetPaused.floatValue(), fElementRepeat);
        } else {
            timerTick(0.0f, fElementRepeat);
        }
        cDebug += 1;
        cPlaying += 1;
        didStartPlayingInternal();
    }

    private void timerTick(final float offset, final boolean fElementRepeat) {
        // NOTE: We don't want to add [unowned self] because the timer will fire anyway.
        // During the shutdown sequence, the loop will stop when didLeave was called.
        viewGroup.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean fElementRepeatNext = fElementRepeat;
                Float offsetForNextTick = null;
                if (fEntered && !fPausing) {
                    float nextOffset = offset + 1.0f / fps;
                    if (nextOffset < 1.0f) {
                        offsetForNextTick = nextOffset;
                    } else {
                        nextOffset = 1.0f;
                        if (repeat) {
                            playAudio();
                            offsetForNextTick = 0.0f;
                        } else if (hasRepeatElement()) {
                            offsetForNextTick = 0.0f;
                            fElementRepeatNext = true;
                        }
                    }
                    if (!fElementRepeatNext) {
                        // TODO I don't this we need this in Java: self.aniLayer?.timeOffset = CFTimeInterval(nextOffset)
                    }
                    for (SwipeNode c : children) {
                        if (c instanceof SwipeElement) {
                            ((SwipeElement) c).setTimeOffsetTo(nextOffset, true);
                        }
                    }
                }
                if (offsetForNextTick != null) {
                    timerTick(offsetForNextTick, fElementRepeat);
                } else {
                    offsetPaused = fPausing ? offset : null;
                    cPlaying -= 1;
                    cDebug -= 1;
                    didFinishPlayingInternal();
                }

            }
        }, 1000 / fps);
    }

    private void didStartPlayingInternal() {
        cPlaying += 1;
        if (cPlaying == 1) {
            MyLog(TAG, "didStartPlaying " + index, 5);
            // TODO NSNotificationCenter.defaultCenter().postNotificationName(SwipePage.didStartPlaying, object: self)
        }
    }

    private void didFinishPlayingInternal() {
        if (cPlaying < 0) throw new AssertionError( "didFinishPlaying going negative! " + index);
        cPlaying -= 1;
        if (cPlaying == 0) {
            // TODO NSNotificationCenter.defaultCenter().postNotificationName(SwipePage.didFinishPlaying, object: self)
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
                    SwipeElement element = new SwipeElement(getContext(), dimension, scaleDummy, elementsInfo.optJSONObject(i), this, this);
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
    public URL baseURL() {
        return delegate.baseURL();
    }
}
