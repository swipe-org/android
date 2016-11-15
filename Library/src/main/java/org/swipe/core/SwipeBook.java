package org.swipe.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;

/**
 * Created by pete on 10/5/16.
 */

public class SwipeBook implements SwipePage.Delegate {

    public interface Delegate {
        URL map(URL url);
        URL makeFullURL(String url);
        boolean landscape();
    }

    private static final String TAG = "SwBook";
    private SwipeBook.Delegate delegate = null;
    private JSONObject bookInfo = null;
    private URL baseURL = null;
    private List<URL> resourceURLs = null;
    protected List<SwipePage> pages = new ArrayList<>();
    private ViewGroup scrollView = null;
    private ViewGroup pagesView = null;
    private ViewGroup viewGroup = null;
    protected Context context = null;
    private String paging = "vertical";
    private boolean viewstate = true;
    private boolean horizontal = false;
    private boolean fAdvancing = true;
    public int viewWidthDIP = 0;
    public int viewHeightDIP = 0;
    public CGSize dimension = new CGSize(320, 568);
    private String orientation = "portrait";
    public float scale = 1;
    private int pageIndex = 0;
    private Map<String, SwipePageTemplate> templatePages = new HashMap<>();
    private JSONObject templateElements = null;
    private JSONObject templates = null;
    private SwipeMarkdown markdown = null;

    public View getView() { return viewGroup; }
    public boolean viewstate() { return viewstate; }
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
    private boolean canSmoothScroll = true;

