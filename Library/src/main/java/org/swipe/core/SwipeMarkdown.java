package org.swipe.core;

import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static android.content.ContentValues.TAG;

/**
* Created by pete on 11/1/16.
*/

class SwipeMarkdown {
    private static final String TAG = "SwMarkdown";

    public class Element implements Cloneable {
        public String text = null;
        public int textColor = Color.BLACK;
        public int textAlignment = View.TEXT_ALIGNMENT_CENTER;
        public int lineSpacing = 1;
        public String fontName = "Helvetica";
        public int fontSize = 0;
        public CGSize scale = null;
        public CGSize shadowOffset = null;
        public float shadowRadius = 0;
        public int shadowColor = Color.BLACK;

        public Element clone()  {
            try {
                return (Element) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private Map<String, Element> attrs = new HashMap<>();
    private Map<String, String> prefixes =  new HashMap<>();
    {
        prefixes.put("-","\u2022 "); // bullet (U+2022); http://graphemica.com/%E2%80%A2
        prefixes.put("```", " ");
    }

    private CGSize scale = null;
    private CGSize shadowOffset = null;
    private float shadowRadius = 0;
    private int shadowColor = Color.BLACK;
    private DisplayMetrics dm = null;

    private float px2Dip(float px) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px, dm);
    }

    private Element attributesWith(float fontSize, float paragraphSpacing, String fontName) {
        Element attr = new Element();
        //style.lineBreakMode = NSLineBreakMode.ByWordWrapping
        attr.lineSpacing = (int) px2Dip(paragraphSpacing * scale.height);
        attr.fontSize = (int) (fontSize * scale.height);
        if (fontName != null) {
            attr.fontName = fontName;
        }
        if (shadowOffset != null) {
            attr.shadowOffset = new CGSize(px2Dip(shadowOffset.width * scale.width), px2Dip(shadowOffset.height * scale.height));
            attr.shadowRadius = px2Dip(shadowRadius * scale.width);
            attr.shadowColor = shadowColor;
        }

        return attr;
    }
    
    public SwipeMarkdown(JSONObject info, float scale, CGSize dimension, DisplayMetrics dm) {
        this.scale = new CGSize(scale, scale);
        this.dm = dm;

        if (info != null) {
            JSONObject shadowInfo = info.optJSONObject("shadow");
            if (shadowInfo != null) {
                shadowOffset = SwipeParser.parseSize(shadowInfo.opt("offset"), new CGSize(1, 1), this.scale);
                shadowRadius = SwipeParser.parseFloat(shadowInfo.optDouble("radius"), 2) * this.scale.width;
                float shadowOpacity = SwipeParser.parseFloat(shadowInfo.optDouble("opacity"), 0.5f);
                shadowColor = SwipeParser.parseColor(shadowInfo.opt("color"), Color.BLACK);
                shadowColor = Color.argb((int) (Color.alpha(shadowColor) * shadowOpacity), Color.red(shadowColor), Color.green(shadowColor), Color.blue(shadowColor));
            }
        }

        attrs.put("#", attributesWith(32, 16, null));
        attrs.put("##", attributesWith(28, 14, null));
        attrs.put("###", attributesWith(24, 12, null));
        attrs.put("####", attributesWith(22, 11, null));
        attrs.put("*", attributesWith(20, 10, null));
        attrs.put("-", attributesWith(20, 5, null));
        attrs.put("```", attributesWith(14, 0, "Courier"));
        attrs.put("```+", attributesWith(7, 0, "Courier"));

        if (info == null) {
            return;
        }

        JSONObject styles = info.optJSONObject("styles");
        if (styles == null) {
            return;
        }

        Iterator<String> keyMarks = styles.keys();
        while (keyMarks.hasNext()) {
            String keyMark = keyMarks.next();
            JSONObject attrInfo = styles.optJSONObject(keyMark);
            if (attrInfo == null) {
                continue;
            }

            Element attrCopy = attrs.get(keyMark);
            if (attrCopy != null) {
                attrCopy =  attrCopy.clone();
            } else {
                attrCopy = attributesWith(20, 10, null);
            }

            Iterator<String> keyAttrs = attrInfo.keys();
            while (keyAttrs.hasNext()){
                String keyAttr = keyAttrs.next();
                Object attrValue = attrInfo.opt(keyAttr);
                switch (keyAttr) {
                    case "color":
                        attrCopy.textColor = SwipeParser.parseColor(attrValue);
                        break;
                    case "font": {
                        if (attrValue instanceof JSONObject) {
                            JSONObject fontInfo = (JSONObject)attrValue;
                            float fontSize = SwipeParser.parseFontSize(fontInfo, dimension.height, Float.NaN, /*markdown*/ true);
                            if (!Float.isNaN(fontSize)) {
                                attrCopy.fontSize = (int)(fontSize * this.scale.height);
                            }
                            List<String> names = SwipeParser.parseFontName(fontInfo, /*markdown*/ true);
                            if (names.size() > 0) {
                                attrCopy.fontName = names.get(0);
                            }
                        }
                        break;
                    }
                    case "prefix":
                        if (attrValue instanceof String) {
                            prefixes.put(keyMark, (String)attrValue);
                        }
                        break;
                    case "alignment": {
                        if (attrValue instanceof String) {
                            switch ((String) attrValue) {
                                case "center":
                                    attrCopy.textAlignment = Gravity.CENTER;
                                    break;
                                case "right":
                                    attrCopy.textAlignment = Gravity.END;
                                    break;
                                case "left":
                                    attrCopy.textAlignment = Gravity.START;
                                    break;
                            }
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            attrs.put(keyMark, attrCopy);
        }
    }

    List<Element> parse(Object markdownsObj) {
        List<Element> elements = new ArrayList<>();

        if (markdownsObj == null) {
            return elements;
        }

        JSONArray markdowns;

        if (markdownsObj instanceof String) {
            markdowns = new JSONArray();
            markdowns.put(markdownsObj);
        } else if (markdownsObj instanceof JSONArray) {
            markdowns = (JSONArray) markdownsObj;
        } else {
            return elements;
        }

        boolean fCode = false;

        for (int index = 0; index < markdowns.length(); index++) {
            String markdown = markdowns.optString(index);
            if (markdown == null) {
                continue;
            }

            String key = "*";
            String body = markdown;

            if (markdown.equals("```")) {
                fCode = !fCode;
                if (fCode) {
                    key = null;
                    body = "";
                } else {
                    key = "```+";
                    body = "";
                }
            } else if (fCode) {
                key = "```";
                body = markdown;
            } else {
                for (String prefix : attrs.keySet()) {
                    if (markdown.indexOf(prefix + " ") == 0) {
                        key = prefix;
                        body = markdown.substring(prefix.length() + 1);
                        break;
                    }
                }
            }

            if (key != null) {
                String prefix = prefixes.get(key);
                if (prefix != null) {
                    body = prefix + body;
                }

                Element element =  attrs.get(key).clone();
                element.text = body;
                elements.add(element);
            }
        }

        return elements;
    }
}

