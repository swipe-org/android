package org.swipe.core;

import android.util.Log;

/**
 * Created by pete on 11/16/16.
 */

public class SwipeUtil {
    public static String fileName(String url) { return url.substring( url.lastIndexOf('/')+1, url.length() ); }

    public static void Log(String tag, String text) {
        Log(tag, text, 0);
    }

    public static void Log(String tag, String text, int level) {
        if (level <= 2) {
            Log.d(tag, text);
        }
    }
}