    public SwipeBook(Context _context, float scrWidth, float scrHeight, JSONObject document, URL url, SwipeBook.Delegate _delegate) {
        context = _context;
        bookInfo = document;
        baseURL = url;
        delegate = _delegate;
        viewstate =  bookInfo.optBoolean("viewstate", true);
        paging = bookInfo.optString("paging", paging);
        horizontal = paging.equals("leftToRight");
        orientation = bookInfo.optString("orientation", orientation);
        templates = bookInfo.optJSONObject("templates");
        JSONObject pageTemplates = null;
        if (templates != null) {
            templateElements = templates.optJSONObject("elements");
            pageTemplates = templates.optJSONObject("pages");
        }

        if (templateElements == null && bookInfo.has("elements")) {
            MyLog(TAG, "DEPRECATED named elements; use 'templates'");
            templateElements = bookInfo.optJSONObject("elements");
        }

        if (pageTemplates == null && bookInfo.has("scenes")) {
            MyLog(TAG, "DEPRECATED scenes; use 'templates'");
            pageTemplates = bookInfo.optJSONObject("scenes");
        }

        if (pageTemplates != null) {
            Iterator<String> it = pageTemplates.keys();
            while (it.hasNext()) {
                String key = it.next();
                templatePages.put(key, new SwipePageTemplate(pageTemplates.optJSONObject(key), this));
            }
        }

        JSONArray dimensionA = bookInfo.optJSONArray("dimension");
        if (dimensionA != null && dimensionA.length() == 2) {
            int dim0 = dimensionA.optInt(0);
            int dim1 = dimensionA.optInt(1);
            if (dim0 == 0) {
                dimension = new CGSize(dim1 / scrHeight * scrWidth, dim1);
            } else if (dim1 == 0) {
                dimension = new CGSize(dim0, dim0 / scrWidth * scrHeight);
            } else {
                dimension = new CGSize(dim0, dim1);
            }
        } else if (delegate.landscape()) {
            dimension = new CGSize(568, 320);
        } else {
            dimension = new CGSize(320, 568);
        }

        if (dimension.height > dimension.width) {
            scale = scrHeight / dimension.height;
        } else {
            scale = scrWidth / dimension.width;
        }

        final float ratioView = scrHeight / scrWidth;
        final float ratioBook = dimension.height / dimension.width;
        if (ratioBook > ratioView) {
            scrWidth = scrHeight / ratioBook;
        } else {
            scrHeight = scrWidth * ratioBook;
        }
        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        viewWidthDIP = (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrWidth, dm));
        viewHeightDIP = (int)(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrHeight, dm));

        markdown = new SwipeMarkdown(bookInfo.optJSONObject("markdown"), scale, dimension, dm);

        JSONArray pageInfos = bookInfo.optJSONArray("pages");
        if (pageInfos != null) {
            for (int i = 0; i < pageInfos.length(); i++) {
                SwipePage page = new SwipePage(getContext(), dimension, dimension, new CGSize(scale, scale), i, pageInfos.optJSONObject(i), this);
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

    private float scrollOffset() {
        if (horizontal) {
            return scrollView.getScrollX();
        } else {
            return scrollView.getScrollY();
        }
    }

    private void syncScrollOffset() {
        if (horizontal) {
            pagesView.setX(-scrollView.getScrollX());
        } else {
            pagesView.setY(-scrollView.getScrollY());
        }
    }

    private float scrollPos() {
        if (horizontal) {
            HorizontalScrollView sv = (HorizontalScrollView)scrollView;
            return pagePosition(sv.getScrollX(), sv.getScrollY());
        } else {
            ScrollView sv = (ScrollView)scrollView;
            return pagePosition(sv.getScrollX(), sv.getScrollY());
        }
    }

    private int scrollIndex() {
        return (int)(scrollPos() + 0.5);
    }

    private float pagePosition(float offsetX, float offsetY) {
        return horizontal ? offsetX / viewWidthDIP : offsetY / viewHeightDIP;
    }

    private void onMove() {
        syncScrollOffset();

        final float pos = scrollPos(); // int part is page number, fraction is offset into page (0.0 - 0.999)
        final int index = (int)pos;
        if (index >= 0) {
            final SwipePage pagePrev = pages.get(index);
            final View prevView = pagePrev.getView();
            if (pagePrev.fixed) {
                if (horizontal) {
                    prevView.setX(scrollOffset());
                } else {
                    prevView.setY(scrollOffset());
                }
            }
        }
        if (index + 1 <pages.size()) {
            final SwipePage pageNext = pages.get(index + 1);
            final float offset = pos - index; // offset into page (0.0 - 0.999)
            pageNext.setTimeOffsetWhileDragging(offset);
            if (pageNext.fixed) {
                final View viewNext = pageNext.getView();
                if (offset > 0.0 && pageNext.replace) {
                    viewNext.setAlpha(1);
                }else{
                    viewNext.setAlpha(offset);
                }
                if (horizontal) {
                    viewNext.setX(scrollOffset());
                } else {
                    viewNext.setY(scrollOffset());
                }
            }
        }
        if (index + 2 < pages.size()) {
            final SwipePage pageNext2 = pages.get(index + 2);
            if (pageNext2.fixed) {
                pageNext2.getView().setAlpha(0);
            }
        }
    }

    private void endMove(final int index) {
        for (int i = index - 1; i < index + 2; i++) {
            if (i >= 0 && i < pages.size()) {
                final SwipePage page = pages.get(i);
                if (page.fixed) {
                    final View view = page.getView();
                    view.setAlpha(1);
                    if (horizontal) {
                        view.setX(i * viewWidthDIP);
                    } else {
                        view.setY(i * viewHeightDIP);
                    }
                }
            }
        }

        if (horizontal) {
            scrollView.scrollTo(index * viewWidthDIP, 0);
        } else {
            scrollView.scrollTo(0, index * viewHeightDIP);
        }
    }

    private void smoothScrollTo(final int position, final int toOffset) {
        final int ANI_FRAME_MSEC = 16; // 60fps
        final int ANI_MSEC = 100;
        final int ANI_FRAMES = ANI_MSEC / ANI_FRAME_MSEC;
        canSmoothScroll = true;

        scrollView.postDelayed(new Runnable() {

            @Override
            public void run() {
                boolean finished = false;

                if (horizontal) {
                    SwipeHorizontalScrollView sv = (SwipeHorizontalScrollView) scrollView;
                    final int deltaOffset = (toOffset - sv.getScrollX()) / ANI_FRAMES;
                    if (Math.abs(deltaOffset) == 0) {
                        finished = true;
                    } else {
                        sv.scrollBy(deltaOffset, 0);
                    }
                } else {
                    SwipeScrollView sv = (SwipeScrollView) scrollView;
                    final int deltaOffset = (toOffset - sv.getScrollY()) / ANI_FRAMES;
                    if (Math.abs(deltaOffset) == 0) {
                        finished = true;
                    } else {
                        sv.scrollBy(0, deltaOffset);
                    }
                }

                if (!finished && canSmoothScroll) {
                    smoothScrollTo(position, toOffset);
                } else {
                    endMove(position);
                    adjustIndex(position);
                }
            }
        }, ANI_FRAME_MSEC);
    }

    public ViewGroup loadView() {
         if (horizontal) {
             SwipeHorizontalScrollView sv = new SwipeHorizontalScrollView(getContext());
             scrollView = sv;
             sv.setOverScrollListener(new SwipeHorizontalScrollView.OnOverScrollListener() {
                @Override
                public void onOverScrolled(int delta) {
                    didOverScroll = true;
                }
             });
        } else {
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

                if (horizontal) {
                    pgSize = viewWidthDIP;
                    distance = (int)Math.abs(e1.getX() - e2.getX());
                } else {
                    pgSize = viewHeightDIP;
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
                boolean handled = false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "onTouch ACTION_DOWN");
                        didOverScroll = false;
                        canSmoothScroll = false;
                        downEvent = MotionEvent.obtain(event);
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        //Log.d(TAG, "onTouch ACTION_MOVE");
                        break;
                    }
                    case MotionEvent.ACTION_UP: {
                        Log.d(TAG, "onTouch ACTION_UP");

                        final int curPage = currentPageIndex();
                        int distance;
                        int pgSize;

                        if (horizontal) {
                            distance = (int) (downEvent.getX() - event.getX());
                            pgSize = viewWidthDIP;
                        } else {
                            distance = (int) (downEvent.getY() - event.getY());
                            pgSize = viewHeightDIP;
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
                        smoothScrollTo(position, offset);
                        prevPosition = position;
                        handled = true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        Log.d(TAG, "onTouch ACTION_CANCEL");
                        break;
                    default:
                        Log.d(TAG, "onTouch other: " + event.getAction() + " " + event.toString());
                        break;
                }

                currentPage().getView().dispatchTouchEvent(event);
                return handled;
            }
        });

        scrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                onMove();
            }
        });

        viewGroup = new ViewGroup(getContext()) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    v.layout(0, 0, lp.width, lp.height);
                }
            }
        };

        pagesView = new ViewGroup(getContext()) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                for (int c = 0; c < this.getChildCount(); c++) {
                    View v = this.getChildAt(c);
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    v.layout(0, 0, lp.width, lp.height);
                }
            }
        };

        for (SwipePage page : pages) {
            ViewGroup pgView = page.loadView();
            if (horizontal) {
                pgView.setX(page.getIndex() * viewWidthDIP);
            } else {
                pgView.setY(page.getIndex() * viewHeightDIP);
            }

            pagesView.addView(pgView, new LinearLayout.LayoutParams(viewWidthDIP, viewHeightDIP));
        }

        ViewGroup.LayoutParams lp;
        if (horizontal) {
            lp = new ViewGroup.LayoutParams(viewWidthDIP * pages.size(), viewHeightDIP);
        } else {
            lp = new ViewGroup.LayoutParams(viewWidthDIP, viewHeightDIP * pages.size());
        }
        viewGroup.addView(pagesView, lp);

        // We use a ScrollView to manage scrolling.  However, since we play some tricks in onMove with setX and setY that don't
        // work properly with the ScrollView, we give it a fake view to manage and put the real content pagesView below the the ScrollView
        // so we can play our tricks in onMove.  All of this is put in an outer ViewGroup.
        View fakeScrollContent = new View(getContext());
        fakeScrollContent.setMinimumWidth(lp.width);
        fakeScrollContent.setMinimumHeight(lp.height);
        scrollView.addView(fakeScrollContent, new ViewGroup.LayoutParams(lp));
        scrollView.measure(View.MeasureSpec.makeMeasureSpec(viewWidthDIP, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(viewHeightDIP, View.MeasureSpec.EXACTLY));
        viewGroup.addView(scrollView, new ViewGroup.LayoutParams(viewWidthDIP, viewHeightDIP));
        viewGroup.setBackgroundColor(SwipeParser.parseColor(bookInfo, "bc", Color.BLACK));

        adjustIndex(pageIndex, true);

        return viewGroup;
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

        SwipePage pagePrev = currentPage();

        if (!fForced) {
            pagePrev.didLeave(newPageIndex < pageIndex);
        }

        pageIndex = newPageIndex;
        pagePrev.prepare();
        currentPage().prepare();
        setActivePage(currentPage());
        MyLog(TAG, "adjustIndex " + pageIndex, 2);

        if (fForced) {
            currentPage().willEnter(true);
        }

        currentPage().didEnter(fAdvancing || fForced);

        return true;
    }

    private SwipePageTemplate pageTemplateActive = null;

    private void setActivePage(SwipePage page) {

        if (pageTemplateActive != page.getPageTemplate()) {
            MyLog(TAG, "setActive " + pageTemplateActive + " " + page.getPageTemplate(), 1);
            if (pageTemplateActive != null) {
                pageTemplateActive.didLeave();
            }
            if (page.getPageTemplate() != null) {
                page.getPageTemplate().didEnter(this);
            }
            pageTemplateActive = page.getPageTemplate();
        }
    }


    @Override
    public SwipePageTemplate pageTemplateWith(String name) {
        String key = name == null || name.isEmpty() ? "*" : name;
        if (key == null) {
            return null;
        }

        return templatePages.get(key);
    }

    @Override
    public JSONObject prototypeWith(String name) {
        if (name == null || templateElements == null) {
            return null;
        }

        return templateElements.optJSONObject(name);
    }

    @Override
    public List<SwipeMarkdown.Element> parseMarkdown(Object markdowns) {
        return markdown.parse(markdowns);
    }

    @Override
    public URL baseURL() {
        return baseURL;
    }

    @Override
    public URL makeFullURL(String url) { return delegate.makeFullURL(url); }

    @Override
    public URL map(URL url) { return delegate.map(url); }

    @Override
    public int currentPageIndex() { return pageIndex; }
}
