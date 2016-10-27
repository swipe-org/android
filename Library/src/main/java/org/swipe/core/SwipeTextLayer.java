package org.swipe.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import static android.R.attr.defaultValue;
import static android.R.attr.shadowRadius;

/**
 * Created by pete on 10/27/16.
 */

public class SwipeTextLayer extends TextView {
    private static final String TAG = "SwTextLayer";
    private boolean fTextBottom = false;
    private boolean fTextTop = false;
    private DisplayMetrics dm = null;

    public SwipeTextLayer(Context context) {
        super(context);
        dm = getContext().getResources().getDisplayMetrics();
    }

    private float px2Dip(float px) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, dm);
    }

    private void parseAlignment(String alignment) {
        switch(alignment) {
            case "center":
                setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                setGravity(getGravity() | Gravity.CENTER);
                break;
            case "left":
                setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                setGravity(getGravity() | Gravity.START);
                break;
            case "right":
                setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                setGravity(getGravity() | Gravity.END);
                break;
            case "justified":
                setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
                setGravity(getGravity() | Gravity.CENTER);
                break;
            case "top":
                setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                setGravity(getGravity() | Gravity.TOP);
                fTextTop = true;
                break;
            case "bottom":
                setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                setGravity(getGravity() | Gravity.BOTTOM);
                fTextBottom = true;
                break;
        }
    }

    private void parseTextInfo(JSONObject info, CGSize scale, CGSize dimension) {
        String alignment = info.optString("textAlign", null);
        if (alignment != null){
            parseAlignment(alignment);
        } else {
            JSONArray alignments = info.optJSONArray("textAlign");
            if (alignments != null) {
                for (int i = 0; i < alignments.length(); i++) {
                    alignment = alignments.optString(i, null);
                    if (alignment != null) {
                        parseAlignment(alignment);
                    }
                }
            }
        }

        float defaultSize = 20.0f / 480.0f * dimension.height;
        float size = SwipeParser.parseFontSize(info, dimension.height, defaultSize, /*markdown*/ false);
        float fontSize =  Math.round(size * scale.height);

        Typeface tf = null;
        List<String> fontNames = SwipeParser.parseFontName(info, /*markdown*/ false);
        for (String name : fontNames) {
            tf = Typeface.create(name, Typeface.NORMAL);
            Log.d(TAG, "typeFace " + tf.toString());
        }
        if (tf == null) {
            tf = Typeface.create("Helvetica", Typeface.NORMAL);
        }
        setTypeface(tf);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
    }

    public void updateTextLayer(String text, JSONObject info, CGSize scale, CGSize dimension) {
        setText(text);
        parseTextInfo(info, scale, dimension);
        setTextColor(SwipeParser.parseColor(info.optString("textColor"), Color.BLACK));

        JSONObject shadowInfo = info.optJSONObject("shadow");
        if (shadowInfo != null){
            CGSize shadowOffset = SwipeParser.parseSize(shadowInfo.opt("offset"), new CGSize(1, 1), scale);
            float shadowRadius = SwipeParser.parseFloat(shadowInfo.optDouble("radius"), 1) * scale.width;
            float shadowOpacity = SwipeParser.parseFloat(shadowInfo.optDouble("opacity"), 0.5f);
            int shadowColor = SwipeParser.parseColor(shadowInfo.opt("color"), Color.BLACK);
            shadowColor = Color.argb((int)(Color.alpha(shadowColor) * shadowOpacity), Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor));
            setShadowLayer(px2Dip(shadowRadius), px2Dip(shadowOffset.width), px2Dip(shadowOffset.height), shadowColor);
        }
    }

    public static SwipeTextLayer parse(Context context, String text, JSONObject info, CGSize scale, CGSize dimension) {
        SwipeTextLayer textLayer = new SwipeTextLayer(context);
        int gravity = textLayer.getGravity();
        textLayer.setGravity(Gravity.CENTER);
        textLayer.updateTextLayer(text, info, scale, dimension);
        return textLayer;
    }

}
