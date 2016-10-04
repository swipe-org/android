package org.swipe.network;

import android.app.Activity;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

/**
 * Created by pete on 9/21/16.
 */

public class SwipeAssetManager {
    private static final String TAG = "SAssetMgr";
    private static final SwipeAssetManager sSharedInstance = new SwipeAssetManager();

    private File cacheDir = null;
    private Activity context = null;

    public static abstract class LoadAssetRunnable implements Runnable {
        public boolean success = false;
        public InputStream in = null;
        public URL localURL = null;
        public Exception error = null;
    }

    static public SwipeAssetManager sharedInstance() {
        return sSharedInstance;
    }

    public boolean initialize(Activity _context) {
        context = _context;
        cacheDir = new File(context.getCacheDir(), "org.swipe.cache");

        if (cacheDir.exists()) {
            if (cacheDir.isDirectory()) {
                return true;
            }
            if (!cacheDir.delete()) {
                return false;
            }
        }

        return cacheDir.mkdir();
    }

    public void loadAsset(final URL url, final boolean bypassCache, final LoadAssetRunnable callback) {
        if (url.getProtocol().equalsIgnoreCase("file")) {
            try {
                String fileName = url.toString().substring("file:///android_asset/".length()); // remove path
                callback.in = context.getResources().getAssets().open(fileName);
                callback.localURL = url;
                callback.success = true;
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
            context.runOnUiThread(callback);
            return;
        }

        final String path = url.getHost() +  url.getPath();
        final String dirPath = path.substring(0, path.lastIndexOf('/'));
        final String fileName = path.substring(path.lastIndexOf('/') + 1);
        final File localF = new File(cacheDir, path);

        if (!bypassCache && localF.exists()) {
            try {
                localF.setLastModified(new Date().getTime());
                callback.in = new FileInputStream(localF);
                callback.localURL = localF.toURL();
                callback.success = true;
                Log.d(TAG, "reuse " + fileName);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
            context.runOnUiThread(callback);
            return;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    InputStream in = connection.getInputStream();
                    File outD = new File(cacheDir, dirPath);
                    outD.mkdirs();
                    File outF = new File(outD, fileName);
                    outF.createNewFile();
                    Log.d(TAG, "saving to " + outF.getPath());
                    FileOutputStream out = new FileOutputStream(outF);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.close();

                    callback.in = new FileInputStream(outF);
                    callback.localURL = outF.toURL();
                    callback.success = true;
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
                context.runOnUiThread(callback);
            }
        });
        thread.start();
    }

    void reduce() {
        // TODO: Implement
    }
}
