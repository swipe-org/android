package org.swipe.browser;

import android.app.Activity;
import android.widget.LinearLayout;

import org.swipe.network.SwipePrefetcher;

import org.json.JSONObject;

import java.net.URL;
import java.util.List;

/**
 * Created by pete on 9/13/16.
 */
public abstract class SwipeBrowserView extends LinearLayout {
    protected Activity activity = null;

    public Activity getActivity() { return activity; }

    public SwipeBrowserView(Activity context) {
        super(context);
        activity = context;
    }

    public interface Delegate {
        void browseTo(String url);
        URL makeFullURL(String url);
        String getFileName();
    }

    private final static String TAG = "SwBrowserView";
    protected SwipeBrowserView.Delegate delegate = null;
    protected SwipePrefetcher prefetcher = null;
    protected JSONObject document = null;
    protected List<URL> resourceURLs = null;
    protected URL baseURL = null;
    private boolean landscape = false;

    // Returns the list of URLs of required resources for this element (including children)
    public abstract List<URL> getResourceURLs();

    public String documentTitle() {
        return document.optString("title", "");
    }

    public void loadDocument(JSONObject _document, URL url) {
        document = _document;
        baseURL = url;
        landscape = document.optString("orientation").equalsIgnoreCase("landscape");
    }

    public boolean landscape() { return landscape; }

    public void setDelegate(SwipeBrowserView.Delegate _delegate) {
        delegate = _delegate;
    }

    //void becomeZombie();
    //func saveState() -> [String:AnyObject]?
    //func languages() -> [[String:AnyObject]]?
    //func reloadWithLanguageId(langId:String)
}
