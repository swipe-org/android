package org.swipe.core;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * Created by pete on 10/17/16.
 */

public class SwipeScrollView extends ScrollView {
    interface OnOverScrollListener {
        void onOverScrolled(int delta);
    }

    private OnOverScrollListener overScrollListener = null;

    void setOverScrollListener(OnOverScrollListener listener) {
        overScrollListener = listener;
    }

    private static final String TAG = "SwScrollView";

    public SwipeScrollView(Context context) {
        super(context);
    }

    @Override
    public boolean onInterceptTouchEvent (MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }


    private boolean touchable = true;

    void setTouchable(boolean val) {
        touchable = val;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (touchable) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    @Override
    protected void onOverScrolled (int scrollX,
                         int scrollY,
                         boolean clampedX,
                         boolean clampedY) {
        if (clampedX || clampedY) {
            if (overScrollListener != null) {
                overScrollListener.onOverScrolled(0);
            }
        }

        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }
}
