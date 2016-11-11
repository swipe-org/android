package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.swipe.browser.R;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

abstract class SwipeView extends SwipeNode {
    private final static String TAG = "SwView";
    protected List<URL> resourceURLs = null;
    protected Context context = null;
    protected ViewGroup viewGroup = null;
    protected Context getContext() { return context; }
    protected CGSize dimension = null;
    protected CGSize scrDimension = null;
    protected CGSize scale = null;
    DisplayMetrics dm = null;

    View getView() { return viewGroup; }

    SwipeView(Context _context, CGSize _dimension, CGSize _scrDimension, CGSize _scale, JSONObject _info, SwipeNode _parent) {
        super(_info, _parent);
        context = _context;
        dimension = _dimension;
        scrDimension = _scrDimension;
        scale = _scale;
        dm = getContext().getResources().getDisplayMetrics();
    }

    public List<URL> getResourceURLs() {
        return resourceURLs;
    }

    abstract void createViewGroup();

    ViewGroup loadView() {
        createViewGroup();
        return viewGroup;
    }

    public int px2Dip(float px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, dm);
    }
}
