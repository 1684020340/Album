package com.livephoto.viewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** 只为应用内导入的文件提供 content:// 分享能力（Android 7+ 不允许分享 file://）。 */
public class LocalFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.livephoto.viewer.files";

    public static Uri uriFor(Context c, File f) {
        File imported = new File(c.getFilesDir(), "imported");
        File share = new File(c.getFilesDir(), "share");
        String path = f.getAbsolutePath();
        if (path.startsWith(imported.getAbsolutePath() + "/")) {
            String rel = path.substring(imported.getAbsolutePath().length() + 1);
            return new Uri.Builder().scheme("content").authority(AUTHORITY)
                    .appendPath("imported").appendEncodedPath(rel).build();
        }
        if (path.startsWith(share.getAbsolutePath() + "/")) {
            String rel = path.substring(share.getAbsolutePath().length() + 1);
            return new Uri.Builder().scheme("content").authority(AUTHORITY)
                    .appendPath("share").appendEncodedPath(rel).build();
        }
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath("imported").appendEncodedPath(f.getName()).build();
    }

    private File baseDir(String sub) {
        return new File(getContext().getFilesDir(), sub);
    }

    private File resolve(Uri uri) {
        try {
            List<String> segs = uri.getPathSegments();
            if (segs.size() < 2) return null;
            String root = segs.get(0);
            if (!"imported".equals(root) && !"share".equals(root)) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < segs.size(); i++) {
                if (i > 1) sb.append('/');
                sb.append(segs.get(i));
            }
            File base = baseDir(root).getCanonicalFile();
            File f = new File(base, sb.toString()).getCanonicalFile();
            if (!f.getPath().startsWith(base.getPath() + File.separator)) return null;
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null || !f.isFile()) throw new FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        File f = resolve(uri);
        if (f == null) return null;
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null) {
            if (ext.equals("mov")) mime = "video/quicktime";
            else if (ext.equals("heic")) mime = "image/heic";
            else if (ext.equals("heif")) mime = "image/heif";
            else mime = "application/octet-stream";
        }
        return mime;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
