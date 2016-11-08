package org.swipe.core;

import android.animation.ObjectAnimator;
import android.view.animation.LinearInterpolator;

/**
 * Created by pete on 11/7/16.
 *
 * Wraps animations so that we can animate it relative to the overall animation to handle
 * 'timing', 'loop', 'repeat'.  As on iOS, we're going with animations that use a 0.0 to 1.0
 * duration model and not time-based (the natural Android animation model).
 */

public class SwipeObjectAnimator {
    private static final String TAG = "SwObjAni";
    public static final int DURATION_MSEC = 1000;

    private ObjectAnimator ani = null;
    private double start = 0;       // relative to overall animation
    private double duration = 1;
    private boolean ended = false;
    private boolean forward = true;

    public SwipeObjectAnimator(ObjectAnimator ani, double start, double duration) {
        if (start + duration > 1.0) {
            final String msg = "TAG: start:" + start + " + duration:" + duration + " > 1.0):" + start + duration;
            throw new AssertionError(msg);
        }

        this.ani = ani;
        this.start = start;
        this.duration = duration;

        ani.setInterpolator(new LinearInterpolator());
        ani.setDuration((long)(duration * DURATION_MSEC));
    }

    public void reset(boolean fForward) {
        forward = fForward;
        ended = false;
        ani.setCurrentFraction(forward ? 0 : 1);
    }

    public void setCurrentFraction(float overallOffset) {
        if (overallOffset == 0) {
            if (!ended) {
                ani.setCurrentFraction(0);
                if (!forward) {
                    ended = true;
                }
            }
        } else if (overallOffset >= start && overallOffset < start + duration) {
            ani.setCurrentFraction((float)((overallOffset - start) / duration));
        } else if (overallOffset == 1) {
            if (!ended) {
                ani.setCurrentFraction(1);
                if (forward) {
                    ended = true;
                }
            }
        }
    }
}
