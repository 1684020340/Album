package com.livephoto.viewer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.FileInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 导入文件：
 * - 单个 .livp / .zip（内含实况照片的 图片 + MOV）→ 解压；
 * - 图片、视频（可多选）→ 按文件名配对后归档到应用私有目录。
 */
public final class Importer {

    private Importer() {
    }

    public static int importUris(Context ctx, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return 0;
        int count = 0;
        for (Uri u : uris) {
            if (u == null) continue;
            String name = queryName(ctx, u);
            if (name == null || name.trim().isEmpty()) name = "imported";
            String ext = extOf(name);
            try {
                if (ext.equals("livp") || ext.equals("zip")) {
                    File dir = newDir(ctx, baseName(name));
                    extractZip(ctx, u, dir);
                    File[] ins = dir.listFiles();
                    Log.d("LivePhotoImport", "zip " + name + " -> " + (ins == null ? 0 : ins.length) + " files");
                    count++;
                } else if (isImageExt(ext) || isVideoExt(ext)) {
                    File dir = dirForType(ctx, baseName(name), isImageExt(ext));
                    copy(ctx, u, new File(dir, safeName(name)));
                    Log.d("LivePhotoImport", "file " + name + " -> " + dir.getName());
                    count++;
                }
            } catch (Throwable t) {
                Log.w("LivePhotoImport", "import failed: " + u, t);
            }
        }
        return count;
    }

    private static File baseDir(Context ctx) {
        File f = new File(ctx.getFilesDir(), "imported");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    private static File newDir(Context ctx, String name) {
        File dir = new File(baseDir(ctx), safeName(name) + "_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    /** 同名图片/视频落到同一个目录，方便按文件名配对。 */
    private static File dirForType(Context ctx, String base, boolean image) {
        File dir = new File(baseDir(ctx), safeName(base));
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String ext = extOf(f.getName());
                    if (image && isImageExt(ext)) {
                        return newDir(ctx, base);
                    }
                    if (!image && isVideoExt(ext)) {
                        return newDir(ctx, base);
                    }
                }
            }
        }
        dir.mkdirs();
        return dir;
    }

    private static String queryName(Context ctx, Uri uri) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last;
    }

    private static InputStream open(Context ctx, Uri uri) throws Exception {
        if (uri == null) throw new java.io.FileNotFoundException("null uri");
        if ("file".equals(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) throw new java.io.FileNotFoundException(String.valueOf(uri));
        return in;
    }

    private static void copy(Context ctx, Uri uri, File dst) throws Exception {
        InputStream in = open(ctx, uri);
        if (in == null) return;
        FileOutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try {
                out.close();
            } catch (Throwable ignored) {
            }
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void extractZip(Context ctx, Uri uri, File dir) throws Exception {
        InputStream in = open(ctx, uri);
        if (in == null) return;
        ZipInputStream zis = new ZipInputStream(in);
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                String base = baseName(n);
                String ext = extOf(n);
                if (!isImageExt(ext) && !isVideoExt(ext)) continue;
                File out = new File(dir, safeName(base + "." + ext));
                if (out.exists()) continue;
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = zis.read(buf)) > 0) fos.write(buf, 0, r);
                } finally {
                    fos.close();
                }
            }
        } finally {
            try {
                zis.close();
            } catch (Throwable ignored) {
            }
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static String safeName(String name) {
        if (name == null || name.isEmpty()) return "file";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "");
        if (name.length() > 80) {
            String ext = extOf(name);
            name = name.substring(0, 60) + "." + ext;
        }
        return name.isEmpty() ? "file" : name;
    }

    static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static boolean isImageExt(String ext) {
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("heic")
                || ext.equals("heif") || ext.equals("png") || ext.equals("webp");
    }

    static boolean isVideoExt(String ext) {
        return ext.equals("mov") || ext.equals("mp4") || ext.equals("m4v") || ext.equals("3gp");
    }
}
