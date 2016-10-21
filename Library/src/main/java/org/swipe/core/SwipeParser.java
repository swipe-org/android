package org.swipe.core;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pete on 9/30/16.
 */

public class SwipeParser {
    private static final String TAG = "SwParser";
    private static final Pattern patternPercent = Pattern.compile("^[0-9\\\\.]+%$");
    private static final Pattern patternColor = Pattern.compile("^#([a-f0-9]{6}|[a-f0-9]{3}|[a-f0-9]{8}|[a-f0-9]{4})$", Pattern.CASE_INSENSITIVE);

    static {
        // Self test
        if (SwipeParser.parsePercent("10", 100, -1) != -1) throw new AssertionError();
        if (SwipeParser.parsePercent("10%", 100, -1) != 10.0) throw new AssertionError();
        if (SwipeParser.parsePercent("100%", 100, -1) != 100.0) throw new AssertionError();
        if (SwipeParser.parsePercent("1.5%", 100, -1) != 1.5) throw new AssertionError();

        try {
            if (SwipeParser.parseColor(new JSONObject("{ }"), "bc", 0x01020304) != 0x01020304) throw new AssertionError();
            if (SwipeParser.parseColor(new JSONObject("{ \"bc\":\"#1234\" }"), "bc", 0) != 0x04010203) throw new AssertionError();
            if (SwipeParser.parseColor(new JSONObject("{ \"bc\":\"#01020304\" }"), "bc", 0) != 0x04010203) throw new AssertionError();
            if (SwipeParser.parseColor(new JSONObject("{ \"bc\":\"#123\" }"), "bc", 0) != 0xff010203) throw new AssertionError();
            if (SwipeParser.parseColor(new JSONObject("{ \"bc\":\"#010203\" }"), "bc", 0) != 0xff010203) throw new AssertionError();
        } catch (JSONException e) {
            assert e != null;
        }
    }

    public static float parsePercent(String value, float full, float defaultValue)  {
        if (value != null && patternPercent.matcher(value).matches()) {
            Float v = new Float(value.trim().replace("%", ""));
            return v.floatValue() / 100.0f * full;
        } else {
            return defaultValue;
        }
    }

    static float parseFloat(JSONObject info, String valKey, float defaultValue) {
        if (info == null) {
            return defaultValue;
        }

        Double val = info.optDouble(valKey);
        if (val.isNaN()) {
            return defaultValue;
        } else {
            return val.floatValue();
        }
    }

    static int parseColor(JSONObject info, String valKey, int defaultColor) {
        if (info == null) {
            return defaultColor;
        }

        JSONObject rgba = info.optJSONObject(valKey);
        if (rgba != null) {
            double red = rgba.optDouble("r", 0);
            double green = rgba.optDouble("g", 0);
            double blue = rgba.optDouble("b", 0);
            double alpha = rgba.optDouble("a", 1);
            int c = Color.argb((int)(alpha * 255), (int)(red * 255), (int)(green * 255), (int)(blue * 255));
            //Log.d(TAG, String.format("parseColor rgba %s = 0x%08x", rgba.toString(), c));
            return c;
        } else {
            String value = info.optString(valKey);
            if (value.isEmpty()) {
                return defaultColor;
            }
            
            switch (value) {
                case "red":
                    return Color.RED;
                case "black":
                    return Color.BLACK;
                case "blue":
                    return Color.BLUE;
                case "white":
                    return Color.WHITE;
                case "green":
                    return Color.GREEN;
                case "yellow":
                    return Color.YELLOW;
                case "purple":
                    return Color.argb(0xFF, 0x80, 0x00, 0x80);
                case "gray":
                    return Color.GRAY;
                case "darkGray":
                    return Color.DKGRAY;
                case "lightGray":
                    return Color.LTGRAY;
                case "brown":
                    return Color.argb(0xFF, 0xA5, 0x2A, 0x2A);
                case "orange":
                    return Color.argb(0xFF, 0xFF, 0xA5, 0x00);
                case "cyan":
                    return Color.CYAN;
                case "magenta":
                    return Color.MAGENTA;
                default:
                    if (patternColor.matcher(value).matches()) {
                        final long v = Long.decode(value);
                        //Log.d(TAG, String.format("parseColor(%s) -> 0x%08x)", value, v));
                        long r = 0;
                        long g = 0;
                        long b = 0;
                        long a = 255;
                        switch (value.length() - 1) {
                            case 3:
                                r = v >> 8 & 0x00f;
                                g = v >> 4 & 0x00f;
                                b = v & 0x00f;
                                break;
                            case 4:
                                r = v >> 12 & 0x000f;
                                g = v >> 8 & 0x000f;
                                b = v >> 4 & 0x000f;;
                                a = v & 0x000f;
                                break;
                            case 6:
                                r = v >> 16 & 0x0000ff;
                                g = v >> 8 & 0x0000ff;
                                b = v & 0x0000ff;
                                break;
                            case 8:
                                r = v >> 24 & 0x0000ff;
                                g = v >> 16 & 0x0000ff;
                                b = v >> 8 & 0x0000ff;
                                a = v & 0x0000ff;
                                break;
                            default:
                                break;
                        }
                        int c = Color.argb((int)a, (int)r, (int)g, (int)b);
                        //Log.d(TAG, String.format("parseColor(%s) a:%02x r:%02x g:%02x b:%02x 0x%08x", value, a, r, g, b, c));
                        return c;
                    }
                    return Color.RED;
            }
        }
    }

