package org.swipe.core;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pete on 10/19/16.
 */

class SwipePath {
    private static final String TAG = "SwPath";
    private static final Pattern patternSVG = Pattern.compile("[a-z][0-9\\-\\.,\\s]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern patternNUM = Pattern.compile("[\\-]*[0-9\\.]+");

    private Path path = null;
    private Path transformedPath = null;
    private int pathCount = 0;
    private Matrix scale = null;
    private Matrix transform = new Matrix();

    private static class SvgCommand {
        String command;
        List <Float> p;

        SvgCommand() {}
    }

    private List<SvgCommand> commands;

    private SwipePath(List<SvgCommand> commands, Matrix scale) {
        this.commands = commands;
        this.scale = scale;
        this.path = make();
    }

    Path getPath() { return this.transformedPath; }
    int getPathCount() { return this.pathCount; }

    Matrix getTransform() { return this.transform; }
    void setTransform(final Matrix transform)  {
        this.transform = transform;
        this.transformedPath = new Path();
        Matrix xform = new Matrix(scale);
        xform.postConcat(this.transform);
        this.path.transform(this.transform, this.transformedPath);
    }

    private Path make() {
        if (commands == null) {
            return null;
        }

        path = new Path();
        PointF pt = new PointF(0, 0);
        PointF cp = new PointF(0, 0); // last control point for S command
        boolean zLast = false;

        for (SvgCommand cmd : commands) {
            List<Float> p = cmd.p;
            zLast = false;

            switch (cmd.command) {
                case "ellipse": {
                    path.addOval(p.get(0), p.get(1), p.get(2), p.get(3), Path.Direction.CW);
                    break;
                }
                case "m":
                    if (p.size() == 2) {
                        path.moveTo(pt.x + p.get(0), pt.y + p.get(1));
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
                    pathCount++;
                    zLast = true;
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
                    while ((p.size() >= i + 4)) {
                        path.cubicTo(pt.x * 2 - cp.x, pt.y * 2 - cp.y, p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3));
                        cp.x = p.get(i);
                        cp.y = p.get(i + 1);
                        pt.x = p.get(i + 2);
                        pt.y = p.get(i + 3);
                        i += 4;
                    }
                    break;
                }
                case "l": {
                    int i = 0;
                    while ((p.size() >= i + 2)) {
                        path.lineTo(pt.x + p.get(i), pt.y + p.get(i + 1));
                        pt.x += p.get(i);
                        pt.y += p.get(i + 1);
                        i += 2;
                    }
                    break;
                }
                case "L": {
                    int i = 0;
                    while ((p.size() >= i + 2)) {
                        path.lineTo(p.get(i), p.get(i + 1));
                        pt.x = p.get(i);
                        pt.y = p.get(i + 1);
                        i += 2;
                    }
                    break;
                }
                case "v": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x, pt.y + p.get(i));
                        pt.y += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "V": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x, p.get(i));
                        pt.y = p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "h": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x + p.get(i), pt.y);
                        pt.x += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "H": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(p.get(i), pt.y);
                        pt.x = p.get(i);
                        i += 1;
                    }
                    break;
                }
                default:
                    Log.w(TAG, "### unknown " + cmd.command);
                    break;
            }
        }

        if (!zLast) {
            pathCount++;
        }
        
        path.transform(scale);
        transformedPath = new Path(path);

        return path;
    }

    static float morph(float fraction, float p1, float p2) {
        return p1 + (fraction * (p2 - p1));
    }
    static  SwipePath morph(float fraction, SwipePath startValue, SwipePath endValue) {
        if (startValue.commands == null || endValue.commands == null ||
                startValue.commands.size() != endValue.commands.size() ||
                startValue.pathCount > 1 || endValue.pathCount > 1
                ) {
            Log.d(TAG, "morph - paths incompatible");
            return null;
        }

        Path path = new Path();
        PointF pt = new PointF(0, 0);
        PointF cp = new PointF(0, 0); // last control point for S command
        List<SvgCommand> commands = new ArrayList<>();

        for (int ic = 0; ic < startValue.commands.size(); ic++) {
            final SvgCommand sc = startValue.commands.get(ic);
            final SvgCommand ec = endValue.commands.get(ic);
            if (!sc.command.equals(ec.command)) {
                Log.d(TAG, "morph - paths commands incompatible");
                return null;
            }
            final String command = sc.command;
            List<Float> p = new ArrayList<>();
            for (int ip = 0; ip < sc.p.size(); ip++) {
                p.add(ip, morph(fraction, sc.p.get(ip), ec.p.get(ip)));
            }

            SvgCommand cmd = new SvgCommand();
            cmd.command = command;
            cmd.p = p;
            commands.add(cmd);

            switch (command) {
                case "ellipse": {
                    path.addOval(p.get(0), p.get(1), p.get(2), p.get(3), Path.Direction.CW);
                    break;
                }
                case "m":
                    if (p.size() == 2) {
                        path.moveTo(pt.x + p.get(0), pt.y + p.get(1));
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
                    while ((p.size() >= i + 4)) {
                        path.cubicTo(pt.x * 2 - cp.x, pt.y * 2 - cp.y, p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3));
                        cp.x = p.get(i);
                        cp.y = p.get(i + 1);
                        pt.x = p.get(i + 2);
                        pt.y = p.get(i + 3);
                        i += 4;
                    }
                    break;
                }
                case "l": {
                    int i = 0;
                    while ((p.size() >= i + 2)) {
                        path.lineTo(pt.x + p.get(i), pt.y + p.get(i + 1));
                        pt.x += p.get(i);
                        pt.y += p.get(i + 1);
                        i += 2;
                    }
                    break;
                }
                case "L": {
                    int i = 0;
                    while ((p.size() >= i + 2)) {
                        path.lineTo(p.get(i), p.get(i + 1));
                        pt.x = p.get(i);
                        pt.y = p.get(i + 1);
                        i += 2;
                    }
                    break;
                }
                case "v": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x, pt.y + p.get(i));
                        pt.y += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "V": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x, p.get(i));
                        pt.y = p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "h": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(pt.x + p.get(i), pt.y);
                        pt.x += p.get(i);
                        i += 1;
                    }
                    break;
                }
                case "H": {
                    int i = 0;
                    while ((p.size() >= i + 1)) {
                        path.lineTo(p.get(i), pt.y);
                        pt.x = p.get(i);
                        i += 1;
                    }
                    break;
                }
                default:
                    Log.w(TAG, "### unknown " + cmd.command);
                    break;
            }
        }

        SwipePath ret = new SwipePath(commands, startValue.scale);
        return ret;
    }

    static SwipePath parse(String string, float w, float h, CGSize scale, DisplayMetrics dm) {
        if (string == null || string.isEmpty()) {
            return null;
        }

        Matrix xform = new Matrix();
        xform.setScale(scale.width, scale.height);

        List<SvgCommand> commands = new ArrayList<>();

        if (string.equals("ellipse")) {
            SvgCommand cmd = new SvgCommand();
            cmd.command = string;
            List<Float> params = new ArrayList<>();
            params.add(0f);
            params.add(0f);
            params.add(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, w, dm));
            params.add(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, h, dm));
            cmd.p = params;
            commands.add(cmd);
            return new SwipePath(commands, xform);
        }

        Matcher matcher = patternSVG.matcher(string);

        while (matcher.find()) {
            String group = matcher.group();
            String svgCmd = group.substring(0, 1);
            String paramsString = group.substring(1);
            //Log.d(TAG, "found " + cmd + " " + params + " at " + matcher.start() + "-" + matcher.end());

            Matcher nums = patternNUM.matcher(paramsString);
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

            SvgCommand cmd = new SvgCommand();
            cmd.command = svgCmd;
            cmd.p = p;
            commands.add(cmd);
        }

        return new SwipePath(commands, xform);
    }

    static Matrix parsePathTransform(final JSONObject info, final float w, final float h)  {
        if (info == null) {
            return null;
        }

        CGSize scale = null;
        Double d = info.optDouble("scale");
        if (!d.isNaN()) {
            float s = d.floatValue();
            scale = new CGSize(s, s);
        } else {
            JSONArray scales = info.optJSONArray("scale");
            if (scales != null &&  scales.length() == 2) {
                Double d0 = scales.optDouble(0);
                Double d1 = scales.optDouble(1);
                if (!d0.isNaN() && !d1.isNaN()) {
                    scale = new CGSize(d0.floatValue(), d1.floatValue());
                }
            }
        }

        if (scale == null) {
            return null;
        }

        Matrix xform = new Matrix();
        xform.postTranslate(-w / 2, -h / 2);
        xform.postScale(scale.width, scale.height);
        xform.postTranslate(w / 2, h / 2);

        return xform;
    }
}
