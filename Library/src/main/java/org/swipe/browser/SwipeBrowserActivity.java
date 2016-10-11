package org.swipe.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import org.swipe.network.SwipeAssetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * Created by pete on 10/4/16.
 */

public class SwipeBrowserActivity extends Activity implements SwipeBrowserView.Delegate {
    private static final String TAG = "SwBrowserActivity";
    private static final String ARG_URL = "org.swipe.arg.url";
    private static final String ACTION_BROWSE_TO = "org.swipe.action.browseTo";

    protected JSONObject document = null;
    protected String urlStr = null;
    protected String fileName = "";
    protected URL baseURL = null;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param url
     */


    public static URL makeFullURL(String url, URL baseURL) {
        try {
            return new URL(baseURL, url);
        } catch (MalformedURLException e) {
            Log.e("makeFullUrl", e.toString());
            return null;
        }
    }

    public URL makeFullURL(String url) {
        return SwipeBrowserActivity.makeFullURL(url, baseURL);
    }

    public void browseTo(String url) {
        String fullUrl = makeFullURL(url).toString();
        Intent intent = new Intent(this, SwipeBrowserActivity.class);
        intent.setAction(ACTION_BROWSE_TO);
        intent.putExtra(ARG_URL, fullUrl);
        startActivity(intent);
    }

    public String getFileName() { return fileName; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        SwipeAssetManager.sharedInstance().initialize(this);

        Intent intent = getIntent();
        if (intent != null)
        {
            Log.d(TAG, intent.toString());

            if (intent.getAction().equals(Intent.ACTION_VIEW))
            {
                // TODO
                Uri dataUri = intent.getData();
                try
                {
                    //InputStream in = getContentResolver().openInputStream(dataUri);

                }
                catch (Exception e)
                {
                    Log.e(TAG, String.format(Locale.US, "Copy run exception %s", e));
                }
            } else if (intent.getAction().equals(ACTION_BROWSE_TO)) {
                openUrl(intent.getStringExtra(ARG_URL));
            } else {
                openUrl(getDefaultUrl());
            }
        } else {
            openUrl(getDefaultUrl());
        }
    }

    protected String getDefaultUrl()
    {
        return "file:///android_asset/port.swipe";
    }

    public static String getContentName(ContentResolver resolver, Uri uri)
    {
        String name = null;

        if (uri != null && "content".equals(uri.getScheme()))
        {
            Cursor cursor = resolver.query(uri, null, null, null, null);
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (nameIndex >= 0)
            {
                name = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        else
        {
            name = uri.getLastPathSegment();
        }

        return name;
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }
    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (landscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }
    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    public boolean landscape() {
        if (document != null && document.optString("orientation", "").equalsIgnoreCase("landscape")) {
            return true;
        } else {
            return false;
        }
    }

    private String convertStreamToString(InputStream is) throws IOException {
        Writer writer = new StringWriter();

        char[] buffer = new char[2048];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } finally {
            is.close();
        }

        return writer.toString();
    }

    protected void openUrl(String _url){
        try {
            urlStr = _url;
            baseURL = new URL(urlStr.substring(0, urlStr.lastIndexOf('/') + 1)); // remove file name
            fileName = urlStr.substring(urlStr.lastIndexOf('/') + 1);
            Log.d(TAG, "onCreate urlStr=" + urlStr + " baseURL=" + baseURL + " fileName=" + fileName);

            if (baseURL.getProtocol().equalsIgnoreCase("file")) {
                String path = urlStr.substring("file:///android_asset/".length()); // remove path
                String data = convertStreamToString(getResources().getAssets().open(path));
                //Log.d(TAG, data);
                openDocument(new JSONObject(data));
            } else {
                URL url = new URL(urlStr);
                SwipeAssetManager.sharedInstance().loadAsset(url, true /* bypassCache for Swipe files*/, new SwipeAssetManager.LoadAssetRunnable() {
                    @Override
                    public void run() {
                        if (this.success && this.in != null) {
                            try {
                                String data = convertStreamToString(this.in);
                                openDocument(new JSONObject(data));
                            } catch (Exception e) {
                                displayError(e.toString());
                                try {
                                    openDocument(new JSONObject("{}"));
                                } catch (JSONException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        } else {
                            try {
                                openDocument(new JSONObject("{}"));
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                });
            }
        } catch (JSONException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"));} catch (JSONException e1) { e1.printStackTrace(); }
        } catch (MalformedURLException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"));} catch (JSONException e1) { e1.printStackTrace(); }
        } catch (IOException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"));} catch (JSONException e1) { e1.printStackTrace(); }
        }

    }
    private void openDocument(JSONObject _document) throws JSONException {
        document = _document;
        ViewGroup vg = null;
        String documentType = document.optString("type", "net.swipe.swipe");

        if (documentType.equalsIgnoreCase("net.swipe.swipe")) {
            vg = new SwipeBookBrowserView(this);
        } else if (documentType.equalsIgnoreCase("net.swipe.list")) {
            vg = new SwipeTableBrowserView(this);
        } else {
            displayError(getString(R.string.unknown_type) + ": " + documentType);
            return;
        }

        if (landscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        SwipeBrowserView viewer = (SwipeBrowserView) vg;
        viewer.setDelegate(this);
        viewer.loadDocument(document, baseURL);

        LinearLayout ll = (LinearLayout) findViewById(R.id.main_activity_fragment_container);
        ll.addView(viewer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
    }

    private void displayError(final String msg) {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                DialogFragment dialog = (new DialogFragment()
                {
                    @Override
                    public Dialog onCreateDialog(Bundle savedInstanceState)
                    {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setMessage(msg);
                        builder.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                            }
                        });

                        return builder.create();
                    }
                });
                dialog.show(getFragmentManager(), "Alert") ;
            }
        });
    }
}
