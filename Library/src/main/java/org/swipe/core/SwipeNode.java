package org.swipe.core;

import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeNode {
    SwipeNode parent = null;
    JSONObject info = null;
    List<SwipeNode> children = new ArrayList<>();

    void MyLog(String tag, String text) {
        MyLog(tag, text, 0);
    }

    void MyLog(String tag, String text, int level) {
        if (level <= 4) {
            Log.d(tag, text);
        }
    }

    SwipeNode(JSONObject _info, SwipeNode _parent) {
        info = _info;
        parent = _parent;
    }
}
