package org.swipe.network;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.swipe.core.SwipeUtil;

import java.io.File;
import java.io.FileDescriptor;
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
    private static final String TAG = "SwAssetMgr";
    private static final SwipeAssetManager sSharedInstance = new SwipeAssetManager();

    private File cacheDir = null;
    private Activity context = null;

    public static abstract class LoadAssetRunnable implements Runnable {
        public boolean success = false;
        public FileInputStream in = null;
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

    private final static String ASSET_PATH = "file:///android_asset/";

    public FileInputStream loadLocalAsset(final URL url) {
        final String path = url.getHost() + url.getPath();
        final File localF = new File(path);

        if (localF.exists()) {
            try {
                localF.setLastModified(new Date().getTime());
                return new FileInputStream(localF);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return null;
            }
        } else {
            return null;
        }
    }

    public void loadAsset(final URL url, final boolean bypassCache, final LoadAssetRunnable callback) {
        final String path = url.getHost().concat(url.getPath());
        final String dirPath = path.substring(0, path.lastIndexOf('/'));
        final String fileName = path.substring(path.lastIndexOf('/') + 1);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                File localF = new File(cacheDir, path);
                if (!localF.exists()) {
                    localF = new File(path);
                }
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

                try {
                    InputStream in = null;
                    final String urlStr = url.toString();
                    final int assetIndex = urlStr.indexOf(ASSET_PATH);
                    if (assetIndex == 0) {
                        String fileName = url.toString().substring("file:///android_asset/".length()); // remove path
                        in = context.getResources().getAssets().open(fileName);
                    } else {
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        in = connection.getInputStream();
                    }

                    File outD = new File(cacheDir, dirPath);
                    outD.mkdirs();
                    File outF = new File(outD, fileName);
                    outF.createNewFile();
                    Log.d(TAG, "saving to " + outF.getPath());
                    FileOutputStream out = new FileOutputStream(outF);
                    int bufCnt = 0;
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        if (bufCnt == 0 && len > 5) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < 6; i++) {
                                sb.append((char)buffer[i]);
                            }
                        }
                        bufCnt++;
                        out.write(buffer, 0, len);
                    }
                    out.close();

                    callback.in = new FileInputStream(outF);
                    callback.localURL = outF.toURL();
                    callback.success = true;
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                context.runOnUiThread(callback);
            }
        });
        thread.start();
    }

    public void reduce() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                SwipeUtil.Log(TAG, "reduce started");
                long kbCnt = 0;

                try {
                    while (cacheDir == null) {
                        // wait to be fully initialized
                        Thread.sleep(100);
                    }

                    kbCnt = reduce(cacheDir);
                } catch (InterruptedException e) {

                }

                SwipeUtil.Log(TAG, "reduce ended; kb used:" + kbCnt);
            }
        });
        thread.start();
    }

    private long reduce(File dir) {
        long kbCnt = 0;
        Date now = new Date();

        if (dir.exists() && dir.isDirectory()) {
            File files[] = dir.listFiles();
            long byteCnt = 0;
            for (File file : files) {
                Date modified = new Date(file.lastModified());
                long delta = now.getTime() - modified.getTime();
                SwipeUtil.Log(TAG, (file.isDirectory() ? "dir: " : "file: ") + file.getPath() + " " + modified + " delta: " + delta, 4);

                if (file.isDirectory()) {
                    long kb = reduce(file);

                    if (file.listFiles().length == 0) {
                        SwipeUtil.Log(TAG, "deleted dir " + file.getPath(), 1);
                        file.delete();
                    } else {
                        kbCnt += kb;
                    }
                } else {
                    if (delta > 604800000) {
                        // Over a week old
                        SwipeUtil.Log(TAG, "deleted file " + file.getPath(), 1);
                        file.delete();
                    } else {
                        byteCnt += file.length();
                    }
                }
            }

            kbCnt += byteCnt / 1024;
        }

        return kbCnt;
    }
}
