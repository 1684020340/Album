package com.livephoto.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 缩略图 / 大图加载，带内存缓存与后台线程。 */
public class ThumbLoader {
    private static ThumbLoader instance;

    public static synchronized ThumbLoader get(Context c) {
        if (instance == null) instance = new ThumbLoader(c.getApplicationContext());
        return instance;
    }

    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(48 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context app;

    private final File diskDir;

    private ThumbLoader(Context c) {
        app = c;
        diskDir = new File(c.getCacheDir(), "thumbs");
        if (!diskDir.exists()) diskDir.mkdirs();
    }

    private File diskFile(String key) {
        return new File(diskDir, Integer.toHexString(key.hashCode()) + ".jpg");
    }

    public void loadThumb(final LivePhotoItem item, final int target, final ImageView iv) {
        if (item == null) {
            iv.setImageDrawable(null);
            return;
        }
        final Uri uri = item.imageUri != null ? item.imageUri : item.videoUri;
        final File file = item.imageFile;
        final String key = (file != null ? file.getAbsolutePath() : String.valueOf(uri)) + (item.isVideo ? "@v" : "")
                + "@" + target;
        iv.setTag(key);
        Bitmap b = cache.get(key);
        if (b != null && !b.isRecycled()) {
            iv.setImageBitmap(b);
            return;
        }
        final File disk = diskFile(key);
        if (disk.isFile() && disk.length() > 0) {
            Bitmap db = BitmapFactory.decodeFile(disk.getAbsolutePath());
            if (db != null) {
                cache.put(key, db);
                iv.setImageBitmap(db);
                return;
            }
        }
        iv.setImageDrawable(null);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = item.isVideo ? decodeVideo(app, item, target)
                        : decode(app, uri, file, target);
                if (bm == null) return;
                cache.put(key, bm);
                try {
                    FileOutputStream fos = new FileOutputStream(disk);
                    bm.compress(Bitmap.CompressFormat.JPEG, 82, fos);
                    fos.close();
                } catch (Throwable ignored) {
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(iv.getTag())) iv.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    public void loadFull(final LivePhotoItem item, final int target, final LoadCallback cb) {
        if (item == null) return;
        final Uri uri = item.imageUri != null ? item.imageUri : item.videoUri;
        final File file = item.imageFile;
        final String key = (file != null ? file.getAbsolutePath() : String.valueOf(uri))
                + (item.isVideo ? "@v" : "") + "@full" + target;
        Bitmap b = cache.get(key);
        if (b != null && !b.isRecycled()) {
            cb.onBitmap(b);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = item.isVideo ? decodeVideo(app, item, target)
                        : decode(app, uri, file, target);
                if (bm != null) cache.put(key, bm);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onBitmap(bm);
                    }
                });
            }
        });
    }

    public interface LoadCallback {
        void onBitmap(Bitmap b);
    }

    public static Bitmap decodeVideo(Context c, LivePhotoItem item, int target) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            Uri uri = item.videoUri;
            if (item.videoFile != null) r.setDataSource(item.videoFile.getAbsolutePath());
            else if ("file".equals(uri.getScheme())) r.setDataSource(uri.getPath());
            else r.setDataSource(c, uri);
            Bitmap b = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) return null;
            int w = b.getWidth();
            int h = b.getHeight();
            if (w <= 0 || h <= 0) return b;
            float scale = Math.min(1f, target / (float) Math.max(w, h));
            if (scale < 1f) {
                Bitmap s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * scale)),
                        Math.max(1, Math.round(h * scale)), true);
                if (s != b) b.recycle();
                b = s;
            }
            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Throwable ignored) {
            }
        }
    }

    public static Bitmap decode(Context c, Uri uri, File file, int target) {
        try {
            int sample = 1;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            if (file != null) {
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            } else if (uri != null) {
                InputStream in = c.getContentResolver().openInputStream(uri);
                if (in != null) {
                    try {
                        BitmapFactory.decodeStream(in, null, bounds);
                    } finally {
                        in.close();
                    }
                }
            }
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            if (w > 0 && h > 0) {
                while (w / (sample * 2) >= target && h / (sample * 2) >= target) {
                    sample *= 2;
                }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    ImageDecoder.Source src;
                    if (file != null) {
                        src = ImageDecoder.createSource(file);
                    } else {
                        src = ImageDecoder.createSource(c.getContentResolver(), uri);
                    }
                    final int s = sample;
                    return ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                        @Override
                        public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                                    ImageDecoder.Source source) {
                            decoder.setTargetSampleSize(s);
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        }
                    });
                } catch (Throwable ignored) {
                    // fall through to BitmapFactory
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            if (file != null) return BitmapFactory.decodeFile(file.getAbsolutePath(), o);
            if (uri == null) return null;
            InputStream in = c.getContentResolver().openInputStream(uri);
            try {
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                if (in != null) in.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
