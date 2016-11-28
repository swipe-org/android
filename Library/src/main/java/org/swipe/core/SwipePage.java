package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;
import org.swipe.browser.SwipeBrowserActivity;
import org.swipe.network.SwipeAssetManager;

import java.io.FileDescriptor;
import java.io.IOException;
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
        JSONObject prototypeWithName(String name);
        SwipePageTemplate pageTemplateWithName(String name);
        Object pathWithName(String name);
        Object voiceWithName(String name);
        void speak(Object utterance);
        void stopSpeaking();
        int currentPageIndex();
        String langId();
        List<SwipeMarkdown.Element> parseMarkdown(Object markdowns);
        URL baseURL();
        URL map(URL url);
        URL makeFullURL(String url);
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
    private Object utterance = null;

    private MediaPlayer audioPlayer = null;
    private boolean fAudioSeeking = false;
    private boolean fAudioPrepared = false;
    private boolean fAudioStartWhenPrepared = false;
    private boolean fAudioStartWhenSeeked = false;

    SwipePage(Context _context, CGSize _dimension, CGSize _scrDimension, CGSize _scale, int _index, JSONObject _info, SwipePage.Delegate _delegate) {
        super(_context, _dimension, _scrDimension, _scale, _info, /* parent */ null);
        index = _index;
        delegate = _delegate;

        pageTemplate = delegate.pageTemplateWithName(info.optString("template"));
        if (pageTemplate == null) {
            pageTemplate = delegate.pageTemplateWithName(info.optString("scene"));
            if (pageTemplate != null) {
                SwipeUtil.Log(TAG, "DEPRECATED 'scene'; use 'template'");
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
                //SwipeUtil.Log(TAG, "onLayout");
                setClipChildren(false);

                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    //SwipeUtil.Log(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
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
        SwipeUtil.Log(TAG, "loadView " + index, 2);
        int bc = SwipeParser.parseColor(info, "bc", Color.WHITE);
        viewGroup.setBackgroundColor(bc);

        duration = (float)info.optDouble("duration", 0.2f);
        fps = info.optInt("fps", 60);
        vibrate = info.optBoolean("vibrate", false);
        fRepeat = info.optBoolean("repeat", false);
        rewind = info.optBoolean("rewind", false);

        String oldAnimation = info.optString("animation", null);
        if (oldAnimation != null) {
            SwipeUtil.Log(TAG, "DEPRECATED 'animation'; use 'play'");
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

        String value = info.optString("audio", null);
        if (value != null) {
            URL urlRaw = delegate.makeFullURL(value);
            URL url = delegate.map(urlRaw);
            if (url != null) {
                SwipeUtil.Log(TAG, "audio=" + value);
                try {
                    FileDescriptor fd = SwipeAssetManager.sharedInstance().loadLocalAsset(url).getFD();
                    if (fd != null) {
                        audioPlayer = new MediaPlayer();
                        try {
                            audioPlayer.setDataSource(fd);
                            audioPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    fAudioPrepared = true;
                                    if (fAudioStartWhenPrepared) {
                                        fAudioStartWhenPrepared = false;
                                        audioPlayer.start();
                                    }
                                }
                            });
                            audioPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    if (audioPlayer.getCurrentPosition() != 0) {
                                        fAudioSeeking = true;
                                        audioPlayer.seekTo(0);
                                    }
                                }
                            });
                            audioPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                                @Override
                                public void onSeekComplete(MediaPlayer mp) {
                                    fAudioSeeking = false;
                                    if (fAudioStartWhenSeeked) {
                                        fAudioStartWhenSeeked = false;
                                        audioPlayer.start();
                                    }
                                }
                            });

                            audioPlayer.prepareAsync();
                        } catch (IOException e) {
                            audioPlayer = null;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

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
        SwipeUtil.Log(TAG, "willLeave " + (index) + " " + fAdvancing, 2);
        if (utterance != null) {
            delegate.stopSpeaking();
            prepareUtterance(); // recreate a new utterance to avoid reusing itt
        }
    }

    void pause(boolean fForceRewind) {
        SwipeUtil.Log(TAG, "pause " + (index) + " " + fForceRewind, 2);

        fPausing = true;

        if (audioPlayer != null) {
            audioPlayer.pause();
            fAudioSeeking = true;
            audioPlayer.seekTo(0);
        }

        NotificationCenter.defaultCenter().postNotification(SwipePage.shouldPauseAutoPlay);

        // auto rewind
        if (rewind || fForceRewind) {
            prepareToPlay(true);
        }
    }

    void didLeave(boolean fGoingBack) {
        SwipeUtil.Log(TAG, "didLeave " + (index) + " " + fGoingBack, 2);
        fEntered = false;
        pause(fGoingBack);
    }

    void willEnter(boolean fForward) {
        SwipeUtil.Log(TAG, "willEnter " + index + " " + fForward, 2);
        if (autoplay && fForward || always) {
            prepareToPlay(true);
        }
        if (fForward && scroll) {
            playAudio();
        }
    }

    private void playAudio() {
        if (audioPlayer != null) {
            if (fAudioPrepared) {
                if (fAudioSeeking) {
                    fAudioStartWhenSeeked = true;
                } else {
                    audioPlayer.start();
                }
            } else {
                fAudioStartWhenPrepared = true;
            }
        }
        /* TODO
        if let utterance = self.utterance {
            delegate.speak(utterance)
        }
        if self.vibrate {
            AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_Vibrate))
        }
        */
    }

    void didEnter(boolean fForward) {
        SwipeUtil.Log(TAG, "didEnter " + index + " " + fForward, 2);
        fEntered = true;
        if ((fForward && autoplay) || always || fRepeat) {
            autoPlay(false);
        } else if (hasRepeatElement()) {
            autoPlay(true);
        }
    }

    void prepare() {
        SwipeUtil.Log(TAG, "prepare " + (index), 2);

        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                e.prepare();
            }
        }

        if (scroll) {
            prepareToPlay(index > delegate.currentPageIndex());
        } else {
            if (index < delegate.currentPageIndex()) {
                prepareToPlay(rewind);
            }
        }
    }

    void release() {
        for (SwipeNode c : children) {
            if (c instanceof SwipeElement) {
                SwipeElement e = (SwipeElement)c;
                e.release();
            }
        }
    }

    private void prepareToPlay(boolean fForward) {
        SwipeUtil.Log(TAG, "prepareToPlay " + (index) + " " + fForward, 2);

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
        if (utterance != null) {
            delegate.stopSpeaking();
            prepareUtterance(); // recreate a new utterance to avoid reusing it
        }

        autoPlay(false);
    }

    private void autoPlay(boolean fElementRepeat) {
        SwipeUtil.Log(TAG, "autoplay " + fElementRepeat, 2);
        fPausing = false;
        if (!fElementRepeat) {
            playAudio();
            NotificationCenter.defaultCenter().postNotification(SwipePage.shouldStartAutoPlay);
        }

        SwipeObjectAnimator.resetInstrumentation();

        if (offsetPaused != null) {
            timerTick(offsetPaused.floatValue(), fElementRepeat, SystemClock.elapsedRealtime());
        } else {
            timerTick(0.0f, fElementRepeat, SystemClock.elapsedRealtime());
        }
        didStartPlayingInternal();
    }

    int accum = 0;
    int actualDuration = 0;
    int skipCnt = 0;

    private void timerTick(final float offset, final boolean fElementRepeat, final long callTime) {
        // During the shutdown sequence, the loop will stop when didLeave was called.
        final float kFrameOffset = 1.0f / duration / fps;
        final int kFrameMsec = 1000 / fps;

        viewGroup.postDelayed(new Runnable() {
            @Override
            public void run() {
                long startTime = SystemClock.elapsedRealtime();
                long frameDuration = startTime - callTime;
                accum += frameDuration - kFrameMsec;
                actualDuration += frameDuration;
                if (frameDuration > kFrameMsec) {
                    //SwipeUtil.Log(TAG, "frame duration delta:" + (frameDuration - kFrameMsec));
                }
                boolean fElementRepeatNext = fElementRepeat;
                Float offsetForNextTick = null;
                if (fEntered && !fPausing) {
                    float nextOffset = offset + kFrameOffset;

                    if (accum >= kFrameMsec) {
                        int frames = (int)(accum / kFrameMsec);
                        skipCnt += frames;
                        accum -= kFrameMsec * frames;
                        nextOffset += kFrameOffset * frames;
                    }
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
                    timerTick(offsetForNextTick, fElementRepeatNext, startTime);
                } else {
                    SwipeUtil.Log(TAG, "skipped:" + skipCnt + " frames");
                    SwipeObjectAnimator.printInstrumentation();
                    actualDuration = 0;
                    accum = 0;
                    skipCnt = 0;
                    offsetPaused = fPausing ? offset : null;
                    didFinishPlayingInternal();
                }
            }
        }, kFrameMsec);
    }

    private void didStartPlayingInternal() {
        cPlaying += 1;
        if (cPlaying == 1) {
            SwipeUtil.Log(TAG, "didStartPlaying " + index, 5);
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

    private void prepareUtterance() {
        JSONObject speech = info.optJSONObject("speech");
        if (speech == null) return;

        String text = parseText(this, speech, "text");
        if (text == null) return;
        /* TODO
        let voice = self.delegate.voice(speech["voice"] as? String)
        let utterance = AVSpeechUtterance(string: text)

        // BCP-47 code
        if let lang = voice["lang"] as? String {
            // HACK: Work-around an iOS9 bug
            // http://stackoverflow.com/questions/30794082/how-do-we-solve-an-axspeechassetdownloader-error-on-ios
            // https://forums.developer.apple.com/thread/19079?q=AVSpeechSynthesisVoice
            let voices = AVSpeechSynthesisVoice.speechVoices()
            var theVoice:AVSpeechSynthesisVoice?
            for voice in voices {
                //NSLog("SWPage lang=\(voice.language)")
                if lang == voice.language {
                    theVoice = voice
                    break;
                }
            }
            if let voice = theVoice {
                utterance.voice = voice
            } else {
                NSLog("SWPage  Voice for \(lang) is not available (iOS9 bug)")
            }
            // utterance.voice = AVSpeechSynthesisVoice(language: lang)
        }

        if let pitch = voice["pitch"] as? Float {
            if pitch >= 0.5 && pitch < 2.0 {
                utterance.pitchMultiplier = pitch
            }
        }
        if let rate = voice["rate"] as? Float {
            if rate >= 0.0 && rate <= 1.0 {
                utterance.rate = AVSpeechUtteranceMinimumSpeechRate + (AVSpeechUtteranceDefaultSpeechRate - AVSpeechUtteranceMinimumSpeechRate) * rate
            } else if rate > 1.0 && rate <= 2.0 {
                utterance.rate = AVSpeechUtteranceDefaultSpeechRate + (AVSpeechUtteranceMaximumSpeechRate - AVSpeechUtteranceDefaultSpeechRate) * (rate - 1.0)
            }
        }
        self.utterance = utterance
    */
    }

    private String parseText(SwipeNode originator, JSONObject info, String key) {
        if (info == null) return null;

        Object text = info.opt(key);
        if (text != null) return null;

        if (text instanceof String) return (String) text;

        if (text instanceof JSONObject) {
            String ref = ((JSONObject) text).optString("ref", null);
            return localizedStringForKey(ref);
        }

        return null;
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
    public JSONObject prototypeWithName(String name) { return delegate.prototypeWithName(name); }

    @Override
    public Object pathWithName(String name) { return delegate.pathWithName(name); }

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
        JSONObject strings = info.optJSONObject("strings");
        if (strings != null) {
            JSONObject texts = strings.optJSONObject(key);
            if (texts != null) {
                return SwipeParser.localizedString(texts, delegate.langId());
            }
        }
        return null;
    }

    @Override
    public String langId() {
        return delegate.langId();
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
            SwipeUtil.Log(TAG, "onAction " + action, 2);
            if (action.equals("play")) {
                //prepareToPlay()
                //autoPlay()
                play();
            }
        }
    }
}
