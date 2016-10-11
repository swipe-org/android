package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeBook implements SwipePage.Delegate {

    public interface Delegate {

    }

    protected SwipeBook.Delegate delegate = null;
    protected JSONObject bookInfo = null;
    protected URL baseURL = null;
    protected List<URL> resourceURLs = null;
    protected List<SwipePage> pages = new ArrayList<>();
    protected ViewPager viewPager = null;
    protected Context context = null;
    protected String paging = "vertical";
    protected boolean viewstate = true;
    protected String orientation = "portrait";

    public View getView() { return viewPager; }
    public boolean viewstate() { return viewstate; }
    public boolean horizontal() { return paging.equals("leftToRight"); }
    public String orientation() { return orientation; }

    protected Context getContext() { return context; }



    public SwipeBook(Context _context, float scrWidth, float scrHeight, JSONObject document, URL url, SwipeBook.Delegate _delegate) {
        context = _context;
        bookInfo = document;
        baseURL = url;
        delegate = _delegate;
        viewstate =  bookInfo.optBoolean("viewstate", true);
        paging = bookInfo.optString("paging", paging);
        orientation = bookInfo.optString("orientation", orientation);

        JSONArray pageInfos = bookInfo.optJSONArray("pages");
        if (pageInfos != null) {
            for (int i = 0; i < pageInfos.length(); i++) {
                SwipePage page = new SwipePage(getContext(), new CGSize(scrWidth, scrHeight), i, pageInfos.optJSONObject(i), this);
                pages.add(page);
            }
        }

        if (horizontal()) {
            viewPager = new SwipeHorizontalPager(getContext());
        } else {
            viewPager = new SwipeVerticalPager(getContext());
        }

        class SwipePagerAdapter extends PagerAdapter {

            @Override
            public int getCount() {
                return pages.size();
            }

            @Override
            public Object instantiateItem(ViewGroup collection, int position) {
                View view = pages.get(position).getView();
                collection.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup collection, int position, Object view) {
                collection.removeView((View) view);
            }


            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }
        }

        viewPager.setAdapter(new SwipePagerAdapter());

        String bcString = bookInfo.optString("bc", "black");
        int bc = Color.parseColor(bcString);
        viewPager.setBackgroundColor(bc);
    }

    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();

            for (SwipePage page : pages) {
                List<URL> urls = page.getResourceURLs();
                if (urls != null) {
                    resourceURLs.addAll(urls);
                }
            }
        }

        return resourceURLs;
    }

    @Override
    public SwipePageTemplate pageTemplateWith(String name) {
        // TODO
        return null;
    }

    @Override
    public URL baseURL() {
        return baseURL;
    }
}
