package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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

    private static final String TAG = "SwBook";
    private SwipeBook.Delegate delegate = null;
    private JSONObject bookInfo = null;
    private URL baseURL = null;
    private List<URL> resourceURLs = null;
    protected List<SwipePage> pages = new ArrayList<>();
    private ViewGroup scrollView = null;
    protected Context context = null;
    private String paging = "vertical";
    private boolean viewstate = true;
    private boolean fAdvancing = true;
    private float scrWidth = 0;
    private float scrHeight = 0;
    private int width = 0;
    private int height = 0;
    private String orientation = "portrait";
    private int pageIndex = 0;

    public View getView() { return scrollView; }
    public boolean viewstate() { return viewstate; }
    public boolean horizontal() { return paging.equals("leftToRight"); }
    public String orientation() { return orientation; }

    private SwipePage currentPage() { return pages.get(pageIndex); }

    protected Context getContext() { return context; }

    private void MyLog(String tag, String text) {
        MyLog(tag, text, 0);
    }

    private void MyLog(String tag, String text, int level) {
        if (level <= 10) {
            Log.d(tag, text);
        }
    }

    private GestureDetector gestureDetector = null;
    private boolean didOverScroll = false;

    public SwipeBook(Context _context, float _scrWidth, float _scrHeight, JSONObject document, URL url, SwipeBook.Delegate _delegate) {
        context = _context;
        scrWidth = _scrWidth;
        scrHeight = _scrHeight;
        // Convert DIP to PX
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        width = (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrWidth, dm));
        height = (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrHeight, dm));
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

    public ViewGroup loadView() {
        LinearLayout ll = new LinearLayout(getContext());

        if (horizontal()) {
            ll.setOrientation(LinearLayout.HORIZONTAL);
            SwipeHorizontalScrollView sv = new SwipeHorizontalScrollView(getContext());
            scrollView = sv;

            sv.setOverScrollListener(new SwipeHorizontalScrollView.OnOverScrollListener() {
                @Override
                public void onOverScrolled(int delta) {
                    didOverScroll = true;
                }
            });
        } else {
            ll.setOrientation(LinearLayout.VERTICAL);
            SwipeScrollView sv = new SwipeScrollView(getContext());
            scrollView = sv;

            sv.setOverScrollListener(new SwipeScrollView.OnOverScrollListener() {
                @Override
                public void onOverScrolled(int delta) {
                    didOverScroll = true;
                }
            });
        }

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Determine if we consider this a 'fling'
                int pgSize;
                int distance;

                if (horizontal()) {
                    pgSize = width;
                    distance = (int)Math.abs(e1.getX() - e2.getX());
                } else {
                    pgSize = height;
                    distance = (int)Math.abs(e1.getY() - e2.getY());
                }

                return (Math.abs(velocityY) > 500 && distance > 50);
            }
        });

        scrollView.setOnTouchListener(new View.OnTouchListener() {

            MotionEvent downEvent = null;
            int prevPosition = -1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean didFling = gestureDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "onTouch ACTION_DOWN");
                        didOverScroll = false;
                        downEvent = MotionEvent.obtain(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        Log.d(TAG, "onTouch ACTION_MOVE");
                        break;
                    case MotionEvent.ACTION_UP: {
                        Log.d(TAG, "onTouch ACTION_UP");

                        final int curPage = currentPageIndex();
                        int distance;
                        int pgSize;

                        if (horizontal()) {
                            distance = (int) (downEvent.getX() - event.getX());
                            pgSize = width;
                        } else {
                            distance = (int) (downEvent.getY() - event.getY());
                            pgSize = height;
                        }

                        int offset = (int) (curPage * pgSize);  // stay on this page by default unless distance dragged is > 50% of page
                        int pgOffset = 0;

                        if (distance > 0 && (distance >= (pgSize / 2) || didFling) && curPage < pages.size() - 1) {
                            // Scroll to next page
                            pgOffset = 1;
                        } else if (distance < 0 && (Math.abs(distance) >= (pgSize / 2) || didFling) && curPage > 0) {
                            // Scroll to prev page
                            pgOffset = -1;
                        }

                        final int position = curPage + pgOffset;

                        if (prevPosition >= 0) {
                            if (position != curPage) {
                                MyLog(TAG, "scrolling from " + prevPosition + " to " + position, 2);
                                final SwipePage prevPage = pages.get(prevPosition);
                                prevPage.willLeave(fAdvancing);
                                final SwipePage page = pages.get(position);
                                fAdvancing = position > prevPosition;
                                page.willEnter(fAdvancing);
                            } else if (position == 0 && didOverScroll && distance < 0 && Math.abs(distance) >= (pgSize / 8)) {
                                final SwipePage page = pages.get(position);
                                MyLog(TAG, "overscrolling detected", 2);
                                page.willLeave(false);
                                page.willEnter(true);
                                page.didEnter(true);
                            }
                        }

                        offset += pgOffset * pgSize;

                        if (horizontal()) {
                            SwipeHorizontalScrollView sv = (SwipeHorizontalScrollView) scrollView;
                            sv.smoothScrollTo(offset, 0);
                        } else {
                            SwipeScrollView sv = (SwipeScrollView) scrollView;
                            sv.smoothScrollTo(0, offset);
                        }

                        // Delay update of position until after scrolling is finished.
                        // TODO:  Need a way to detect that scrolling is finished
                        scrollView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                adjustIndex(position);
                            }
                        }, 200);
                        prevPosition = position;
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        Log.d(TAG, "onTouch ACTION_CANCEL");
                        break;
                    default:
                        Log.d(TAG, "onTouch other: " + event.getAction() + " " + event.toString());
                        break;
                }

                return false;
            }
        });

        for (SwipePage page : pages) {
            ll.addView(page.loadView(), new ViewGroup.LayoutParams(width, height));
        }

        scrollView.addView(ll, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setBackgroundColor(Color.parseColor(bookInfo.optString("bc", "black")));

        adjustIndex(pageIndex, true);

        return scrollView;
    }

    private boolean adjustIndex(int newPageIndex) {
        return adjustIndex(newPageIndex, false);
    }

    private boolean adjustIndex(int newPageIndex, boolean fForced) {
        if (pages.size() == 0) {
            Log.d(TAG, "adjustIndex ### No Pages");
            return false;
        }

        if (newPageIndex == pageIndex && !fForced) {
            return false;
        }

        if (!fForced) {
            SwipePage pagePrev = currentPage();
            pagePrev.didLeave(newPageIndex < pageIndex);
        }

        pageIndex = newPageIndex;
        currentPage().prepare();
        setActivePage(currentPage());
        MyLog(TAG, "adjustIndex " + pageIndex);

        if (fForced) {
            currentPage().willEnter(true);
        }

        currentPage().didEnter(fAdvancing || fForced);

        return true;
    }

    private void setActivePage(SwipePage page) {
        /* TODO
        if self.pageTemplateActive != page.pageTemplate {
            MyLog("SwipeBook setActive \(self.pageTemplateActive), \(page.pageTemplate)", level:1)
            if let pageTemplate = self.pageTemplateActive {
                pageTemplate.didLeave()
            }
            if let pageTemplate = page.pageTemplate {
                pageTemplate.didEnter(page.prefetcher)
            }
            self.pageTemplateActive = page.pageTemplate
        }
        */
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

    @Override
    public int currentPageIndex() { return pageIndex; }
}
