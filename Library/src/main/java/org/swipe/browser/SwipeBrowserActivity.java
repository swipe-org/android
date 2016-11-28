package org.swipe.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.swipe.core.SwipeUtil;
import org.swipe.network.SwipeAssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
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
    protected SwipeBrowserView browserView = null;

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

    void openTimeLineUrl(final Uri dataUri) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.show();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                    StringBuilder builder = new StringBuilder();
                    builder.append("{\n" +
                            "    \"type\":\"net.swipe.list\",\n" +
                            "    \"rowHeight\":\"10%\",\n" +
                            "    \"sections\":[\n" +
                            "        {\n" +
                            "            \"items\":[");



                    final int itemsRequested = 200;
                    int totalItemCnt = 0;
                    boolean hasData = true;
                    int loopCnt = 0;

                    while (hasData && loopCnt < 5) {
                        loopCnt++;

                        try {
                            String offset = "?limit=" + itemsRequested +"&offset=" + totalItemCnt;
                            final String urlStr = dataUri.toString().concat(offset);
                            final URL url = new URL(urlStr);
                            Log.d(TAG, "fetching url: " + url.toString());
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            final JSONObject json = new JSONObject(convertStreamToString(connection.getInputStream()));
                            JSONArray projects = json.getJSONArray("projects");
                            final int itemCnt = projects.length();
                            if (itemCnt > 0) {
                                for (int i = 0; i < itemCnt; i++) {
                                    StringBuilder item = new StringBuilder();
                                    JSONObject project = projects.getJSONObject(i);
                                    JSONObject swipe = project.getJSONObject("swipe");
                                    item.append("    { \"url\":\"");
                                    item.append(swipe.getString("url"));
                                    String title = project.getString("title");
                                    item.append("\", \"title\":\"");
                                    item.append("#").append(totalItemCnt+i).append(" ");
                                    item.append(title);
                                    JSONObject thumbnail = project.optJSONObject("thumbnail");
                                    if (thumbnail != null) {
                                        item.append("\", \"icon\":\"");
                                        item.append(thumbnail.optString("url"));
                                    }
                                    item.append("\" },\n");
                                    String swipeItem = item.toString();
                                    Log.d(TAG, "item: " + swipeItem);
                                    builder.append(swipeItem);
                                }
                                totalItemCnt += itemCnt;
                                if (itemCnt < itemsRequested) {
                                    hasData = false;
                                }
                            } else {
                                hasData = false;
                            }
                            connection.disconnect();
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                            hasData = false;
                        }
                    }

                    builder.append("    {}\n");  // dummy item to deal with trailing comma
                    builder.append("    ]\n" +
                            "        }\n" +
                            "    ]\n" +
                            "}");

                    final String swipe = builder.toString();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                progressDialog.dismiss();
                                openDocument(new JSONObject(swipe), dataUri.toString());
                            } catch (Exception e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    });

            }
        });
        thread.start();
    }

    private static final int MY_DATA_CHECK_CODE = 9;

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

            if (intent.getAction().equals(Intent.ACTION_VIEW)) {
                final Uri dataUri = intent.getData();
                if (dataUri.getPath().indexOf("/api/1.0/projects/timeline") == 0) {
                    openTimeLineUrl(dataUri);
                } else {
                    String swipeUrl = dataUri.toString().replace("player", "swipe");
                    openUrl(swipeUrl);
                }
            } else if (intent.getAction().equals(ACTION_BROWSE_TO)) {
                openUrl(intent.getStringExtra(ARG_URL));
            } else {
                openUrl(getDefaultUrl());
            }
        } else {
            openUrl(getDefaultUrl());
        }

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    protected String getDefaultUrl()
    {
        return "file:///android_asset/index.swipe";
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

        if (browserView != null) {
            browserView.onResume();
        }
    }
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        if (browserView != null) {
            browserView.onPause();
        }
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
                openDocument(new JSONObject(data), urlStr);
            } else {
                URL url = new URL(urlStr);
                SwipeAssetManager.sharedInstance().loadAsset(url, true /* bypassCache for Swipe files*/, new SwipeAssetManager.LoadAssetRunnable() {
                    @Override
                    public void run() {
                        if (this.success && this.in != null) {
                            try {
                                String data = convertStreamToString(this.in);
                                openDocument(new JSONObject(data), urlStr);
                            } catch (Exception e) {
                                displayError(e.toString());
                                try {
                                    openDocument(new JSONObject("{}"), "error");
                                } catch (JSONException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        } else {
                            try {
                                openDocument(new JSONObject("{}"), "error");
                            } catch (JSONException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                });
            }
        } catch (JSONException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"), "error");} catch (JSONException e1) { e1.printStackTrace(); }
        } catch (MalformedURLException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"), "error");} catch (JSONException e1) { e1.printStackTrace(); }
        } catch (IOException e) {
            displayError(e.toString());
            try { openDocument(new JSONObject("{}"), "error");} catch (JSONException e1) { e1.printStackTrace(); }
        }

    }
    private void openDocument(JSONObject _document, final String urlStr) throws JSONException {
        document = _document;
        String documentType = document.optString("type", "net.swipe.swipe");

        if (documentType.equalsIgnoreCase("net.swipe.swipe") || documentType.equalsIgnoreCase("org.swipe.swipe")) {
            browserView = new SwipeBookBrowserView(this);
        } else if (documentType.equalsIgnoreCase("net.swipe.list") || documentType.equalsIgnoreCase("org.swipe.list")) {
            browserView = new SwipeTableBrowserView(this);
        } else {
            displayError(getString(R.string.unknown_type) + ": " + documentType);
            return;
        }

        if (landscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        browserView.setDelegate(this);
        browserView.loadDocument(document, urlStr, baseURL);

        LinearLayout ll = (LinearLayout) findViewById(R.id.main_activity_fragment_container);
        ll.addView(browserView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
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
