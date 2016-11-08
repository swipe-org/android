package org.swipe.core;

import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeNode {

    protected JSONObject info = null;
    protected List<SwipeNode> children = new ArrayList<>();

    protected void MyLog(String tag, String text) {
        MyLog(tag, text, 0);
    }

    protected void MyLog(String tag, String text, int level) {
        if (level <= 4) {
            Log.d(tag, text);
        }
    }

    public SwipeNode(JSONObject _info) {
        info = _info;
    }
}
