package com.livephoto.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** 分享：按设置做「安全分享」（去掉 EXIF）与 HEIF→JPEG 转换。 */
public final class ShareUtil {
    private ShareUtil() {
    }

    /** 需要转换时返回新的 content:// URI，否则返回原 URI。 */
    public static Uri prepare(Context ctx, LivePhotoItem it, boolean isVideo) {
        if (isVideo) {
            if (it.videoFile != null) return LocalFileProvider.uriFor(ctx, it.videoFile);
            return it.videoUri;
        }
        boolean heic = it.title != null && it.title.toLowerCase(Locale.ROOT).endsWith(".heic");
        boolean needConvert = Prefs.safeShare(ctx) || (heic && Prefs.heifToJpeg(ctx));
        if (!needConvert) {
            if (it.imageFile != null) return LocalFileProvider.uriFor(ctx, it.imageFile);
            return it.imageUri;
        }
        try {
            Bitmap b = ThumbLoader.decode(ctx, it.imageUri, it.imageFile,
                    Math.max(Ui.screenWidth(ctx), Ui.screenHeight(ctx)));
            if (b == null) throw new RuntimeException("decode failed");
            File dir = new File(ctx.getFilesDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "share_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(out);
            b.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            return LocalFileProvider.uriFor(ctx, out);
        } catch (Throwable t) {
            if (it.imageFile != null) return LocalFileProvider.uriFor(ctx, it.imageFile);
            return it.imageUri;
        }
    }
}
