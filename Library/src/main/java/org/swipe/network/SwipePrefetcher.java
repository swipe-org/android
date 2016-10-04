package org.swipe.network;

import android.util.Log;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by pete on 9/21/16.
 */

public class SwipePrefetcher {
    public interface Listener {
        // These are all called from a worker thread so implementations should runOnUiThread as desired
        public void didComplete(SwipePrefetcher prefetcher);
        public void progress(SwipePrefetcher prefetcher);
    }

    private static final String TAG = "SPrefetcher";
    protected List<URL> urls = new ArrayList<>();
    protected List<URL> urlsFetching = new ArrayList<>();
    protected Map<URL, URL> urlsFetched = new HashMap<>();
    protected List<URL> urlsFailed = new ArrayList<>();
    protected List<Exception> errors = new ArrayList<>();
    protected boolean fComplete = false;
    protected float progress = 0;
    protected int count = 0;

    public float  getProgress() {
        return progress;
    }

    public SwipePrefetcher() {
        // Required empty public constructor
    }

    public void get(final List<URL> resourceURLs, final Listener listener) {
        Log.d(TAG, "url count = " + resourceURLs.size());

        if (fComplete) {
            Log.d(TAG, "already completed");
            //callback(true, self.urlsFailed, self.errors);
            listener.didComplete(this);
            return;
        }

        count = resourceURLs.size();
        if (count == 0) {
            fComplete = true;
            progress = 1;
            //callback(true, urlsFailed, errors)
            listener.didComplete(this);
            return;
        }

        SwipeAssetManager manager = SwipeAssetManager.sharedInstance();
        final SwipePrefetcher prefetcher = this;

        for (final URL url : resourceURLs) {
            urlsFetching.add(url);

            manager.loadAsset(url, false, new SwipeAssetManager.LoadAssetRunnable() {
                @Override
                public void run() {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (localURL != null) {
                        Log.d(TAG, "fetched " + localURL);
                        urlsFetched.put(url, localURL);
                    }else{
                        urlsFailed.add(url);
                        errors.add(error);
                    }
                    count -= 1;
                    if (count == 0) {
                        fComplete = true;
                        progress = 1;
                        Log.d(TAG, "completed " + urlsFetched.size());
                        //callback(true, self.urlsFailed, self.errors)
                        listener.didComplete(prefetcher);
                    } else {
                        progress = (float)(urlsFetched.size() - count) / (float)(urlsFetched.size());
                        //callback(false, self.urlsFailed, self.errors)
                        listener.progress(prefetcher);
                    }
                }
            });
        }
    }

    public URL map(URL url) {
        URL found = urlsFetched.get(url);
        if (found != null) {
            Log.d(TAG, "found " + url.toString());
        } else {
            Log.d(TAG, "not found " + url.toString());
        }

        return found;
    }
}
