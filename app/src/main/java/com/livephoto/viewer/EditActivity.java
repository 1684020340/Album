package com.livephoto.viewer;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** 编辑：旋转、翻转、滤镜，并「存储为新照片」（苹果照片式编辑的简化版）。 */
public class EditActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LivePhotoItem item;
    private Bitmap source;
    private Bitmap current;
    private ImageView preview;
    private TextView filterLabel;
    private int rotation = 0;
    private boolean flip = false;
    private int filter = 0;
    private static final String[] FILTERS = new String[]{"原图", "黑白", "复古", "鲜艳", "冷色"};
    private View saveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long id = getIntent().getLongExtra("id", -1);
        long size = getIntent().getLongExtra("size", -1);
        for (LivePhotoItem it : Library.snapshot()) {
            if (it.id == id && (size <= 0 || it.imageSize == size)) {
                item = it;
                break;
            }
        }
        if (item == null) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout previewWrap = new FrameLayout(this);
        column.addView(previewWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewWrap.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildToolBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        load();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));

        IconView close = new IconView(this, IconView.CLOSE);
        close.setIconColor(Ui.TEXT);
        bar.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView title = Ui.text(this, "编辑", 16, Ui.TEXT, true);
        Ui.margins(title, this, 4, 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        saveBtn = Ui.text(this, "存储为新照片", 14, Ui.BLUE, true);
        saveBtn.setBackground(Ui.rounded(this, 0x1F0A84FF, 16));
        saveBtn.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        bar.addView(saveBtn);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        return bar;
    }

    private View buildToolBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 24));
        bar.setBackgroundColor(0xF2000000);

        bar.addView(tool("旋转", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rotation = (rotation + 90) % 360;
                render();
            }
        }));
        bar.addView(tool("翻转", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flip = !flip;
                render();
            }
        }));
        bar.addView(tool("滤镜", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter = (filter + 1) % FILTERS.length;
                if (filterLabel != null) filterLabel.setText(FILTERS[filter]);
                render();
                Toast.makeText(EditActivity.this, FILTERS[filter], Toast.LENGTH_SHORT).show();
            }
        }));
        filterLabel = Ui.text(this, FILTERS[0], 11, Ui.TEXT3, false);
        filterLabel.setGravity(Gravity.CENTER);
        Ui.margins(filterLabel, this, 0, 4, 0, 0);
        // 把滤镜名放在工具栏下方
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(0xF2000000);
        wrap.addView(bar);
        wrap.addView(filterLabel);
        return wrap;
    }

    private TextView tool(String text, View.OnClickListener l) {
        TextView t = Ui.text(this, text, 14, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 22), Ui.dp(this, 8), Ui.dp(this, 22), Ui.dp(this, 8));
        t.setOnClickListener(l);
        return t;
    }

    private void load() {
        final LivePhotoItem it = item;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = ThumbLoader.decode(EditActivity.this, it.imageUri, it.imageFile,
                        Math.max(Ui.screenWidth(EditActivity.this), Ui.screenHeight(EditActivity.this)));
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (b == null) {
                            Toast.makeText(EditActivity.this, "无法载入图片", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        source = b;
                        current = b;
                        preview.setImageBitmap(b);
                    }
                });
            }
        }).start();
    }

    private void render() {
        if (source == null) return;
        final Bitmap base = source;
        final int r = rotation;
        final boolean f = flip;
        final int flt = filter;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap out = base;
                if (r != 0 || f) {
                    Matrix m = new Matrix();
                    if (f) m.postScale(-1f, 1f);
                    if (r != 0) m.postRotate(r);
                    out = Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
                }
                if (flt != 0) {
                    out = applyFilter(out, flt);
                }
                final Bitmap result = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        current = result;
                        preview.setImageBitmap(result);
                    }
                });
            }
        }).start();
    }

    private static Bitmap applyFilter(Bitmap src, int kind) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix cm = new ColorMatrix();
        switch (kind) {
            case 1: // 黑白
                cm.setSaturation(0f);
                break;
            case 2: // 复古
                cm.set(new float[]{
                        0.393f, 0.769f, 0.189f, 0, 0,
                        0.349f, 0.686f, 0.168f, 0, 0,
                        0.272f, 0.534f, 0.131f, 0, 0,
                        0, 0, 0, 1, 0});
                break;
            case 3: // 鲜艳
                cm.setSaturation(1.7f);
                break;
            case 4: // 冷色
                cm.set(new float[]{
                        1.05f, 0, 0, 0, 0,
                        0, 1.02f, 0, 0, 0,
                        0, 0, 1.15f, 0, 0,
                        0, 0, 0, 1, 0});
                break;
            default:
                return src;
        }
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    private void save() {
        if (current == null || item == null) return;
        saveBtn.setEnabled(false);
        Toast.makeText(this, "正在存储…", Toast.LENGTH_SHORT).show();
        final Bitmap bmp = current;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String name = "小瞳心_" + System.currentTimeMillis() + ".jpg";
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    if (Build.VERSION.SDK_INT >= 29) {
                        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/小瞳心照片");
                        v.put(MediaStore.Images.Media.IS_PENDING, 1);
                    }
                    Uri uri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri != null) {
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                            os.close();
                        }
                        if (Build.VERSION.SDK_INT >= 29) {
                            ContentValues done = new ContentValues();
                            done.put(MediaStore.Images.Media.IS_PENDING, 0);
                            getContentResolver().update(uri, done, null, null);
                        }
                        ok = true;
                    }
                } catch (Throwable ignored) {
                }
                final boolean success = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        saveBtn.setEnabled(true);
                        Toast.makeText(EditActivity.this,
                                success ? "已存储为新照片" : "存储失败", Toast.LENGTH_SHORT).show();
                        if (success) finish();
                    }
                });
            }
        }).start();
    }
}
