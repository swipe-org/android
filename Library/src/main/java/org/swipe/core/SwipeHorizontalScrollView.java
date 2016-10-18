package org.swipe.core;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/**
 * Created by pete on 10/18/16.
 */

public class SwipeHorizontalScrollView extends HorizontalScrollView {
    public SwipeHorizontalScrollView(Context context) {
        super(context);
    }

    interface OnOverScrollListener {
        void onOverScrolled(int delta);
    }

    private SwipeHorizontalScrollView.OnOverScrollListener overScrollListener = null;

    void setOverScrollListener(SwipeHorizontalScrollView.OnOverScrollListener listener) {
        overScrollListener = listener;
    }

    private static final String TAG = "SwHScrollView";

    @Override
    public boolean onInterceptTouchEvent (MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent (MotionEvent ev) {
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onOverScrolled (int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (clampedX || clampedY) {
            if (overScrollListener != null) {
                overScrollListener.onOverScrolled(0);
            }
        }

        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }
}
