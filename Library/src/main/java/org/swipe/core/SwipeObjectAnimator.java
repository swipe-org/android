package org.swipe.core;

import android.animation.ObjectAnimator;
import android.util.Log;
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

    public static class Instrumentation {
        long setFractionCnt = 0;
        long resetCnt = 0;
    }

    private static Instrumentation instrumentation = new Instrumentation();

    public static Instrumentation getInstrumentation() { return instrumentation; }
    public static Instrumentation resetInstrumentation() {
        Instrumentation prev = instrumentation;
        instrumentation = new Instrumentation();
        return prev;
    }

    public static void printInstrumentation() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n");
        builder.append(String.format("INST set:%4d reset:%4d", instrumentation.setFractionCnt, instrumentation.resetCnt));
        builder.append("\n\n");
        SwipeUtil.Log(TAG, builder.toString());
    }

    private void ani_setCurrentFraction(float fraction) {
        instrumentation.setFractionCnt++;
        ani.setCurrentPlayTime((long) (ani.getDuration() * fraction));
    }

    public void reset(boolean fForward) {
        forward = fForward;
        ended = false;
        instrumentation.resetCnt++;
        ani.setCurrentPlayTime(forward ? 0 : ani.getDuration());
    }

    public void setCurrentFraction(float overallOffset) {
        if (overallOffset >= start && overallOffset < start + duration) {
            ani_setCurrentFraction((float) ((overallOffset - start) / duration));
        } else if (overallOffset == 0) {
            if (!ended) {
                ani_setCurrentFraction(0);
                if (!forward) {
                    ended = true;
                }
            }
        } else if (!forward && overallOffset < start) {
            if (!ended) {
                ani_setCurrentFraction(0);
                ended = true;
            }
        } else if (overallOffset == 1) {
            if (!ended) {
                ani_setCurrentFraction(1);
                if (forward) {
                    ended = true;
                }
            }
        } else if (forward && overallOffset >+ start + duration) {
            if (!ended) {
                ani_setCurrentFraction(1);
                ended = true;
            }
        }
    }
}
