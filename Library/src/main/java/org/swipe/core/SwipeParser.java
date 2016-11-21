package org.swipe.core;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
            if (SwipeParser.parseColor((new JSONObject("{ }")).optString("bc"), 0x01020304) != 0x01020304)
                throw new AssertionError();
            if (SwipeParser.parseColor((new JSONObject("{ \"bc\":\"#1234\" }")).optString("bc"), 0) != 0x44112233)
                throw new AssertionError();
            if (SwipeParser.parseColor((new JSONObject("{ \"bc\":\"#01020304\" }")).optString("bc"), 0) != 0x04010203)
                throw new AssertionError();
            if (SwipeParser.parseColor((new JSONObject("{ \"bc\":\"#123\" }")).optString("bc"), 0) != 0xff112233)
                throw new AssertionError();
            if (SwipeParser.parseColor((new JSONObject("{ \"bc\":\"#010203\" }")).optString("bc"), 0) != 0xff010203)
                throw new AssertionError();
        } catch (JSONException e) {
            assert e != null;
        }
    }

    public static float parsePercent(String value, float full, float defaultValue) {
        if (value != null && patternPercent.matcher(value).matches()) {
            Float v = new Float(value.trim().replace("%", ""));
            return v.floatValue() / 100.0f * full;
        } else {
            return defaultValue;
        }
    }

    static CGSize parseSize(Object param, CGSize defaultValue, CGSize scale) {
        if (param != null && param instanceof JSONArray && ((JSONArray) param).length() == 2) {
            JSONArray values = (JSONArray)param;
            Double val0 = values.optDouble(0);
            Double val1 = values.optDouble(1);
            if (!val0.isNaN() && !val1.isNaN()) {
                return new CGSize(val0.floatValue() * scale.width, val1.floatValue() * scale.height);
            }
        }

        return new CGSize(defaultValue.width * scale.width, defaultValue.height * scale.height);
    }

    static float parsePercentAny(Object value, float full, float defaultValue) {
        if (value != null) {
            if (value instanceof Double) {
                return ((Double) value).floatValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).floatValue();
            } else if (value instanceof String) {
                return SwipeParser.parsePercent((String) value, full, defaultValue);
            }
        }
        return defaultValue;
    }

    static float parseFloat(JSONObject info, String key, float defaultValue) {
        return parseFloat(info.opt(key), defaultValue);
    }

    static float parseFloat(Object info, float defaultValue) {
        if (info != null) {
            if (info instanceof Double) {
                Double val = (Double) info;
                return val.floatValue();
            } else {
                return parseInt(info, (int)defaultValue);
            }
        }

        return defaultValue;
    }

    static int parseInt(Object info, int defaultValue) {
        if (info != null) {
            if (info instanceof Number) {
                Number val = (Number) info;
                return val.intValue();
            }
        }

        return defaultValue;
    }

    static int parseColor(Object info) {
        return parseColor(info, Color.TRANSPARENT);
    }
    static int parseColor(JSONObject info, String key, int defaultColor) {
        return parseColor(info.opt(key), defaultColor);
    }

    static int parseColor(Object info, int defaultColor) {
        if (info == null) {
            return defaultColor;
        }

        if (info instanceof JSONObject) {
            JSONObject rgba = (JSONObject)info;
            double red = rgba.optDouble("r", 0);
            double green = rgba.optDouble("g", 0);
            double blue = rgba.optDouble("b", 0);
            double alpha = rgba.optDouble("a", 1);
            int c = Color.argb((int) (alpha * 255), (int) (red * 255), (int) (green * 255), (int) (blue * 255));
            //Log.d(TAG, String.format("parseColor rgba %s = 0x%08x", rgba.toString(), c));
            return c;
        } else if (info instanceof String) {
            String value = (String) info;
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
                                r = v / 0x100 * 0x11;
                                g = v / 0x10 % 0x10 * 0x11;
                                b = v % 0x10 * 0x11;
                                break;
                            case 4:
                                r = v / 0x1000 * 0x11;
                                g = v / 0x100 % 0x10 * 0x11;
                                b = v / 0x10 % 0x10 * 0x11;
                                a = v % 0x10 * 0x11;
                                break;
                            case 6:
                                r = v / 0x10000;
                                g = v / 0x100;
                                b = v;
                                break;
                            case 8:
                                r = v / 0x1000000;
                                g = v / 0x10000;
                                b = v / 0x100;
                                a = v;
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
        } else {
            return defaultColor;
        }
    }


    static JSONObject parseTransform(JSONObject param, JSONObject _base, boolean fSkipTranslate, boolean fSkipScale) {
        final String TAG = "SwParser.parseTransform";
        if (param == null) {
            return null;
        }

        try {
            boolean hasValue = false;
            JSONObject transform = new JSONObject("{}");
            JSONObject value = new JSONObject(param.toString()); // copy
            JSONObject b = null;

            if (_base != null) {
                b = new JSONObject(_base.toString()); // copy

                Iterator<String> basekeys = b.keys();
                while (basekeys.hasNext()) {
                    String key = basekeys.next();
                    switch (key) {
                        case "translate":
                        case "rotate":
                        case "scale":
                            if (!value.has(key)) {
                                value.put(key, b.get(key));
                            }
                            break;
                    }
                }
            }

            if (fSkipTranslate) {
                if (b != null && b.has("translate")) {
                    transform.put("translate", b.get("translate"));
                }
            } else if (value.has("translate")) {
                transform.put("translate", value.get("translate"));
                hasValue = true;
            }

            if (value.has("depth")) {
                transform.put("depth", value.get("depth"));
                hasValue = true;
            }
            if (value.has("rotate")) {
                transform.put("rotate", value.get("rotate"));
                hasValue = true;
            }

            if (!fSkipScale) {
                if (value.has("scale")) {
                    transform.put("scale", value.get("scale"));
                    hasValue = true;
                }
            }

            return hasValue ? transform : null;
        } catch (JSONException e) {
            Log.e(TAG, "parseTransform exception " + e);
            return null;
        }
    }


    //
    // This function performs the "deep inheritance"
    //   Object property: Instance properties overrides Base properties
    //   Array property: Merge (inherit properties in case of "id" matches, otherwise, just append)
    //
    static JSONObject inheritProperties(JSONObject object, JSONObject _baseObject) {
        final String TAG = "SwParser.inheritProperties";
        try {
            JSONObject ret = new JSONObject(object.toString()); // copy

            if (_baseObject != null) {
                JSONObject prototype = new JSONObject(_baseObject.toString()); // copy
                Iterator<String> protoKeys = prototype.keys();

                while (protoKeys.hasNext()) {
                    String keyString = protoKeys.next();
                    Object value = prototype.get(keyString);
                    Log.d(TAG, "keyString: " + keyString);

                    if (!ret.has(keyString)) {
                        // Only the prototype has the property so simply add it
                        ret.put(keyString, value);

                    } else if (ret.optJSONArray(keyString) != null && value instanceof JSONArray) {
                        if (keyString.equals("elements")) {
                            JSONArray retArray = (JSONArray) value;
                            JSONArray arrayObject = ret.getJSONArray(keyString);
                            Map<String, Integer> idMap = new HashMap<>();

                            for (int index = 0; index < retArray.length(); index++) {
                                JSONObject item = retArray.getJSONObject(index);
                                if (item != null) {
                                    String key = item.optString("id");
                                    if (key != null) {
                                        idMap.put(key, index);
                                    }
                                }
                            }

                            for (int i = 0; i < arrayObject.length(); i++) {
                                JSONObject item = arrayObject.getJSONObject(i);
                                String key = item.optString("id", null);
                                if (key != null) {
                                    Integer index = idMap.get(key);
                                    if (index != null) {
                                        // id matches, merge them
                                        retArray.put(index, SwipeParser.inheritProperties((JSONObject)item, retArray.getJSONObject(index)));
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
                        } else {
                            Log.d(TAG, "ignoring " + keyString);
                        }
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
                }
            }

            return ret;
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
    }

    static String localizedString(JSONObject params, String langId) {
        if (langId != null) {
            String text = params.optString(langId, null);
            if (text != null) {
                return text;
            }
        }

        return params.optString("*", null);
    }

    static float parseFontSize(JSONObject info, float full, float defaultValue, boolean markdown) {
        if (info == null) {
            return defaultValue;
        }

        final String key = markdown ? "size" : "fontSize";

        Double sizeValue = info.optDouble(key);
        if (!sizeValue.isNaN()){
            return sizeValue.floatValue();
        }

        return SwipeParser.parsePercent(info.optString(key, null), full, defaultValue);
    }

    static List<String> parseFontName(JSONObject info, boolean markdown) {
        List<String> fontNames = new ArrayList<>();

        if (info != null) {
            final String key = markdown ? "name" : "fontName";
            String name = info.optString(key, null);
            if (name != null) {
                fontNames.add(name);
            } else {
                JSONArray names = info.optJSONArray(key);
                if (names != null){
                    for (int i = 0; i < names.length(); i++) {
                        name = names.optString(i, null);
                        if (name != null) {
                            fontNames.add(name);
                        }
                    }
                }
            }
        }

        return fontNames;
    }
}
