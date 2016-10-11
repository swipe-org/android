package org.swipe.core;

import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipePageTemplate {

    public JSONObject pageTemplateInfo = null;

    protected List<URL> resourceURLs = null;

    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();
            // TODO
        }

        return resourceURLs;
    }
}
