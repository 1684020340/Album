package com.livephoto.viewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 影集：仿小米相册——那年今日、常用、相册；底部 照片/影集/搜索。 */
public class AlbumsActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<LivePhotoItem> items = new ArrayList<LivePhotoItem>();
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.L_BG);
        setContentView(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(content);

        column.addView(NavBar.build(this, 1), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        load();
    }

    private void load() {
        List<LivePhotoItem> snap = Library.snapshot();
        if (!snap.isEmpty()) {
            items = snap;
            build();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Library.snapshot().isEmpty()) {
                    try {
                        Library.refresh(AlbumsActivity.this);
                    } catch (Throwable ignored) {
                    }
                }
                final List<LivePhotoItem> s2 = Library.snapshot();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        items = s2;
                        build();
                    }
                });
            }
        }).start();
    }

    private void build() {
        if (content == null) return;
        content.removeAllViews();
        buildHero();
        buildCommon();
        buildAlbumGrid();
    }

    // ---------- 那年今日 ----------
    private void buildHero() {
        final List<LivePhotoItem> sameDay = sameDayItems();
        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setBackground(Ui.rounded(this, 0xFF222222, 18));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220));
        hlp.setMargins(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), 0);
        content.addView(hero, hlp);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LivePhotoItem cover = Prefs.yearAgo(this) && !sameDay.isEmpty()
                ? sameDay.get(0) : (items.isEmpty() ? null : items.get(0));
        if (cover != null) ThumbLoader.get(this).loadThumb(cover, Ui.dp(this, 480), bg);

        View scrim = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xC9000000, 0x22000000, 0x00000000});
        scrim.setBackground(g);
        hero.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        tlp.setMargins(Ui.dp(this, 18), 0, 0, Ui.dp(this, 16));
        hero.addView(texts, tlp);
        TextView t1 = Ui.text(this, Prefs.yearAgo(this) ? "那年今日" : "今天", 28, 0xFFFFFFFF, true);
        texts.addView(t1);
        TextView t2 = Ui.text(this, monthDayText(), 18, 0xCCFFFFFF, false);
        Ui.margins(t2, this, 0, 4, 0, 0);
        texts.addView(t2);
        if (!sameDay.isEmpty()) {
            TextView t3 = Ui.text(this, sameDay.size() + " 张过往今日的照片", 12, 0x99FFFFFF, false);
            Ui.margins(t3, this, 0, 6, 0, 0);
            texts.addView(t3);
        }

        IconView more = new IconView(this, IconView.MORE);
        more.setIconColor(0xFFFFFFFF);
        more.setBackground(Ui.glassDark(this, 20));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                Ui.dp(this, 40), Ui.dp(this, 40));
        mlp.gravity = Gravity.TOP | Gravity.RIGHT;
        mlp.setMargins(0, Ui.dp(this, 14), Ui.dp(this, 14), 0);
        hero.addView(more, mlp);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        hero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMoment();
            }
        });
    }

    private String monthDayText() {
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        return m + "/" + d;
    }

    private List<LivePhotoItem> sameDayItems() {
        List<LivePhotoItem> out = new ArrayList<LivePhotoItem>();
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        for (LivePhotoItem it : items) {
            if (it.isVideo) continue;
            c.setTimeInMillis(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
            if (c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                    && c.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
                    && c.get(Calendar.YEAR) < now.get(Calendar.YEAR)) {
                out.add(it);
            }
        }
        return out;
    }

    private void showMenu() {
        String[] actions = new String[]{"地图相册", "释放空间", "最近删除", "设置"};
        new AlertDialog.Builder(this)
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            Toast.makeText(AlbumsActivity.this,
                                    "地图相册需要地图服务，当前机型未提供", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            showSpace();
                        } else if (which == 2) {
                            Toast.makeText(AlbumsActivity.this,
                                    "删除的照片在系统相册的「最近删除」中", Toast.LENGTH_SHORT).show();
                        } else {
                            startActivity(new Intent(AlbumsActivity.this, SettingsActivity.class));
                        }
                    }
                })
                .show();
    }

    private void showSpace() {
        long total = 0;
        int photos = 0;
        int videos = 0;
        for (LivePhotoItem it : items) {
            long s = it.isVideo ? it.videoSize : Math.max(it.imageSize, it.videoSize);
            total += s;
            if (it.isVideo) videos++;
            else photos++;
        }
        new AlertDialog.Builder(this)
                .setTitle("释放空间")
                .setMessage("共 " + photos + " 张照片、" + videos + " 个视频，占用 "
                        + Ui.fmtSize(total) + "。\n\n可尝试：删除重复照片、清理截图、清理已隐藏照片。")
                .setPositiveButton("好", null)
                .show();
    }

    // ---------- 常用 ----------
    private void buildCommon() {
        addSection("常用");
        int all = items.size();
        int camera = 0;
        int shots = 0;
        int videos = 0;
        List<LivePhotoItem> cameraItems = new ArrayList<LivePhotoItem>();
        List<LivePhotoItem> shotItems = new ArrayList<LivePhotoItem>();
        List<LivePhotoItem> videoItems = new ArrayList<LivePhotoItem>();
        for (LivePhotoItem it : items) {
            if (it.isVideo) {
                videos++;
                videoItems.add(it);
                continue;
            }
            if (isScreenshot(it)) {
                shots++;
                shotItems.add(it);
            }
            String b = it.bucketName == null ? "" : it.bucketName;
            if (b.contains("相机") || b.toLowerCase().contains("camera")) {
                camera++;
                cameraItems.add(it);
            }
        }
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        content.addView(row1);
        row1.addView(commonCard("全部照片", all, items, 0, IconView.GRID));
        row1.addView(commonCard("相机", camera, cameraItems, 7, IconView.FOLDER));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        content.addView(row2);
        row2.addView(commonCard("截屏录屏", shots, shotItems, 5, IconView.FOLDER));
        row2.addView(commonCard("视频", videos, videoItems, 3, IconView.PLAY));
    }

    private View commonCard(String title, int count, List<LivePhotoItem> list,
                            final int mode, int icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.lightCard(this, 16));
        card.setElevation(Ui.dp(this, 1));
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        card.setLayoutParams(lp);

        LinearLayout mosaic = new LinearLayout(this);
        mosaic.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1f);
                ilp.setMargins(1, 1, 1, 1);
                row.addView(iv, ilp);
                int idx = r * 2 + c;
                if (idx < list.size()) {
                    ThumbLoader.get(this).loadThumb(list.get(idx), Ui.dp(this, 70), iv);
                }
            }
            mosaic.addView(row);
        }
        card.addView(mosaic);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        Ui.margins(footer, this, 2, 9, 2, 0);
        IconView iv = new IconView(this, icon);
        iv.setIconColor(Ui.L_TEXT);
        footer.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 15), Ui.dp(this, 15)));
        TextView t = Ui.text(this, title, 13, Ui.L_TEXT, false);
        Ui.margins(t, this, 5, 0, 0, 0);
        footer.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cnt = Ui.text(this, count + "", 12, Ui.L_TEXT2, false);
        footer.addView(cnt);
        card.addView(footer);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMode(mode);
            }
        });
        return card;
    }

    // ---------- 相册 ----------
    private void buildAlbumGrid() {
        addSection("相册");
        List<Album> albums = new ArrayList<Album>();
        if (Prefs.cardsAlbum(this)) {
            albums.add(new Album("我的卡证", Cards.count(this), null, 6));
        }
        albums.add(new Album("个人收藏", Favs.count(this), null, 2));
        albums.add(new Album("已隐藏", Hidden.count(this), null, 4));
        albums.add(new Album("视频", countVideos(), videoItems(), 3));

        LinkedHashMap<String, List<LivePhotoItem>> byBucket =
                new LinkedHashMap<String, List<LivePhotoItem>>();
        for (LivePhotoItem it : items) {
            String b = it.bucketName;
            if (b == null || b.trim().isEmpty() || b.equalsIgnoreCase("Pictures")) continue;
            if (isScreenshot(it)) continue;
            if (it.isVideo) continue;
            List<LivePhotoItem> l = byBucket.get(b);
            if (l == null) {
                l = new ArrayList<LivePhotoItem>();
                byBucket.put(b, l);
            }
            l.add(it);
        }
        List<Map.Entry<String, List<LivePhotoItem>>> entries =
                new ArrayList<Map.Entry<String, List<LivePhotoItem>>>(byBucket.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<String, List<LivePhotoItem>>>() {
            @Override
            public int compare(Map.Entry<String, List<LivePhotoItem>> a,
                               Map.Entry<String, List<LivePhotoItem>> b) {
                return Integer.compare(b.getValue().size(), a.getValue().size());
            }
        });
        int added = 0;
        for (Map.Entry<String, List<LivePhotoItem>> e : entries) {
            if (added++ >= 8) break;
            albums.add(new Album(e.getKey(), e.getValue().size(), e.getValue(), 7));
        }

        int perRow = 3;
        LinearLayout row = null;
        for (int i = 0; i < albums.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
                content.addView(row);
            }
            row.addView(albumCard(albums.get(i)));
        }
    }

    private static class Album {
        String name;
        int count;
        List<LivePhotoItem> cover;
        int mode;

        Album(String n, int c, List<LivePhotoItem> cv, int m) {
            name = n;
            count = c;
            cover = cv;
            mode = m;
        }
    }

    private int countVideos() {
        int n = 0;
        for (LivePhotoItem it : items) if (it.isVideo) n++;
        return n;
    }

    private List<LivePhotoItem> videoItems() {
        List<LivePhotoItem> l = new ArrayList<LivePhotoItem>();
        for (LivePhotoItem it : items) if (it.isVideo) l.add(it);
        return l;
    }

    private View albumCard(final Album a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        card.setLayoutParams(lp);

        FrameLayout coverWrap = new FrameLayout(this);
        coverWrap.setBackground(Ui.rounded(this, 0xFFE5E5EA, 14));
        coverWrap.setClipToOutline(true);
        card.addView(coverWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 104)));
        LivePhotoItem cover = (a.cover != null && !a.cover.isEmpty()) ? a.cover.get(0) : null;
        if (cover != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            coverWrap.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ThumbLoader.get(this).loadThumb(cover, Ui.dp(this, 130), iv);
        } else {
            IconView icon = new IconView(this, IconView.FOLDER);
            icon.setIconColor(Ui.L_TEXT3);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                    Ui.dp(this, 34), Ui.dp(this, 34));
            ilp.gravity = Gravity.CENTER;
            coverWrap.addView(icon, ilp);
        }
        TextView name = Ui.text(this, a.name, 13, Ui.L_TEXT, false);
        Ui.margins(name, this, 4, 7, 0, 0);
        card.addView(name);
        TextView cnt = Ui.text(this, a.count + "", 11, Ui.L_TEXT2, false);
        Ui.margins(cnt, this, 4, 2, 0, 0);
        card.addView(cnt);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (a.mode == 7) {
                    Intent i = new Intent(AlbumsActivity.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("mode", 7);
                    i.putExtra("bucket", a.name);
                    startActivity(i);
                    finish();
                } else {
                    openMode(a.mode);
                }
            }
        });
        return card;
    }

    private void openMoment() {
        Calendar c = Calendar.getInstance();
        int key = (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
        Intent i = new Intent(AlbumsActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("mode", 0);
        i.putExtra("moment", key);
        startActivity(i);
    }

    private void openMode(int mode) {
        Intent i = new Intent(AlbumsActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("mode", mode);
        startActivity(i);
        finish();
    }

    private boolean isScreenshot(LivePhotoItem it) {
        String n = it.title == null ? "" : it.title.toLowerCase();
        String b = it.bucketName == null ? "" : it.bucketName.toLowerCase();
        return n.startsWith("screenshot") || n.contains("截图") || b.contains("截屏")
                || b.contains("截图") || b.contains("screenshot");
    }

    private void addSection(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 6));
        row.addView(Ui.text(this, title, 19, Ui.L_TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, "›", 20, Ui.L_TEXT3, false));
        content.addView(row);
    }
}
