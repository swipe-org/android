package org.swipe.core;

/**
 * Created by pete on 11/16/16.
 */

public class SwipeUtil {
    public static String fileName(String url) { return url.substring( url.lastIndexOf('/')+1, url.length() ); }
}
