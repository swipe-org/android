package org.swipe.core;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
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
 */

public class SwipePage extends SwipeView implements SwipeElement.Delegate {

    public interface Delegate {
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
        func currentPageIndex() -> Int
        func parseMarkdown(markdowns:[String]) -> NSAttributedString
        */
        URL baseURL();
        /*
        func voice(k:String?) -> [String:AnyObject]
        func languageIdentifier() -> String?
        func tapped()
        */
    }

    private static final String TAG = "SwPage";
    protected Delegate delegate = null;
    protected int index = -1;
    protected SwipePageTemplate pageTemplate = null;
    protected double duration = 0.2;

    public SwipePage(Context _context, CGSize _dimension, int _index, JSONObject _info, SwipePage.Delegate _delegate) {
        super(_context, _dimension, _info);
        index = _index;
        delegate = _delegate;

        // Expand tempates first
        pageTemplate = delegate.pageTemplateWith(info.optString("template"));
        if (pageTemplate == null) {
            pageTemplate = delegate.pageTemplateWith(info.optString("scene"));
            if (pageTemplate != null) {
                Log.w(TAG, "DEPRECATED 'scene'; use 'template'");
            }
        }

        super.info = SwipeParser.inheritProperties(_info, pageTemplate != null ? pageTemplate.pageTemplateInfo : null);

        duration = info.optDouble("duration", duration);

        List<Animator> animations = new ArrayList<>();

        JSONArray elementsInfo = info.optJSONArray("elements");
        if (elementsInfo != null) {
            CGSize scale = new CGSize(1, 1);
            for (int i = 0; i < elementsInfo.length(); i++) {
                SwipeElement element = new SwipeElement(getContext(), dimension, elementsInfo.optJSONObject(i), scale, this, this);
                children.add(element);
                viewGroup.addView(element.getView());
                animations.addAll(element.getAnimations());
            }
        }

        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(animations);
        animator.start();
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
                    SwipeElement element = new SwipeElement(getContext(), dimension, elementsInfo.optJSONObject(i), scaleDummy, this, this);
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