    public static Path transformedPath(final Path path, final JSONObject info, final String key, final float w, final float h)  {
        if (info == null) {
            return null;
        }

        CGSize scale = null;
        Double d = info.optDouble(key);
        if (!d.isNaN()) {
            float s = d.floatValue();
            scale = new CGSize(s, s);
        } else {
            JSONArray scales = info.optJSONArray(key);
            if (scales != null &&  scales.length() == 2) {
                Double d0 = scales.optDouble(0);
                Double d1 = scales.optDouble(1);
                if (!d0.isNaN() && !d1.isNaN()) {
                    scale = new CGSize(d0.floatValue(), d1.floatValue());
                }
            }
        }

        if (scale == null) {
            return null;
        }

        Matrix xform = new Matrix();
        xform.setTranslate(-w / 2, -h / 2);
        xform.postScale(scale.width, scale.height);
        xform.postTranslate(w / 2, h / 2);
        Path xpath = new Path();
        path.transform(xform, xpath);
        return xpath;
    }


    //
    // This function performs the "deep inheritance"
    //   Object property: Instance properties overrides Base properties
    //   Array property: Merge (inherit properties in case of "id" matches, otherwise, just append)
    //
    static JSONObject inheritProperties(JSONObject object, JSONObject prototype) {
        final String TAG = "SwInheritProperties";
        JSONObject ret = object;
        if (prototype != null) {
            Iterator<String> keyStrings = prototype.keys();
            while (keyStrings.hasNext()) {
                try {
                    String keyString = keyStrings.next();
                    if (!ret.has(keyString)) {
                        // Only the baseObject has the property
                        ret.put(keyString, prototype.get(keyString));
                    } else if (ret.optJSONArray(keyString) != null && prototype.optJSONArray(keyString) != null) {
                        // Each has the property array. We need to merge them
                        JSONArray arrayObject = ret.optJSONArray(keyString);
                        JSONArray retArray = prototype.optJSONArray(keyString);
                        Map<String, Integer> idMap = new HashMap<>();
                        for (int index = 0; index < retArray.length(); index++) {
                            JSONObject item = retArray.getJSONObject(index);
                            String key = item.optString("id");
                            if (key != null) {
                                idMap.put(key, Integer.valueOf(index));
                            }
                        }

                        for (int i = 0; i < arrayObject.length(); i++) {
                            JSONObject item = arrayObject.getJSONObject(i);
                            String key = item.optString("id");
                            if (key != null) {
                                Integer index = idMap.get(key);
                                if (index != null) {
                                    // id matches, merge them
                                    retArray.put(index.intValue(),SwipeParser.inheritProperties(item, retArray.getJSONObject(index.intValue())));
                                } else {
                                    // no id match, just append
                                    retArray.put(item);
                                }
                            } else {
                                // no id, just append
                                retArray.put(item);
                            }
                        }
                        ret.put(keyString, retArray);
                    } else if (ret.optJSONObject(keyString) != null && prototype.optJSONObject(keyString) != null) {
                        // Each has the property objects. We need to merge them.  Example: '"events" { }'
                        JSONObject objects = ret.getJSONObject(keyString);
                        JSONObject retObjects = prototype.getJSONObject(keyString);
                        Iterator<String> keys = objects.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            retObjects.put(key, objects.get(key));
                        }
                        ret.put(keyString, retObjects);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, e.toString());
                }
            }
        }
        return ret;
    }

}
