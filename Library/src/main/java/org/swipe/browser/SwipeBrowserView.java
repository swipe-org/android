package org.swipe.browser;

import android.app.Activity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import org.swipe.network.SwipePrefetcher;

import org.json.JSONObject;

import java.net.URL;
import java.util.List;

/**
 * Created by pete on 9/13/16.
 */
public abstract class SwipeBrowserView extends LinearLayout implements SwipePrefetcher.Listener {
    private final static String TAG = "SwBrowserView";
    protected Activity activity = null;
    protected ProgressBar progressBar = null;

    public Activity getActivity() { return activity; }

    public SwipeBrowserView(Activity context) {
        super(context);
        activity = context;
        progressBar = (ProgressBar) getActivity().findViewById(R.id.progressBar);
    }

    public interface Delegate {
        void browseTo(String url);
        URL makeFullURL(String url);
        String getFileName();
    }

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
        progressBar.setVisibility(VISIBLE);
    }

    public boolean landscape() { return landscape; }

    public void setDelegate(SwipeBrowserView.Delegate _delegate) {
        delegate = _delegate;
    }

    public void progress(SwipePrefetcher prefetcher) {
        final int progress = (int)(progressBar.getMax() * prefetcher.getProgress());
        if (progress - progressBar.getProgress() < 2) return;

        progressBar.setProgress(progress);
    }

    public void didComplete(SwipePrefetcher prefetcher) {
        progressBar.setProgress(progressBar.getMax());
        post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(INVISIBLE);
            }
        });
    }

    protected void onPause() {

    }

    protected void onResume() {

    }

    //void becomeZombie();
    //func saveState() -> [String:AnyObject]?
    //func languages() -> [[String:AnyObject]]?
    //func reloadWithLanguageId(langId:String)
}
