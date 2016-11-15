package org.swipe.core;

import android.media.MediaPlayer;
import android.util.Log;

import org.json.JSONObject;
import org.swipe.browser.SwipeBrowserActivity;
import org.swipe.network.SwipeAssetManager;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pete on 10/5/16.
 */

public class SwipePageTemplate {
    private static final String TAG = "SwPageTemplate";
    public JSONObject pageTemplateInfo = null;

    private SwipePage.Delegate delegate = null;
    private MediaPlayer bgmPlayer = null;
    private boolean fDebugEntered = false;
    protected List<URL> resourceURLs = null;

    public SwipePageTemplate(JSONObject info, SwipePage.Delegate delegate) {
        pageTemplateInfo = info;
    }

    public List<URL> getResourceURLs() {
        if (resourceURLs == null) {
            resourceURLs = new ArrayList<>();
            String value = pageTemplateInfo.optString("bgm", null);
            if (value != null) {
                URL url = delegate.makeFullURL(value);
                resourceURLs.add(url);
            }
        }

        return resourceURLs;
    }

    // This function is called when a page associated with this pageTemplate is activated (entered)
    //  AND the previous page is NOT associated with this pageTemplate object.

    void didEnter(SwipePage.Delegate delegate) {
        if (fDebugEntered) throw new AssertionError("re-entering");
        fDebugEntered = true;

        String value = pageTemplateInfo.optString("bgm", null);
        if (value == null) {
            return;
        }
        URL urlRaw = delegate.makeFullURL(value);
        URL url = delegate.map(urlRaw);
        if (url != null) {
            Log.d(TAG, "SWPageTemplate didEnter with bgm=" + value);
            try {
                FileDescriptor fd = SwipeAssetManager.sharedInstance().loadLocalAsset(url).getFD();
                if (fd != null) {
                    bgmPlayer = new MediaPlayer();
                    try {
                        bgmPlayer.setDataSource(fd);
                        bgmPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            @Override
                            public void onPrepared(MediaPlayer mp) {
                                bgmPlayer.start();
                            }
                        });
                        bgmPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                // We repeat the bgm
                                mp.seekTo(0);
                                mp.start();
                            }
                        });
                        bgmPlayer.prepareAsync();
                        return;
                    } catch (IOException e) {
                        bgmPlayer = null;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "didEnter failed to create URL");
    }

    // This function is called when a page associated with this pageTemplate is deactivated (leaved)
    //  AND the subsequent page is not associated with this pageTemplate object.

    void didLeave() {
        if (!fDebugEntered) throw new AssertionError("leaving without entering");
        fDebugEntered = false;

        if (bgmPlayer != null) {
            bgmPlayer.stop();
            bgmPlayer = null;
        }
    }
}
