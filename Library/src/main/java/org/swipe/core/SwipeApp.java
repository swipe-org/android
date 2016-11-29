package org.swipe.core;

import android.app.Application;

import org.swipe.network.SwipeAssetManager;

/**
 * Created by pete on 11/29/16.
 */

public class SwipeApp extends Application {
    private static final String TAG = "SwApp";

    @Override
    public void onCreate() {
        super.onCreate();
        SwipeAssetManager.sharedInstance().reduce();
    }
}
