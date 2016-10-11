package org.swipe.browser;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.swipe.core.SwipeParser;
import org.swipe.network.SwipeAssetManager;
import org.swipe.network.SwipePrefetcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * Created by pete on 9/12/16.
 */
public class SwipeTableBrowserView extends SwipeBrowserView implements SwipePrefetcher.Listener {
    private final static String TAG = "SwTblBrowser";
    protected LayoutInflater inflater = null;
    protected ArrayList<JSONObject> items = new ArrayList<>();
    protected TextView titleView = null;
    protected ListView listView = null;
    protected int rowHeight = -1;

    public SwipeTableBrowserView(Activity context) {
        super(context);

        inflater = (LayoutInflater)context.getSystemService(LAYOUT_INFLATER_SERVICE);

        setOrientation(VERTICAL);

        titleView = new TextView(getContext());
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        final int pixels = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getResources().getDisplayMetrics());
        titleView.setMinHeight(pixels);
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setText(R.string.tbd);

        addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pixels));

        listView = new ListView(getContext());
        addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    // Returns the list of URLs of required resources for this element (including children)
    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<URL>();

            for (JSONObject item : items) {
                String urlIcon = item.optString("icon");
                if (urlIcon != null) {
                    URL url = delegate.makeFullURL(urlIcon);
                    if (url != null) {
                        resourceURLs.add(url);
                    }
                }
            }
        }
        return resourceURLs;
    }

    @Override
    public void loadDocument(JSONObject _document, URL url) {
        super.loadDocument(_document, url);

        try {
            if (document.has("sections")) {
                JSONArray sectionsArray = (JSONArray) document.get("sections");
                for (int s = 0; s < sectionsArray.length(); s++) {
                    JSONObject section = sectionsArray.getJSONObject(s);

                    if (section.has("items")) {
                        JSONArray itemsArray = (JSONArray) section.get("items");
                        for (int i = 0; i < itemsArray.length(); i++) {
                            if (itemsArray.isNull(i)) {
                                Log.w(TAG, "item at " + i + " is NULL");
                                continue;
                            }
                            JSONObject item = itemsArray.getJSONObject(i);
                            if (item.has("url") && item.has("title")) {
                                //Log.d(TAG, "item  = " + item.optString("title", "NO TITLE") + ", " + item.optString("url", "NO URL"));
                                items.add(item);
                            } else {
                                Log.e(TAG, "item url MISSING");
                            }
                        }
                    }
                }
            } else if (document.has("items")) {
                JSONArray itemsArray = (JSONArray) document.get("items");
                for (int i = 0; i < itemsArray.length(); i++) {
                    if (itemsArray.isNull(i)) {
                        Log.w(TAG, "item at " + i + " is NULL");
                        continue;
                    }
                    JSONObject item = itemsArray.getJSONObject(i);
                    if (item.has("url") && item.has("title")) {
                        //Log.d(TAG, "item  = " + item.optString("title", "NO TITLE") + ", " + item.optString("url", "NO URL"));
                        items.add(item);
                    } else {
                        Log.e(TAG, "item url MISSING");
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "loadDocument exception " + e);
        }

        prefetcher = new SwipePrefetcher();
        prefetcher.get(getResourceURLs(), this);
    }

    // SwipePrefetcher.Listener

    @Override
    public void didComplete(final SwipePrefetcher prefetcher) {
        Log.d(TAG, "prefetcher.didComplete");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "items.count " + items.size());
                DisplayMetrics dm = getResources().getDisplayMetrics();
                float fwidth = dm.density * dm.widthPixels;
                float fheight = dm.density * dm.heightPixels;
                Log.d(TAG, "display w=" + fwidth + " h=" + fheight);

                rowHeight = (int) document.optDouble("rowHeight", -1);
                if (rowHeight == -1) {
                    String rowHeightStr = document.optString("rowHeight");
                    if (rowHeightStr != null) {
                        rowHeight = (int) SwipeParser.parsePercent(rowHeightStr, landscape() ? fwidth / dm.density : fheight / dm.density, -1);
                    }
                }

                ArrayAdapter<JSONObject> adapter = new ArrayAdapter<JSONObject>(getActivity(), R.layout.list_item, items)
                {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent)
                    {
                        View view = convertView;
                        if (view == null) {
                            view = (View)inflater.inflate(R.layout.list_item, parent, false);
                        }

                        // icon
                        String urlStr = items.get(position).optString("icon");
                        if (urlStr != null) {
                            URL localUrl = prefetcher.map(delegate.makeFullURL(urlStr));
                            if (localUrl != null) {
                                final ImageView iv = (ImageView) view.findViewById(R.id.list_view_image);
                                SwipeAssetManager.sharedInstance().loadAsset(localUrl, false, new SwipeAssetManager.LoadAssetRunnable() {
                                    @Override
                                    public void run() {
                                        if (this.success) {
                                            iv.setImageBitmap(BitmapFactory.decodeStream(this.in));
                                            try {
                                                this.in.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                });
                            }
                        }

                        // title
                        TextView tv = (TextView) view.findViewById(R.id.list_view_text);
                        tv.setMinHeight(rowHeight);
                        tv.setText(items.get(position).optString("title", urlStr));
                        return view;
                    }
                };

                titleView.setText(document.optString("title", delegate.getFileName()));

                listView.setAdapter(adapter);
                listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                        Log.d(TAG, "clicked on " + position + ", " + items.get(position).optString("title"));
                        if (delegate != null)
                            delegate.browseTo(items.get(position).optString("url"));
                    }
                });
            }
        });
    }

    @Override
    public void progress(SwipePrefetcher prefetcher) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // TODO: implement
            }
        });
    }

}
