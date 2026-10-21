package com.livephoto.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** iOS 风格相册（WebView 版）：加载 assets/ios_album.html，用本机照片填充。 */
public class HtmlAlbumActivity extends Activity {
    private static final int REQ_FILE = 1001;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private final Map<String, LivePhotoItem> items = new HashMap<String, LivePhotoItem>();
    private LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(36 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        try {
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
        } catch (Throwable ignored) {
        }

        web.addJavascriptInterface(new Bridge(), "AndroidPhotos");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.d("HtmlConsole", m.message() + " @" + m.lineNumber());
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(i, REQ_FILE);
                    return true;
                } catch (Throwable t) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        web.loadUrl("file:///android_asset/ios_album.html");

        web.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    web.evaluateJavascript(
                            "JSON.stringify({photos:(typeof photos!=='undefined'?photos.length:-1),"
                                    + "grid:(document.getElementById('photosGrid')?document.getElementById('photosGrid').children.length:-1),"
                                    + "text:document.body.innerText.slice(0,200)})",
                            new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String value) {
                                    android.util.Log.d("HtmlDebug", value);
                                }
                            });
                } catch (Throwable ignored) {
                }
            }
        }, 4000);
    }

    private class Bridge {
        @JavascriptInterface
        public void log(String msg) {
            android.util.Log.d("HtmlBridge", msg);
        }

        @JavascriptInterface
        public String list() {
            try {
                Context ctx = getApplicationContext();
                try {
                    Library.loadCache(ctx);
                } catch (Throwable ignored) {
                }
                try {
                    Library.refreshFast(ctx);
                } catch (Throwable ignored) {
                }
                List<LivePhotoItem> all = Library.snapshot();
                JSONArray arr = new JSONArray();
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.CHINA);
                synchronized (items) {
                    items.clear();
                    int n = 0;
                    for (LivePhotoItem it : all) {
                        if (it.isVideo) continue;
                        if (it.id <= 0) continue;
                        if (n >= 1500) break;
                        JSONObject o = new JSONObject();
                        String id = String.valueOf(it.id);
                        o.put("id", id);
                        o.put("url", "http://photo.local/t/" + id);
                        o.put("title", it.title == null ? "照片" : it.title);
                        o.put("location", it.bucketName == null ? "照片" : it.bucketName);
                        Date d = new Date(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
                        o.put("date", df.format(d));
                        o.put("time", tf.format(d));
                        o.put("camera", "");
                        o.put("lens", "");
                        o.put("iso", "");
                        o.put("shutter", "");
                        JSONArray tags = new JSONArray();
                        o.put("tags", tags);
                        o.put("favorite", Favs.isFav(ctx, it));
                        o.put("filter", "none");
                        o.put("brightness", 100);
                        o.put("contrast", 100);
                        o.put("saturate", 100);
                        o.put("isLive", it.live());
                        arr.put(o);
                        items.put(id, it);
                        n++;
                    }
                }
                android.util.Log.d("HtmlBridge", "list size=" + arr.length());
                return arr.toString();
            } catch (Throwable t) {
                android.util.Log.e("HtmlBridge", "list failed", t);
                return "[]";
            }
        }

        @JavascriptInterface
        public String videoPath(String id) {
            try {
                LivePhotoItem it;
                synchronized (items) {
                    it = items.get(id);
                }
                if (it == null || !it.live()) return "";
                Context ctx = getApplicationContext();
                File dir = new File(getCacheDir(), "livevideo");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, "v_" + id + ".mp4");
                if (out.isFile() && out.length() > 0) return out.getAbsolutePath();
                if (it.videoFile != null) {
                    copyFile(it.videoFile, out);
                    if (out.isFile() && out.length() > 0) return out.getAbsolutePath();
                } else if (it.videoUri != null) {
                    InputStream in = null;
                    OutputStream os = null;
                    try {
                        in = ctx.getContentResolver().openInputStream(it.videoUri);
                        os = new FileOutputStream(out);
                        byte[] buf = new byte[256 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        os.flush();
                    } finally {
                        try { if (os != null) os.close(); } catch (Throwable ignored) {}
                        try { if (in != null) in.close(); } catch (Throwable ignored) {}
                    }
                    if (out.isFile() && out.length() > 0) return out.getAbsolutePath();
                }
                if (it.motionPhoto) {
                    File f = MotionVideo.getBlocking(ctx, it);
                    if (f != null) return f.getAbsolutePath();
                }
                return "";
            } catch (Throwable t) {
                return "";
            }
        }

        private void copyFile(File src, File out) {
            InputStream in = null;
            OutputStream os = null;
            try {
                in = new FileInputStream(src);
                os = new FileOutputStream(out);
                byte[] buf = new byte[256 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
            } catch (Throwable ignored) {
            } finally {
                try { if (os != null) os.close(); } catch (Throwable ignored) {}
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
            }
        }

        @JavascriptInterface
        public void setFavorite(String id, boolean fav) {
            try {
                LivePhotoItem it;
                synchronized (items) {
                    it = items.get(id);
                }
                if (it != null) Favs.set(getApplicationContext(), it, fav);
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public String ping() {
            return "ok";
        }
    }

    private WebResourceResponse serve(Uri uri) {
        try {
            if (uri == null || !"photo.local".equals(uri.getHost())) return null;
            List<String> seg = uri.getPathSegments();
            if (seg.size() < 2) return null;
            String kind = seg.get(0);
            String id = seg.get(1);
            if (!"t".equals(kind) && !"f".equals(kind)) return null;
            int target = "t".equals(kind) ? 420 : 2000;
            String key = kind + id;
            Bitmap b = cache.get(key);
            if (b == null) {
                LivePhotoItem it;
                synchronized (items) {
                    it = items.get(id);
                }
                if (it == null) return null;
                b = ThumbLoader.decode(getApplicationContext(), it.imageUri, it.imageFile, target);
                if (b == null) return null;
                cache.put(key, b);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 82, bo);
            return new WebResourceResponse("image/jpeg", null,
                    new ByteArrayInputStream(bo.toByteArray()));
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            Uri[] uris = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            if (fileCallback != null) {
                fileCallback.onReceiveValue(uris);
                fileCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.removeJavascriptInterface("AndroidPhotos");
                web.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
