package net.swipe.browser;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.net.URL;
import java.util.List;

/**
 * Created by pete on 9/12/16.
 */
public class SwipeBookBrowserView extends SwipeBrowserView {

    private final static String TAG = "SViewerFrag";

    public SwipeBookBrowserView(Activity context) {
        super(context);

        TextView titleView = new TextView(getContext());
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        int pixels = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics());
        titleView.setMinHeight(pixels);
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setText(R.string.tbd);
        addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pixels));
    }

    @Override
    public List<URL> getResourceURLs() {
        return resourceURLs;
    }

    @Override
    public void loadDocument(JSONObject _document) {
        super.loadDocument(_document);
    }

}
