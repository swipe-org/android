package org.swipe.browser;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;

import org.json.JSONObject;
import org.swipe.core.SwipeBook;
import org.swipe.network.SwipePrefetcher;

import java.net.URL;
import java.util.List;

/**
 * Created by pete on 9/12/16.
 */
public class SwipeBookBrowserView extends SwipeBrowserView implements SwipeBook.Delegate, SwipePrefetcher.Listener {
    private final static String TAG = "SwBookBrowser";

    protected SwipeBook book = null;

    public SwipeBookBrowserView(Activity context) {
        super(context);
    }

    @Override
    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = book.getResourceURLs();
        }

        return resourceURLs;
    }

    @Override
    public void loadDocument(JSONObject _document, URL url) {
        super.loadDocument(_document, url);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float fwidth = dm.widthPixels / dm.density;
        float fheight = dm.heightPixels / dm.density;
        if (landscape() && fwidth < fheight || !landscape() && fwidth > fheight) {
            float temp = fwidth;
            fwidth = fheight;
            fheight = temp;
        }
        Log.d(TAG, "display w=" + fwidth + " h=" + fheight);

        book = new SwipeBook(getContext(), fwidth, fheight, document, baseURL, this);

        /* TODO
        if let languages = self.book.languages(),
                language = languages.first,
                langId = language["id"] as? String {
            self.book.langId = langId
        }
        */

        if (book.viewstate()) {
            /* TODO
            if let pageIndex = state?["page"] as? Int where pageIndex < self.book.pages.count {
                self.book.pageIndex = pageIndex
            }
            if let langId = state?["langId"] as? String {
                self.book.langId = langId
            }
            */
        }

        prefetcher = new SwipePrefetcher();
        prefetcher.get(getResourceURLs(), this);
    }

    @Override
    public void didComplete(SwipePrefetcher prefetcher) {
        ;
        addView(book.loadView(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void progress(SwipePrefetcher prefetcher) {
        // TODO
    }
}
