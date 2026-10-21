package com.livephoto.viewer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本机照片库，全部读取本机照片：
 * 1) 系统相册 MediaStore 的全部照片；
 * 2) 同名配对的 图片 + 视频（iPhone 导出的 HEIC/JPG + MOV）；
 * 3) JPEG 内嵌视频的动态照片（MVIMG_* / 小米 / 三星等）；
 * 4) 常见目录与应用内导入的文件。
 */
public final class Library {
    private static final String TAG = "LivePhotoLib";
    private static final Object LOCK = new Object();
    private static final List<LivePhotoItem> ITEMS = new ArrayList<LivePhotoItem>();
    private static final Map<String, Boolean> MOTION_INDEX = new HashMap<String, Boolean>();
    private static final AtomicBoolean SCAN_ACTIVE = new AtomicBoolean(false);
    private static boolean indexLoaded = false;

    private Library() {
    }

    public static List<LivePhotoItem> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<LivePhotoItem>(ITEMS);
        }
    }

    private static File cacheFile(Context ctx) {
        return new File(ctx.getFilesDir(), "lib_cache.txt");
    }

    /** 磁盘缓存：冷启动先显示上次的库，再后台刷新。 */
    public static int loadCache(Context ctx) {
        BufferedReader r = null;
        try {
            File f = cacheFile(ctx);
            if (!f.isFile() || f.length() == 0) return 0;
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            List<LivePhotoItem> list = new ArrayList<LivePhotoItem>();
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 14) continue;
                try {
                    LivePhotoItem it = new LivePhotoItem();
                    it.id = Long.parseLong(p[1]);
                    it.videoId = Long.parseLong(p[2]);
                    it.title = p[3];
                    it.bucketName = p[4].isEmpty() ? null : p[4];
                    it.dateMs = Long.parseLong(p[5]);
                    it.dateAdded = Long.parseLong(p[6]);
                    it.imageSize = Long.parseLong(p[7]);
                    it.videoSize = Long.parseLong(p[8]);
                    it.durationMs = Long.parseLong(p[9]);
                    it.width = Integer.parseInt(p[10]);
                    it.height = Integer.parseInt(p[11]);
                    int flags = Integer.parseInt(p[12]);
                    it.motionPhoto = (flags & 1) != 0;
                    it.motionChecked = (flags & 2) != 0;
                    it.isVideo = (flags & 4) != 0;
                    if (it.id > 0) {
                        it.imageUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it.id);
                    }
                    if (it.videoId > 0) {
                        it.videoUri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.videoId);
                    }
                    if (it.imageUri == null && it.videoUri == null) continue;
                    list.add(it);
                } catch (Throwable ignored) {
                }
            }
            Collections.sort(list, new Comparator<LivePhotoItem>() {
                @Override
                public int compare(LivePhotoItem a, LivePhotoItem b) {
                    return Long.compare(b.dateMs, a.dateMs);
                }
            });
            synchronized (LOCK) {
                ITEMS.clear();
                ITEMS.addAll(list);
            }
            Log.d(TAG, "cache loaded: " + list.size());
            return list.size();
        } catch (Throwable t) {
            Log.w(TAG, "load cache failed", t);
            return 0;
        } finally {
            if (r != null) try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void saveCache(Context ctx) {
        FileOutputStream fos = null;
        try {
            List<LivePhotoItem> snap = snapshot();
            if (snap.isEmpty()) return;
            File tmp = new File(ctx.getFilesDir(), "lib_cache.txt.tmp");
            fos = new FileOutputStream(tmp);
            StringBuilder sb = new StringBuilder();
            for (LivePhotoItem it : snap) {
                if (it.id <= 0 && it.imageFile == null) continue;
                if (it.imageFile != null && it.id <= 0) continue; // 本地文件项下次重扫
                int flags = (it.motionPhoto ? 1 : 0) | (it.motionChecked ? 2 : 0) | (it.isVideo ? 4 : 0);
                sb.append("1\t").append(it.id).append('\t').append(it.videoId).append('\t')
                        .append(clean(it.title)).append('\t').append(clean(it.bucketName)).append('\t')
                        .append(it.dateMs).append('\t').append(it.dateAdded).append('\t')
                        .append(it.imageSize).append('\t').append(it.videoSize).append('\t')
                        .append(it.durationMs).append('\t').append(it.width).append('\t')
                        .append(it.height).append('\t').append(flags).append('\t').append(0)
                        .append('\n');
            }
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
            fos = null;
            tmp.renameTo(cacheFile(ctx));
            Log.d(TAG, "cache saved: " + snap.size());
        } catch (Throwable t) {
            Log.w(TAG, "save cache failed", t);
        } finally {
            if (fos != null) try {
                fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** 快速刷新：只扫 MediaStore + 导入目录（冷启动用，不扫四个大目录）。 */
    public static int refreshFast(Context context) {
        loadIndex(context);
        Set<String> seen = new HashSet<String>();
        List<LivePhotoItem> out = new ArrayList<LivePhotoItem>();
        try {
            out.addAll(scanMediaStore(context));
        } catch (Throwable t) {
            Log.w(TAG, "fast media scan failed", t);
        }
        try {
            for (LivePhotoItem it : scanImported(context)) {
                if (seen.add(keyOf(it.title, it.imageSize))) out.add(it);
            }
        } catch (Throwable t) {
            Log.w(TAG, "fast imported scan failed", t);
        }
        for (LivePhotoItem it : out) applyMotionIndex(it);
        Collections.sort(out, new Comparator<LivePhotoItem>() {
            @Override
            public int compare(LivePhotoItem a, LivePhotoItem b) {
                return Long.compare(b.dateMs, a.dateMs);
            }
        });
        synchronized (LOCK) {
            ITEMS.clear();
            ITEMS.addAll(out);
        }
        saveCache(context);
        Log.d(TAG, "refreshFast: " + out.size());
        return out.size();
    }

    public static int refresh(Context context) {
        loadIndex(context);
        Set<String> seen = new HashSet<String>();
        List<LivePhotoItem> out = new ArrayList<LivePhotoItem>();

        List<LivePhotoItem> media = new ArrayList<LivePhotoItem>();
        try {
            media.addAll(scanMediaStore(context));
        } catch (Throwable t) {
            Log.w(TAG, "scan media store failed", t);
        }
        for (LivePhotoItem it : media) {
            out.add(it);
            seen.add(keyOf(it.title, it.imageSize));
        }

        try {
            for (LivePhotoItem it : scanImported(context)) {
                if (seen.add(keyOf(it.title, it.imageSize))) out.add(it);
            }
        } catch (Throwable t) {
            Log.w(TAG, "scan imported failed", t);
        }

        try {
            for (LivePhotoItem it : scanCommonDirs(seen)) {
                out.add(it);
            }
        } catch (Throwable t) {
            Log.w(TAG, "scan common dirs failed", t);
        }

        for (LivePhotoItem it : out) applyMotionIndex(it);

        Collections.sort(out, new Comparator<LivePhotoItem>() {
            @Override
            public int compare(LivePhotoItem a, LivePhotoItem b) {
                return Long.compare(b.dateMs, a.dateMs);
            }
        });
        synchronized (LOCK) {
            ITEMS.clear();
            ITEMS.addAll(out);
        }
        saveCache(context);
        Log.d(TAG, "refresh: total=" + out.size() + " media=" + media.size());
        return out.size();
    }

    /** 触发系统媒体库扫描常见目录（首次运行 / 导入后）。 */
    public static void requestMediaScan(Context context, long timeoutMs) {
        try {
            String[] dirs = new String[]{
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath()
            };
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(dirs.length);
            MediaScannerConnection.scanFile(context, dirs, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            latch.countDown();
                        }
                    });
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            Log.w(TAG, "request media scan failed", t);
        }
    }

    public static boolean isScanningMotion() {
        return SCAN_ACTIVE.get();
    }

    /** 后台逐张识别内嵌动态照片，识别进度通过 onUpdate 回调（后台线程）。 */
    public static void startMotionScan(final Context ctx, final Runnable onUpdate) {
        if (!SCAN_ACTIVE.compareAndSet(false, true)) return;
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int changed = 0;
                int checked = 0;
                try {
                    List<LivePhotoItem> snap = snapshot();
                    for (LivePhotoItem it : snap) {
                        if (it.motionChecked) continue;
                        boolean found = detectMotion(app, it);
                        it.motionChecked = true;
                        it.motionPhoto = found;
                        putIndex(it, found);
                        checked++;
                        if (found) {
                            changed++;
                            Log.d(TAG, "motion photo: " + it.title);
                            if (changed % 3 == 0) {
                                saveIndex(app);
                                if (onUpdate != null) onUpdate.run();
                            }
                        } else if (checked % 300 == 0) {
                            if (onUpdate != null) onUpdate.run();
                        }
                    }
                    saveIndex(app);
                } catch (Throwable t) {
                    Log.w(TAG, "motion scan failed", t);
                } finally {
                    SCAN_ACTIVE.set(false);
                    if (onUpdate != null) onUpdate.run();
                    Log.d(TAG, "motion scan done checked=" + checked + " found=" + changed);
                }
            }
        }).start();
    }

    private static boolean detectMotion(Context ctx, LivePhotoItem it) {
        if (it.dataPath == null && it.imageFile != null) {
            it.dataPath = it.imageFile.getAbsolutePath();
        }
        String ext = extOf(it.title == null ? "" : it.title);
        if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("heic")) return false;
        if (it.imageSize > 0 && it.imageSize < 60 * 1024) return false;
        byte[] head = readHead(ctx, it, 128 * 1024);
        if (head == null || head.length == 0) return false;
        String s = new String(head, java.nio.charset.StandardCharsets.ISO_8859_1);
        return s.contains("MotionPhoto") || s.contains("MicroVideo") || s.contains("GCamera:");
    }

    private static byte[] readHead(Context ctx, LivePhotoItem it, int max) {
        try {
            if (it.dataPath != null) {
                RandomAccessFile f = new RandomAccessFile(it.dataPath, "r");
                try {
                    int n = (int) Math.min(max, f.length());
                    byte[] b = new byte[n];
                    f.readFully(b);
                    return b;
                } finally {
                    f.close();
                }
            }
            InputStream in = ctx.getContentResolver().openInputStream(it.imageUri);
            if (in == null) return null;
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[Math.min(max, 64 * 1024)];
                int total = 0;
                while (total < max) {
                    int r = in.read(buf, 0, Math.min(buf.length, max - total));
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

    // ---------- 识别结果持久化 ----------

    private static File indexFile(Context ctx) {
        return new File(ctx.getFilesDir(), "motion_index.txt");
    }

    private static void loadIndex(Context ctx) {
        if (indexLoaded) return;
        indexLoaded = true;
        BufferedReader r = null;
        try {
            File f = indexFile(ctx);
            if (!f.isFile()) return;
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length >= 3) {
                    MOTION_INDEX.put(p[1] + ":" + p[2], "1".equals(p[0]));
                }
            }
            Log.d(TAG, "motion index loaded: " + MOTION_INDEX.size());
        } catch (Throwable t) {
            Log.w(TAG, "load index failed", t);
        } finally {
            if (r != null) try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String idxKey(LivePhotoItem it) {
        return it.id + ":" + it.imageSize;
    }

    private static void applyMotionIndex(LivePhotoItem it) {
        Boolean b = MOTION_INDEX.get(idxKey(it));
        if (b != null) {
            it.motionChecked = true;
            it.motionPhoto = b;
        }
    }

    private static void putIndex(LivePhotoItem it, boolean motion) {
        MOTION_INDEX.put(idxKey(it), motion);
    }

    private static void saveIndex(Context ctx) {
        FileOutputStream fos = null;
        try {
            File f = indexFile(ctx);
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            fos = new FileOutputStream(tmp);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Boolean> e : MOTION_INDEX.entrySet()) {
                String[] parts = e.getKey().split(":");
                if (parts.length != 2) continue;
                sb.append(e.getValue() ? '1' : '0').append('|')
                        .append(parts[0]).append('|').append(parts[1]).append('\n');
            }
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
            fos = null;
            tmp.renameTo(f);
        } catch (Throwable t) {
            Log.w(TAG, "save index failed", t);
        } finally {
            if (fos != null) try {
                fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- 基础工具 ----------

    private static String keyOf(String title, long size) {
        return (title == null ? "" : baseName(title).toLowerCase(Locale.ROOT)) + "|" + size;
    }

    static boolean isImageExt(String ext) {
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("heic")
                || ext.equals("heif") || ext.equals("png") || ext.equals("webp");
    }

    static boolean isVideoExt(String ext) {
        return ext.equals("mov") || ext.equals("mp4") || ext.equals("m4v") || ext.equals("3gp");
    }

    static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String niceBucket(String old) {
        if (old == null) return null;
        String n = old.trim();
        if (n.isEmpty()) return old;
        String l = n.toLowerCase(Locale.ROOT);
        if (l.equals("screenshots") || l.equals("screenshot") || l.equals("截屏录屏") || l.equals("截屏")) return "截屏录屏";
        if (l.equals("livephotos") || l.equals("livephoto") || l.equals("实况照片")) return "实况照片";
        if (l.equals("camera") || l.equals("相机")) return "相机";
        if (l.equals("weixin") || l.equals("wechat") || l.equals("微信")) return "微信";
        if (l.equals("download") || l.equals("downloads") || l.equals("下载")) return "下载";
        if (l.equals("movies") || l.equals("video") || l.equals("视频")) return "视频";
        if (l.equals("pictures") || l.equals("图片")) return "相册";
        if (l.equals("dcim")) return "相册";
        if (l.equals("save_image") || l.equals("saveimage")) return "保存的图片";
        if (l.equals("thumb") || l.equals("thumbnail")) return "缩略图";
        if (l.equals("like")) return "喜欢";
        if (l.equals("alipay") || l.equals("alipay_images")) return "支付宝";
        if (l.equals("qq") || l.equals("tencent")) return "QQ";
        if (l.equals("weibo")) return "微博";
        if (l.equals("bilibili") || l.equals("哔哩哔哩")) return "哔哩哔哩";
        if (l.equals("taobao")) return "淘宝";
        if (l.equals("douyin") || l.equals("抖音")) return "抖音";
        if (l.equals("telegram")) return "Telegram";
        if (l.equals("com.tencent.mm") || l.equals("mmexport")) return "微信";
        return old;
    }

    static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static List<LivePhotoItem> scanMediaStore(Context context) {
        List<LivePhotoItem> result = new ArrayList<LivePhotoItem>();
        Map<String, LivePhotoItem> map = new HashMap<String, LivePhotoItem>();
        Map<Long, LivePhotoItem> byId = new HashMap<Long, LivePhotoItem>();

        String[] imgCols = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
        };
        Uri imgBase = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cur = null;
        try {
            cur = context.getContentResolver().query(imgBase, imgCols, "is_pending=0", null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    String name = cur.getString(1);
                    if (name == null) continue;
                    String ext = extOf(name);
                    if (!isImageExt(ext)) continue;
                    String bucket = cur.getString(2);
                    LivePhotoItem item = new LivePhotoItem();
                    item.id = id;
                    item.title = name;
                    item.bucket = bucket;
                    item.imageUri = ContentUris.withAppendedId(imgBase, id);
                    item.width = cur.getInt(4);
                    item.height = cur.getInt(5);
                    item.imageSize = cur.getLong(6);
                    long taken = cur.getLong(7);
                    if (taken <= 0) taken = cur.getLong(8) * 1000L;
                    item.dateMs = taken;
                    try {
                        item.dataPath = cur.getString(9);
                        item.bucketName = niceBucket(cur.getString(10));
                        item.dateAdded = cur.getLong(11) * 1000L;
                    } catch (Throwable ignored) {
                    }
                    String key = (bucket == null ? "" : bucket) + "|" + baseName(name).toLowerCase(Locale.ROOT);
                    map.put(key, item);
                    byId.put(id, item);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "images query failed", t);
        } finally {
            if (cur != null) cur.close();
        }

        String[] vidCols = new String[]{
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
        };
        Uri vidBase = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        cur = null;
        try {
            cur = context.getContentResolver().query(vidBase, vidCols, "is_pending=0", null, null);
            if (cur != null) {
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    String name = cur.getString(1);
                    if (name == null) continue;
                    String ext = extOf(name);
                    if (!isVideoExt(ext)) continue;
                    String bucket = cur.getString(2);
                    String key = (bucket == null ? "" : bucket) + "|" + baseName(name).toLowerCase(Locale.ROOT);
                    long vId = id;
                    long vSize = cur.getLong(4);
                    long vDur = cur.getLong(5);
                    long vTaken = cur.getLong(6);
                    if (vTaken <= 0) vTaken = cur.getLong(7) * 1000L;
                    int vw = cur.getInt(8);
                    int vh = cur.getInt(9);
                    LivePhotoItem item = map.get(key);
                    if (item != null && !item.isVideo && item.videoUri == null) {
                        item.videoId = vId;
                        item.videoUri = ContentUris.withAppendedId(vidBase, vId);
                        item.videoSize = vSize;
                        item.durationMs = vDur;
                    } else if (item == null) {
                        LivePhotoItem v = new LivePhotoItem();
                        v.id = vId;
                        v.title = name;
                        v.isVideo = true;
                        v.videoId = vId;
                        v.videoUri = ContentUris.withAppendedId(vidBase, vId);
                        v.videoSize = vSize;
                        v.durationMs = vDur;
                        v.imageSize = vSize;
                        v.width = vw;
                        v.height = vh;
                        v.dateMs = vTaken;
                        v.bucket = bucket;
                        byId.put(vId + 1000000000L, v);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "videos query failed", t);
        } finally {
            if (cur != null) cur.close();
        }

        result.addAll(byId.values());
        return result;
    }

    private static List<LivePhotoItem> scanImported(Context context) {
        List<LivePhotoItem> result = new ArrayList<LivePhotoItem>();
        File base = new File(context.getFilesDir(), "imported");
        File[] dirs = base.listFiles();
        if (dirs == null) return result;
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            File image = null;
            File video = null;
            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                String ext = extOf(f.getName());
                if (image == null && isImageExt(ext)) image = f;
                else if (video == null && isVideoExt(ext)) video = f;
            }
            if (image == null) continue;
            LivePhotoItem item = new LivePhotoItem();
            item.title = image.getName();
            item.imageFile = image;
            item.imageUri = Uri.fromFile(image);
            item.imageSize = image.length();
            item.dataPath = image.getAbsolutePath();
            item.imported = true;
            item.dateMs = image.lastModified();
            if (video != null) {
                item.videoFile = video;
                item.videoUri = Uri.fromFile(video);
                item.videoSize = video.length();
                item.dateMs = Math.max(item.dateMs, video.lastModified());
            }
            result.add(item);
        }
        return result;
    }

    private static List<LivePhotoItem> scanCommonDirs(Set<String> seen) {
        List<LivePhotoItem> out = new ArrayList<LivePhotoItem>();
        File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File[] roots = new File[]{dcim, pictures, downloads, movies};
        for (File r : roots) walk(r, 0, out, seen);
        return out;
    }

    private static void walk(File dir, int depth, List<LivePhotoItem> out, Set<String> seen) {
        if (dir == null || depth > 3) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable t) {
            return;
        }
        if (files == null) return;
        Map<String, File> imgs = new HashMap<String, File>();
        Map<String, File> vids = new HashMap<String, File>();
        List<File> subdirs = new ArrayList<File>();
        for (File f : files) {
            if (f == null) continue;
            try {
                if (f.isDirectory()) {
                    subdirs.add(f);
                    continue;
                }
                if (!f.isFile()) continue;
                String ext = extOf(f.getName());
                String base = baseName(f.getName()).toLowerCase(Locale.ROOT);
                if (isImageExt(ext)) imgs.put(base, f);
                else if (isVideoExt(ext)) vids.put(base, f);
            } catch (Throwable ignored) {
            }
        }
        for (Map.Entry<String, File> e : imgs.entrySet()) {
            File img = e.getValue();
            try {
                if (img.length() <= 0 || !img.canRead()) continue;
            } catch (Throwable ignored) {
                continue;
            }
            if (!seen.add(keyOf(img.getName(), img.length()))) continue;
            LivePhotoItem item = new LivePhotoItem();
            item.title = img.getName();
            item.imageFile = img;
            item.imageUri = Uri.fromFile(img);
            item.imageSize = img.length();
            item.dataPath = img.getAbsolutePath();
            item.imported = true;
            item.dateMs = img.lastModified();
            File vid = vids.get(e.getKey());
            if (vid != null) {
                try {
                    if (vid.length() > 0 && vid.canRead()) {
                        item.videoFile = vid;
                        item.videoUri = Uri.fromFile(vid);
                        item.videoSize = vid.length();
                    }
                } catch (Throwable ignored) {
                }
            }
            out.add(item);
        }
        for (File sub : subdirs) walk(sub, depth + 1, out, seen);
    }
}
