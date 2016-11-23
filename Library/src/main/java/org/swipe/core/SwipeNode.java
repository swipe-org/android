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

    SwipeNode(JSONObject _info, SwipeNode _parent) {
        info = _info;
        parent = _parent;
    }
}
