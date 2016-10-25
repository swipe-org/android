package org.swipe.core;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pete on 10/19/16.
 */

public class SwipePath {
    private static final String TAG = "SwPath";
    private static final Pattern patternSVG = Pattern.compile("[a-z][0-9\\-\\.,\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternNUM = Pattern.compile("[\\-]*[0-9\\.]+");

    static Path parse(String string, float w, float h, CGSize scale, DisplayMetrics dm) {
        if (string == null || string.isEmpty()) {
            return null;
        }

        Path path = new Path();

        if (string.equals("ellipse")) {
            path.addOval(0, 0, w * scale.width, h * scale.height, Path.Direction.CW);
            return path;
        }

        Matcher matcher = patternSVG.matcher(string);
        PointF pt = new PointF(0, 0);
        PointF cp = new PointF(0, 0); // last control point for S command
        boolean end = false;

        while (matcher.find() && !end) {
            String group = matcher.group();
            String cmd = group.substring(0, 1);
            String params = group.substring(1);
            //Log.d(TAG, "found " + cmd + " " + params + " at " + matcher.start() + "-" + matcher.end());

            Matcher nums = patternNUM.matcher(params);
            List<Float> p = new ArrayList<>();
            while (nums.find()) {
                String num = nums.group();
                Float fnum = 0.0f;
                try {
                    fnum = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Float.parseFloat(num), dm);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "num " + num + " exception " + e);
                }
                p.add(fnum);
            }

            switch(cmd) {
                case "m":
                    if (p.size() == 2) {
                        path.moveTo(pt.x+p.get(0), pt.y+p.get(1));
                        pt.x += p.get(0);
                        pt.y += p.get(1);
                    }
                    break;
                case "M":
                    if (p.size() == 2) {
                        path.moveTo(p.get(0), p.get(1));
                        pt.x = p.get(0);
                        pt.y = p.get(1);
                    }
                    break;
                case "z":
                case "Z":
                    path.close();
                    end = true;
                    break;
                case "c": {
                    int i = 0;
                    while ((p.size() >= i + 6)) {
                        path.cubicTo(pt.x + p.get(i), pt.y + p.get(i + 1), pt.x + p.get(i + 2), pt.y + p.get(i + 3), pt.x + p.get(i + 4), pt.y + p.get(i + 5));
                        cp.x = pt.x + p.get(i + 2);
                        cp.y = pt.y + p.get(i + 3);
                        pt.x += p.get(i + 4);
                        pt.y += p.get(i + 5);
                        i += 6;
                    }
                    break;
                }
                case "C": {
                    int i = 0;
                    while ((p.size() >= i + 6)) {
                        path.cubicTo(p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3), p.get(i + 4), p.get(i + 5));
                        cp.x = p.get(i + 2);
                        cp.y = p.get(i + 3);
                        pt.x = p.get(i + 4);
                        pt.y = p.get(i + 5);
                        i += 6;
                    }
                    break;
                }
                case "q": {
                    int i = 0;
                    while ((p.size() >= i + 4)) {
                        path.quadTo(pt.x + p.get(i), pt.y + p.get(i + 1), pt.x + p.get(i + 2), pt.y + p.get(i + 3));
                        cp.x = pt.x + p.get(i);
                        cp.y = pt.y + p.get(i + 1);
                        pt.x += p.get(i + 2);
                        pt.y += p.get(i + 3);
                        i += 4;
                    }
                    break;
                }
                case "Q": {
                    int i = 0;
                    while ((p.size() >= i + 4)) {
                        path.quadTo(p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3));
                        cp.x = p.get(i);
                        cp.y = p.get(i + 1);
                        pt.x = p.get(i + 2);
                        pt.y = p.get(i + 3);
                        i += 4;
                    }
                    break;
                }
                case "s": {
                    int i = 0;
                    while ((p.size() >= i + 4)) {
                        path.cubicTo(pt.x * 2 - cp.x, pt.y * 2 - cp.y, pt.x + p.get(i), pt.y + p.get(i + 1), pt.x + p.get(i + 2), pt.y + p.get(i + 3));
                        cp.x = pt.x + p.get(i);
                        cp.y = pt.y + p.get(i + 1);
                        pt.x += p.get(i + 2);
                        pt.y += p.get(i + 3);
                        i += 4;
                    }
                    break;
                }
                case "S": {
                    int i = 0;
                    while((p.size() >= i+4)) {
                        path.cubicTo(pt.x * 2 - cp.x, pt.y * 2 - cp.y, p.get(i), p.get(i+1), p.get(i+2), p.get(i+3));
                        cp.x = p.get(i);
                        cp.y = p.get(i+1);
                        pt.x = p.get(i+2);
                        pt.y = p.get(i+3);
                        i += 4;
                    }
                    break;
                }
                case "l": {
                    int i = 0;
                    while((p.size() >= i+2)) {
                        path.lineTo(pt.x+p.get(i), pt.y+p.get(i+1));
                        pt.x += p.get(i);
                        pt.y += p.get(i+1);
                        i += 2;
                    }
                    break;
                }
                case "L": {
                    int i = 0;
                    while((p.size() >= i+2)) {
                        path.lineTo(p.get(i), p.get(i+1));
                        pt.x = p.get(i);
                        pt.y = p.get(i+1);
                        i += 2;
                    }
                    break;
                }
                case "v": {
                    int i = 0;
                    while((p.size() >= i+1)) {
                        path.lineTo(pt.x, pt.y+p.get(i));
                        pt.y += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "V": {
                    int i = 0;
                    while((p.size() >= i+1)) {
                        path.lineTo(pt.x, p.get(i));
                        pt.y = p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "h": {
                    int i = 0;
                    while((p.size() >= i+1)) {
                        path.lineTo(pt.x+p.get(i), pt.y);
                        pt.x += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "H": {
                    int i = 0;
                    while((p.size() >= i+1)) {
                        path.lineTo(p.get(i), pt.y);
                        pt.x = p.get(i);
                        i += 1;
                    }
                    break;
                }
                default:
                    Log.w(TAG, "### unknown " + cmd);
                    break;
            }
        }
        Matrix xform = new Matrix();
        xform.setScale(scale.width, scale.height);
        path.transform(xform);
        return path;
    }
}
