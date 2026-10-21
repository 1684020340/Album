package com.livephoto.viewer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从「内嵌动态照片」的 JPEG 里取出那段 MP4：
 * 文件结构 = 主图 JPEG + 追加的 MP4（视频长度记录在 XMP 里：
 * MotionPhoto v1 的 Item:Length，或旧版 GCamera:MicroVideoOffset）。
 */
public final class MotionVideo {
    private static final String TAG = "MotionVideo";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Map<String, File> READY = new ConcurrentHashMap<String, File>();
    private static final Map<String, Boolean> RUNNING = new ConcurrentHashMap<String, Boolean>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onReady(File f);
    }

    private MotionVideo() {
    }

    private static String key(LivePhotoItem it) {
        return it.id + ":" + it.imageSize;
    }

    public static File ready(LivePhotoItem it) {
        return it == null ? null : READY.get(key(it));
    }

    /** 提前准备（进查看器时调用，让长按可以立即播放）。 */
    public static void prepare(final Context ctx, final LivePhotoItem it) {
        if (it == null || !it.motionPhoto || it.videoUri != null) return;
        get(ctx, it, null);
    }

    public static void get(final Context ctx, final LivePhotoItem it, final Callback cb) {
        if (it == null || !it.motionPhoto || it.videoUri != null) {
            if (cb != null) cb.onReady(null);
            return;
        }
        final String k = key(it);
        File f = READY.get(k);
        if (f != null && f.isFile() && f.length() > 0) {
            if (cb != null) cb.onReady(f);
            return;
        }
        if (RUNNING.putIfAbsent(k, Boolean.TRUE) != null) {
            // 正在抽取，稍后回报
            if (cb != null) {
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        File ff = READY.get(k);
                        if (ff != null) cb.onReady(ff);
                    }
                }, 500);
            }
            return;
        }
        final Context app = ctx.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                File out = null;
                try {
                    out = extract(app, it);
                } catch (Throwable t) {
                    Log.w(TAG, "extract failed " + it.title, t);
                }
                if (out != null) READY.put(k, out);
                Log.d(TAG, "extract " + it.title + " -> " + (out != null ? out.length() : -1));
                RUNNING.remove(k);
                final File fo = out;
                if (cb != null) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onReady(fo);
                        }
                    });
                }
            }
        });
    }

    /** 同步取视频文件（给 WebView 实况播放用）。 */
    public static File getBlocking(Context ctx, final LivePhotoItem it) {
        if (it == null) return null;
        File f = READY.get(key(it));
        if (f != null && f.isFile() && f.length() > 0) return f;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        get(ctx, it, new Callback() {
            @Override
            public void onReady(File file) {
                latch.countDown();
            }
        });
        try {
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
        return READY.get(key(it));
    }

    private static File extract(Context ctx, LivePhotoItem it) {
        long fileSize = it.imageSize;
        if (fileSize <= 0 && it.imageFile != null) fileSize = it.imageFile.length();
        if (fileSize <= 0 && it.dataPath != null) fileSize = new File(it.dataPath).length();
        if (fileSize <= 0) return null;

        byte[] head = readHead(ctx, it, 256 * 1024);
        if (head == null) return null;
        String xmp = new String(head, StandardCharsets.ISO_8859_1);

        long videoLen = -1;
        long padding = 0;
        int mimeIdx = xmp.indexOf("video/mp4");
        if (mimeIdx >= 0) {
            Matcher len = Pattern.compile("Item:Length=\"(\\d+)\"").matcher(xmp);
            while (len.find()) {
                if (len.start() > mimeIdx) {
                    videoLen = Long.parseLong(len.group(1));
                    break;
                }
            }
            Matcher pad = Pattern.compile("Item:Padding=\"(\\d+)\"").matcher(xmp);
            while (pad.find()) {
                if (pad.start() > mimeIdx) {
                    padding = Long.parseLong(pad.group(1));
                    break;
                }
            }
        }
        if (videoLen <= 0) {
            Matcher mv = Pattern.compile("MicroVideoOffset=\"(\\d+)\"").matcher(xmp);
            if (mv.find()) videoLen = Long.parseLong(mv.group(1));
        }

        File cacheDir = new File(ctx.getCacheDir(), "motion");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File out = new File(cacheDir, "m_" + it.id + "_" + it.imageSize + ".mp4");

        if (videoLen > 0 && videoLen < fileSize) {
            long start = fileSize - padding - videoLen;
            if (start > 0 && isValidVideoStart(ctx, it, start)) {
                if (copyRange(ctx, it, start, start + videoLen, out)) return out;
            }
        }
        // 兜底：在文件里找 MP4 的 ftyp box
        if (findByFtyp(ctx, it, fileSize, out)) return out;
        return null;
    }

    private static boolean isValidVideoStart(Context ctx, LivePhotoItem it, long start) {
        byte[] b = readAt(ctx, it, start, 8);
        if (b == null || b.length < 8) return false;
        long size = ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
        String type = new String(b, 4, 4, StandardCharsets.ISO_8859_1);
        return size >= 8 && ("ftyp".equals(type) || "moov".equals(type) || "mdat".equals(type));
    }

    private static boolean findByFtyp(Context ctx, LivePhotoItem it, long fileSize, File out) {
        long limit = Math.min(fileSize, 24L * 1024 * 1024);
        long pos = 0;
        int chunk = 1024 * 1024;
        byte[] buf = new byte[chunk + 8];
        while (pos < limit) {
            int want = (int) Math.min(chunk + 8, limit - pos);
            byte[] b = readAt(ctx, it, pos, want);
            if (b == null || b.length < 12) return false;
            for (int i = 4; i + 4 <= b.length; i++) {
                if (b[i] == 'f' && b[i + 1] == 't' && b[i + 2] == 'y' && b[i + 3] == 'p') {
                    long start = pos + i - 4;
                    if (start > 8 && start < fileSize && isValidVideoStart(ctx, it, start)) {
                        long end = fileSize;
                        return copyRange(ctx, it, start, end, out);
                    }
                }
            }
            if (b.length < want) break;
            pos += chunk;
        }
        return false;
    }

    private static byte[] readHead(Context ctx, LivePhotoItem it, int max) {
        byte[] b = readAt(ctx, it, 0, max);
        return b;
    }

    private static byte[] readAt(Context ctx, LivePhotoItem it, long offset, int len) {
        if (len <= 0) return null;
        try {
            if (it.dataPath != null) {
                RandomAccessFile f = new RandomAccessFile(it.dataPath, "r");
                try {
                    long size = f.length();
                    if (offset >= size) return null;
                    int n = (int) Math.min(len, size - offset);
                    byte[] buf = new byte[n];
                    f.seek(offset);
                    f.readFully(buf);
                    return buf;
                } finally {
                    f.close();
                }
            }
            InputStream in = ctx.getContentResolver().openInputStream(it.imageUri);
            if (in == null) return null;
            try {
                long toSkip = offset;
                while (toSkip > 0) {
                    long skipped = in.skip(toSkip);
                    if (skipped <= 0) break;
                    toSkip -= skipped;
                }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                int total = 0;
                while (total < len) {
                    int r = in.read(buf, 0, Math.min(buf.length, len - total));
                    if (r <= 0) break;
                    bo.write(buf, 0, r);
                    total += r;
                }
                return bo.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean copyRange(Context ctx, LivePhotoItem it, long start, long end, File out) {
        FileOutputStream fos = null;
        InputStream in = null;
        try {
            if (it.dataPath != null) {
                RandomAccessFile f = new RandomAccessFile(it.dataPath, "r");
                try {
                    long size = f.length();
                    if (end > size) end = size;
                    long n = end - start;
                    if (n <= 0) return false;
                    f.seek(start);
                    fos = new FileOutputStream(out);
                    byte[] buf = new byte[256 * 1024];
                    long left = n;
                    while (left > 0) {
                        int r = f.read(buf, 0, (int) Math.min(buf.length, left));
                        if (r <= 0) break;
                        fos.write(buf, 0, r);
                        left -= r;
                    }
                } finally {
                    f.close();
                }
            } else {
                in = ctx.getContentResolver().openInputStream(it.imageUri);
                if (in == null) return false;
                long toSkip = start;
                while (toSkip > 0) {
                    long skipped = in.skip(toSkip);
                    if (skipped <= 0) break;
                    toSkip -= skipped;
                }
                fos = new FileOutputStream(out);
                byte[] buf = new byte[256 * 1024];
                long left = end - start;
                while (left > 0) {
                    int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (r <= 0) break;
                    fos.write(buf, 0, r);
                    left -= r;
                }
            }
            fos.flush();
            return out.length() > 0;
        } catch (Throwable t) {
            Log.w(TAG, "copyRange failed", t);
            return false;
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Throwable ignored) {
            }
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
