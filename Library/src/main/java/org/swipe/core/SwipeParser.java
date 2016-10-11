package org.swipe.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by pete on 9/30/16.
 */

public class SwipeParser {
    static {
        // Self test
        assert SwipeParser.parsePercent("10", 100, -1) == -1;
        assert SwipeParser.parsePercent("10%", 100, -1) == 10.0;
        assert SwipeParser.parsePercent("100%", 100, -1) == 100.0;
        assert SwipeParser.parsePercent("1.5%", 100, -1) == 1.5;
    }

    public static float parsePercent(String value, float full, float defaultValue)  {
        if (value != null && Pattern.matches("^[0-9\\\\.]+%$", value)) {
            Float v = new Float(value.trim().replace("%", ""));
            return v.floatValue() / 100.0f * full;
        } else {
            return defaultValue;
        }
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
