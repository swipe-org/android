package net.swipe.core;

import android.support.compat.BuildConfig;
import android.util.Log;

import java.util.regex.Pattern;

/**
 * Created by pete on 9/30/16.
 */

public class SwipeParser {
    private static final String TAG = "SParser";

    static {
        // Self test
        assert SwipeParser.parsePercent("10", 100.0, -1.0) == -1;
        assert SwipeParser.parsePercent("10%", 100.0, -1.0) == 10.0;
        assert SwipeParser.parsePercent("100%", 100.0, -1.0) == 100.0;
        assert SwipeParser.parsePercent("1.5%", 100.0, -1.0) == 1.5;
    }

    public static double parsePercent(String value, double full, double defaultValue)  {
        if (Pattern.matches("^[0-9\\\\.]+%$", value)) {
            Double d = new Double(value.trim().replace("%", ""));
            return d.doubleValue() / 100.0 * full;
        } else {
            return defaultValue;
        }
    }

}
