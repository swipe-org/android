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

    private static final String TAG = "SwPref";
    private List<URL> urls = new ArrayList<>();
    private Map<URL, Integer> urlsFetching = new HashMap<>();
    private Map<URL, URL> urlsFetched = new HashMap<>();
    private List<URL> urlsFailed = new ArrayList<>();
    private List<Exception> errors = new ArrayList<>();
    private boolean fComplete = false;
    private float progress = 0;
    private int count = 0;

    public float  getProgress() {
        return progress;
    }

    private void updateListener(final Listener listener) {
        count -= 1;
        if (count == 0) {
            fComplete = true;
            progress = 1;
            Log.d(TAG, "completed " + urlsFetched.size());
            //callback(true, self.urlsFailed, self.errors)
            listener.didComplete(this);
        } else {
            progress = (float)(urlsFetched.size() - count) / (float)(urlsFetched.size());
            //callback(false, self.urlsFailed, self.errors)
            listener.progress(this);
        }
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

        for (final URL url : resourceURLs) {
            if (urlsFetching.containsKey(url)) {
                //Log.d(TAG, "skipping " + url);
                updateListener(listener);
                continue;
            }

            urlsFetching.put(url, 0);
            Log.d(TAG, "fetching " + url);

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
                        //Log.d(TAG, "fetched " + localURL);
                        urlsFetched.put(url, localURL);
                    } else {
                        urlsFailed.add(url);
                        errors.add(error);
                    }

                    updateListener(listener);
                }
            });
        }
    }

    public URL map(URL url) {
        URL found = urlsFetched.get(url);
        if (found != null) {
            //Log.d(TAG, "found " + url.toString());
        } else {
            Log.d(TAG, "not found " + url.toString());
        }

        return found;
    }
}
