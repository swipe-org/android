package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
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

public class SwipeView extends SwipeNode {
    private final static String TAG = "SwView";
    protected List<URL> resourceURLs = null;
    protected Context context = null;
    protected ViewGroup viewGroup = null;
    protected Context getContext() { return context; }
    protected CGSize dimension = null;
    protected CGSize scale = null;

    View getView() { return viewGroup; }

    public SwipeView(Context _context, CGSize _dimension, CGSize _scale, JSONObject _info) {
        super(_info);
        context = _context;
        dimension = _dimension;
        scale = _scale;
    }

    public List<URL> getResourceURLs() {
        return resourceURLs;
    }

    ViewGroup loadView() {
        viewGroup = new ViewGroup(getContext()) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                //Log.d(TAG, "onLayout");

                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    //Log.d(TAG, "layout " + c + " w:" + lp.width + " h:" + lp.height);
                    v.layout(0, 0, lp.width, lp.height);
                }
            }
        };

        String bcString = info.optString("bc", "black");
        int bc = Color.parseColor(bcString);
        viewGroup.setBackgroundColor(bc);
        return viewGroup;
    }
}
