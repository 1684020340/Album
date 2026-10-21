package com.livephoto.viewer;

import android.net.Uri;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 一张照片；如果是实况照片，则附带动态视频（外部配对文件或 JPEG 内嵌）。 */
public class LivePhotoItem {
    public long id;
    public long videoId;
    public String title;
    public Uri imageUri;
    public Uri videoUri;
    public File imageFile;
    public File videoFile;
    public String dataPath;
    public long dateMs;
    public long dateAdded;
    public long imageSize;
    public long videoSize;
    public int width;
    public int height;
    public long durationMs;
    public boolean imported;
    /** 是否为「内嵌动态照片」（JPEG 里附加 MP4，MVIMG/小米/三星等）。 */
    public boolean motionPhoto;
    /** 是否已经检测过。 */
    public boolean motionChecked;
    /** 视频（而非照片）。 */
    public boolean isVideo;
    public String bucket;
    public String bucketName;

    /** 是否是实况照片（有配对的视频，或 JPEG 内嵌视频）。 */
    public boolean live() {
        return !isVideo && (videoUri != null || videoFile != null || motionPhoto);
    }

    public String durationText() {
        if (durationMs <= 0) return "";
        long total = Math.round(durationMs / 1000.0);
        long m = total / 60;
        long s = total % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    public String artKey() {
        return (title == null ? "" : title) + "#" + dateMs;
    }

    public String dateText() {
        if (dateMs <= 0) return "未知时间";
        SimpleDateFormat f = new SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA);
        return f.format(new Date(dateMs));
    }

    public String infoText() {
        StringBuilder sb = new StringBuilder();
        sb.append("名称：").append(title == null ? "未知" : title).append('\n');
        sb.append("时间：").append(dateText()).append('\n');
        if (width > 0 && height > 0) {
            sb.append("尺寸：").append(width).append(" × ").append(height).append('\n');
        }
        sb.append("大小：").append(Ui.fmtSize(imageSize)).append('\n');
        sb.append("类型：").append(isVideo ? "视频" : (live() ? "实况照片" : "普通照片")).append('\n');
        if (motionPhoto) {
            sb.append("动态来源：照片内嵌视频\n");
        } else if (videoUri != null || videoFile != null) {
            sb.append("动态时长：").append(Ui.fmtDuration(durationMs)).append('\n');
            sb.append("动态大小：").append(Ui.fmtSize(videoSize)).append('\n');
        }
        sb.append("位置：").append(imported ? "本地导入" : "系统相册");
        return sb.toString();
    }
}
