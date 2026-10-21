package com.livephoto.viewer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.database.ContentObserver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 照片（时间线）：仿小米相册的浅色时间线 + 多选/相似优选。 */
public class MainActivity extends Activity {
    private static final int REQ_PERM = 101;
    private static final int REQ_PICK = 102;
    private static final int REQ_DELETE = 103;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<LivePhotoItem> allItems = new ArrayList<LivePhotoItem>();
    private List<LivePhotoItem> items = new ArrayList<LivePhotoItem>();
    private final List<Row> rows = new ArrayList<Row>();
    private int mode = 0;
    private int momentFilter = 0;
    private String bucketFilter = null;
    private int yearFilter = 0;
    private int monthFilter = 0;
    private boolean collageAdded = false;
    private TextView stickyHeader;
    private int lastFirstVisible = 0;
    private boolean navHidden = false;
    private int cols = 3;
    private int cellSize;
    private int gap;
    private ListView listView;
    private RowAdapter adapter;
    private TextView subtitleView;
    private TextView selectBtn;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptySub;
    private LinearLayout emptyActions;
    private View actionBar;
    private View navBar;
    private boolean loading = false;
    private long lastRefresh = 0;
    private Cell activeCell;
    private boolean scanStarted = false;
    private boolean selecting = false;
    private final Set<LivePhotoItem> selected = new HashSet<LivePhotoItem>();
    private final Set<LivePhotoItem> bestPicks = new HashSet<LivePhotoItem>();
    private ContentObserver mediaObserver;
    private final Runnable observerRefresh = new Runnable() {
        @Override
        public void run() {
            if (loading) {
                ui.postDelayed(this, 800);
                return;
            }
            reload();
        }
    };

    static class Row {
        int type; // 0 header, 1 photos
        String header;
        LivePhotoItem[] items = new LivePhotoItem[4];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gap = Ui.dp(this, 2);
        mode = getIntent().getIntExtra("mode", 0);
        momentFilter = getIntent().getIntExtra("moment", 0);
        bucketFilter = getIntent().getStringExtra("bucket");
        recomputeCols();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFFFFFFF);
        setContentView(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout contentWrap = new FrameLayout(this);
        column.addView(contentWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listView.setBackgroundColor(0xFFFFFFFF);
        listView.setVerticalScrollBarEnabled(false);
        contentWrap.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = buildEmptyView();
        contentWrap.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stickyHeader = Ui.text(this, "", 15, Ui.L_TEXT, true);
        stickyHeader.setBackgroundColor(0xFFFFFFFF);
        stickyHeader.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        FrameLayout.LayoutParams shlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shlp.gravity = Gravity.TOP;
        contentWrap.addView(stickyHeader, shlp);
        stickyHeader.setVisibility(View.GONE);

        actionBar = buildActionBar();
        column.addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionBar.setVisibility(View.GONE);

        navBar = NavBar.build(this, 0);
        column.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) stopActivePreview();
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
                updateStickyHeader(firstVisibleItem);
                if (firstVisibleItem > lastFirstVisible + 2) hideNavBar();
                else if (firstVisibleItem < lastFirstVisible - 2) showNavBar();
                lastFirstVisible = firstVisibleItem;
            }
        });

        handleIntent(getIntent());
        subtitleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loading) {
                    Library.cancelRefresh();
                    loading = false;
                    allItems = Library.snapshot();
                    sortItems();
                    applyFilter();
                    Toast.makeText(MainActivity.this, "已取消扫描", Toast.LENGTH_SHORT).show();
                } else if (hasPartialAccess() && !hasPermission()) {
                    requestPermission();
                }
            }
        });
        if (hasPermission() || hasPartialAccess()) reload();
        else showPermissionIntro();
    }

    private void registerMediaObserver() {
        if (mediaObserver != null) return;
        mediaObserver = new ContentObserver(ui) {
            @Override
            public void onChange(boolean selfChange) {
                ui.removeCallbacks(observerRefresh);
                ui.postDelayed(observerRefresh, 800);
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
            getContentResolver().registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        } catch (Throwable ignored) {
        }
    }

    private void unregisterMediaObserver() {
        if (mediaObserver == null) return;
        try {
            getContentResolver().unregisterContentObserver(mediaObserver);
        } catch (Throwable ignored) {
        }
        mediaObserver = null;
        ui.removeCallbacks(observerRefresh);
    }

    private void recomputeCols() {
        int g = Prefs.gridSize(this);
        cols = g == 0 ? 5 : (g == 2 ? 3 : 4);
        cellSize = (Ui.screenWidth(this) - gap * (cols + 1)) / cols;
    }

    private void updateStickyHeader(int firstVisible) {
        if (stickyHeader == null) return;
        if (rows.isEmpty() || firstVisible < 0 || firstVisible >= rows.size()) {
            stickyHeader.setVisibility(View.GONE);
            return;
        }
        String header = null;
        for (int i = Math.min(firstVisible, rows.size() - 1); i >= 0; i--) {
            if (rows.get(i).type == 0) {
                header = rows.get(i).header;
                break;
            }
        }
        if (header == null) {
            stickyHeader.setVisibility(View.GONE);
        } else {
            stickyHeader.setText(header);
            stickyHeader.setVisibility(View.VISIBLE);
        }
    }

    private void hideNavBar() {
        if (navBar == null || navHidden || selecting) return;
        navHidden = true;
        navBar.animate().translationY(navBar.getHeight()).alpha(0f).setDuration(180).start();
    }

    private void showNavBar() {
        if (navBar == null || !navHidden) return;
        navHidden = false;
        navBar.animate().translationY(0f).alpha(1f).setDuration(180).start();
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 10));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(this, "照片", 32, Ui.L_TEXT, true));
        subtitleView = Ui.text(this, "读取中…", 12, Ui.L_TEXT2, false);
        Ui.margins(subtitleView, this, 0, 2, 0, 0);
        titles.addView(subtitleView);
        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        selectBtn = Ui.text(this, "取消", 14, Ui.L_BLUE, false);
        selectBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        selectBtn.setVisibility(View.GONE);
        row.addView(selectBtn);
        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSelect();
            }
        });

        IconView filter = new IconView(this, IconView.SLIDERS);
        filter.setIconColor(Ui.L_TEXT);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
        flp.setMargins(Ui.dp(this, 4), 0, 0, 0);
        row.addView(filter, flp);
        filter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        IconView more = new IconView(this, IconView.MORE);
        more.setIconColor(Ui.L_TEXT);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
        mlp.setMargins(Ui.dp(this, 6), 0, 0, 0);
        row.addView(more, mlp);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu();
            }
        });
        return row;
    }

    private void showMenu() {
        final String[] actions = new String[]{
                "排序：拍摄时间", "排序：添加时间", "排序：最早在前",
                "筛选：全部照片", "筛选：实况照片", "筛选：个人收藏",
                "筛选：视频", "筛选：屏幕截图", "筛选：已隐藏",
                "按年份筛选…", "按月份筛选…", "清除日期筛选",
                "视图：密集（5列）", "视图：统一（4列）", "视图：大拼贴（3列）",
                Prefs.hideScreenshot(this) ? "隐藏截屏：开" : "隐藏截屏：关",
                Prefs.hideVideo(this) ? "隐藏视频：开" : "隐藏视频：关",
                "设置", "中国移动云盘", "刷新", "iOS 风格相册"
        };
        new AlertDialog.Builder(this)
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String a = actions[which];
                        if (a.equals("排序：拍摄时间")) { Prefs.setSortMode(MainActivity.this, 0); reload(); }
                        else if (a.equals("排序：添加时间")) { Prefs.setSortMode(MainActivity.this, 1); reload(); }
                        else if (a.equals("排序：最早在前")) { Prefs.setSortMode(MainActivity.this, 2); reload(); }
                        else if (a.equals("筛选：全部照片")) setMode(0);
                        else if (a.equals("筛选：实况照片")) setMode(1);
                        else if (a.equals("筛选：个人收藏")) setMode(2);
                        else if (a.equals("筛选：视频")) setMode(3);
                        else if (a.equals("筛选：屏幕截图")) setMode(5);
                        else if (a.equals("筛选：已隐藏")) setMode(4);
                        else if (a.equals("按年份筛选…")) pickYear();
                        else if (a.equals("按月份筛选…")) pickMonth();
                        else if (a.equals("清除日期筛选")) { yearFilter = 0; monthFilter = 0; applyFilter(); }
                        else if (a.startsWith("视图：")) {
                            Prefs.setGridSize(MainActivity.this,
                                    a.contains("密集") ? 0 : (a.contains("统一") ? 1 : 2));
                            recomputeCols();
                            adapter = new RowAdapter();
                            listView.setAdapter(adapter);
                            applyFilter();
                        }
                        else if (a.startsWith("隐藏截屏")) {
                            Prefs.setHideScreenshot(MainActivity.this, !Prefs.hideScreenshot(MainActivity.this));
                            applyFilter();
                        }
                        else if (a.startsWith("隐藏视频")) {
                            Prefs.setHideVideo(MainActivity.this, !Prefs.hideVideo(MainActivity.this));
                            applyFilter();
                        }
                        else if (a.equals("设置")) startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        else if (a.equals("中国移动云盘")) startActivity(new Intent(MainActivity.this, CloudActivity.class));
                        else if (a.equals("iOS 风格相册")) startActivity(new Intent(MainActivity.this, HtmlAlbumActivity.class));
                        else refreshFull();
                    }
                }).show();
    }

    private void pickYear() {
        final List<Integer> years = new ArrayList<Integer>();
        Calendar c = Calendar.getInstance();
        for (LivePhotoItem it : allItems) {
            if (it.dateMs <= 0) continue;
            c.setTimeInMillis(it.dateMs);
            int y = c.get(Calendar.YEAR);
            if (!years.contains(y)) years.add(y);
        }
        Collections.sort(years, Collections.reverseOrder());
        if (years.isEmpty()) return;
        String[] labels = new String[years.size()];
        for (int i = 0; i < years.size(); i++) labels[i] = years.get(i) + " 年";
        new AlertDialog.Builder(this)
                .setTitle("按年份筛选")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        yearFilter = years.get(which);
                        monthFilter = 0;
                        applyFilter();
                        listView.setSelection(0);
                    }
                }).show();
    }

    private void pickMonth() {
        final List<int[]> keys = new ArrayList<int[]>();
        Calendar c = Calendar.getInstance();
        for (LivePhotoItem it : allItems) {
            if (it.dateMs <= 0) continue;
            c.setTimeInMillis(it.dateMs);
            int y = c.get(Calendar.YEAR);
            int m = c.get(Calendar.MONTH) + 1;
            boolean found = false;
            for (int[] k : keys) if (k[0] == y && k[1] == m) { found = true; break; }
            if (!found) keys.add(new int[]{y, m});
        }
        Collections.sort(keys, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return (b[0] * 100 + b[1]) - (a[0] * 100 + a[1]);
            }
        });
        if (keys.isEmpty()) return;
        String[] labels = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) labels[i] = keys.get(i)[0] + " 年 " + keys.get(i)[1] + " 月";
        new AlertDialog.Builder(this)
                .setTitle("按月份筛选")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        yearFilter = keys.get(which)[0];
                        monthFilter = keys.get(which)[1];
                        applyFilter();
                        listView.setSelection(0);
                    }
                }).show();
    }

    private void showMoreMenu() {
        String[] actions = new String[]{
                selecting ? "退出多选" : "选择照片",
                "设置", "中国移动云盘", "刷新图库", "iOS 风格相册"
        };
        new AlertDialog.Builder(this)
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            if (selecting) exitSelect();
                            else enterSelect();
                        } else if (which == 1) {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        } else if (which == 2) {
                            startActivity(new Intent(MainActivity.this, CloudActivity.class));
                        } else if (which == 4) {
                            startActivity(new Intent(MainActivity.this, HtmlAlbumActivity.class));
                        } else {
                            refreshFull();
                        }
                    }
                }).show();
    }

    private void setMode(int m) {
        mode = m;
        momentFilter = 0;
        bucketFilter = null;
        yearFilter = 0;
        monthFilter = 0;
        applyFilter();
        listView.setSelection(0);
    }

    // ---------- 选择 ----------

    private void enterSelect() {
        selecting = true;
        selected.clear();
        bestPicks.clear();
        selectBtn.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        navBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
        updateSelectionUI();
    }

    private void exitSelect() {
        selecting = false;
        selected.clear();
        bestPicks.clear();
        selectBtn.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
        navBar.setVisibility(View.VISIBLE);
        navBar.setTranslationY(0f);
        navBar.setAlpha(1f);
        navHidden = false;
        adapter.notifyDataSetChanged();
        updateSubtitle();
    }

    private void updateSelectionUI() {
        if (selecting) {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText("已选择 " + selected.size() + " 张");
        } else {
            updateSubtitle();
        }
    }

    private View buildActionBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 8));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(Ui.lightPill(this, 26));
        bar.setElevation(Ui.dp(this, 8));
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(action("分享", Ui.L_BLUE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSelected();
            }
        }));
        bar.addView(action("收藏", Ui.L_BLUE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                favSelected();
            }
        }));
        bar.addView(action("删除", 0xFFE5484D, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelected();
            }
        }));
        bar.addView(action("全选", Ui.L_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setItems(new String[]{"全选（相似优选）", "全选本屏", "按当前日期全选"},
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int which) {
                                        if (which == 0) selectAllSmart();
                                        else if (which == 1) selectAllVisible();
                                        else selectAllByDate();
                                    }
                                }).show();
            }
        }));
        return wrap;
    }

    private TextView action(String text, int color, View.OnClickListener l) {
        TextView t = Ui.text(this, text, 14, color, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));
        t.setOnClickListener(l);
        return t;
    }

    private void selectAllVisible() {
        int first = listView.getFirstVisiblePosition();
        int last = listView.getLastVisiblePosition();
        for (int i = Math.max(0, first); i <= Math.min(rows.size() - 1, last); i++) {
            Row r = rows.get(i);
            if (r.type != 1) continue;
            for (LivePhotoItem it : r.items) if (it != null) selected.add(it);
        }
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void selectAllByDate() {
        int first = listView.getFirstVisiblePosition();
        int start = -1;
        for (int i = Math.min(first, rows.size() - 1); i >= 0; i--) {
            if (rows.get(i).type == 0) { start = i + 1; break; }
        }
        if (start < 0) start = 0;
        for (int i = start; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.type == 0) break;
            if (r.type == 1 || r.type == 2) {
                for (LivePhotoItem it : r.items) if (it != null) selected.add(it);
            }
        }
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    /** 全选；开启「相似照片优选」时每组相似照片只选一张最优，并打星。 */
    private void selectAllSmart() {
        selected.clear();
        bestPicks.clear();
        if (!Prefs.similarBest(this)) {
            selected.addAll(items);
        } else {
            List<LivePhotoItem> sorted = new ArrayList<LivePhotoItem>(items);
            Collections.sort(sorted, new Comparator<LivePhotoItem>() {
                @Override
                public int compare(LivePhotoItem a, LivePhotoItem b) {
                    return Long.compare(a.dateMs, b.dateMs);
                }
            });
            LivePhotoItem best = null;
            long bestTime = 0;
            for (LivePhotoItem it : sorted) {
                boolean similar = best != null
                        && Math.abs(it.dateMs - bestTime) <= 3000
                        && !it.isVideo && !best.isVideo
                        && it.width > 0 && best.width > 0
                        && Math.abs(it.width - best.width) <= Math.max(4, best.width / 50);
                if (similar) {
                    if (it.imageSize > best.imageSize) {
                        selected.remove(best);
                        best = it;
                        bestPicks.remove(best);
                    }
                    bestPicks.add(best);
                    selected.add(best);
                } else {
                    best = it;
                    bestTime = it.dateMs;
                    selected.add(it);
                }
            }
        }
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void shareSelected() {
        List<Uri> uris = new ArrayList<Uri>();
        for (LivePhotoItem it : selected) {
            Uri u = ShareUtil.prepare(this, it, it.isVideo);
            if (u != null) uris.add(u);
        }
        if (uris.isEmpty()) return;
        Intent i = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<Uri>(uris));
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "分享 " + uris.size() + " 张照片"));
        } catch (Throwable t) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void favSelected() {
        boolean allFav = true;
        for (LivePhotoItem it : selected) if (!Favs.isFav(this, it)) allFav = false;
        for (LivePhotoItem it : selected) Favs.set(this, it, !allFav);
        adapter.notifyDataSetChanged();
        Toast.makeText(this, allFav ? "已取消收藏" : "已加入个人收藏", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelected() {
        if (selected.isEmpty()) return;
        final List<Uri> media = new ArrayList<Uri>();
        final List<File> files = new ArrayList<File>();
        for (LivePhotoItem it : selected) {
            if (it.id > 0 && it.imageUri != null) media.add(it.imageUri);
            else if (it.imageFile != null) files.add(it.imageFile);
        }
        if (!media.isEmpty() && Build.VERSION.SDK_INT >= 30) {
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), media);
                startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
                return;
            } catch (Throwable ignored) {
            }
        }
        for (File f : files) {
            try {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } catch (Throwable ignored) {
            }
        }
        afterDelete();
    }

    private void afterDelete() {
        exitSelect();
        reload();
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
    }

    // ---------- 数据 ----------

    private void reload() {
        if (loading) return;
        loading = true;
        if (allItems.isEmpty()) {
            int cached = Library.loadCache(this);
            if (cached > 0) {
                allItems = Library.snapshot();
                sortItems();
                applyFilter();
                subtitleView.setText("正在更新…点此取消");
            } else {
                subtitleView.setText("正在读取…点此取消");
            }
        } else {
            subtitleView.setText("正在更新…");
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long t0 = System.currentTimeMillis();
                Library.refreshFast(MainActivity.this);
                final List<LivePhotoItem> snap = Library.snapshot();
                final long cost = System.currentTimeMillis() - t0;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        lastRefresh = System.currentTimeMillis();
                        Log.d("LivePhotoPerf", "refreshFast cost=" + cost + "ms items=" + snap.size());
                        allItems = snap;
                        sortItems();
                        applyFilter();
                        if (!scanStarted) {
                            scanStarted = true;
                            startMotionScan();
                        }
                    }
                });
            }
        }).start();
    }

    /** 手动「刷新」：触发媒体库扫描并全量（含四个目录）刷新。 */
    private void refreshFull() {
        Toast.makeText(this, "正在刷新图库…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Library.requestMediaScan(MainActivity.this, 1500);
                Library.refresh(MainActivity.this);
                final List<LivePhotoItem> snap = Library.snapshot();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        allItems = snap;
                        sortItems();
                        applyFilter();
                        Toast.makeText(MainActivity.this, "已刷新", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void sortItems() {
        Collections.sort(allItems, new Comparator<LivePhotoItem>() {
            @Override
            public int compare(LivePhotoItem a, LivePhotoItem b) {
                int sort = Prefs.sortMode(MainActivity.this);
                if (sort == 1) return Long.compare(b.dateAdded, a.dateAdded);
                if (sort == 2) return Long.compare(a.dateMs, b.dateMs);
                return Long.compare(b.dateMs, a.dateMs);
            }
        });
    }

    private void startMotionScan() {
        Library.startMotionScan(this, new Runnable() {
            @Override
            public void run() {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        updateSubtitle();
                        adapter.notifyDataSetChanged();
                        if (mode > 0 || momentFilter > 0 || bucketFilter != null) applyFilter();
                    }
                });
            }
        });
    }

    private void applyFilter() {
        List<LivePhotoItem> filtered = new ArrayList<LivePhotoItem>();
        Calendar cc = Calendar.getInstance();
        for (LivePhotoItem it : allItems) {
            if (mode == 4) {
                if (!Hidden.isHidden(this, it)) continue;
            } else if (Hidden.isHidden(this, it)) {
                continue;
            }
            if (mode == 1 && !it.live()) continue;
            if (mode == 2 && !Favs.isFav(this, it)) continue;
            if (mode == 3 && !it.isVideo) continue;
            if (mode == 5 && !isScreenshot(it)) continue;
            if (mode != 5 && Prefs.hideScreenshot(this) && isScreenshot(it)) continue;
            if (mode != 3 && Prefs.hideVideo(this) && it.isVideo) continue;
            if (mode == 6 && !Cards.isCard(this, it)) continue;
            if (mode == 7 && (bucketFilter == null || !bucketFilter.equals(it.bucketName))) continue;
            if (yearFilter > 0 || monthFilter > 0) {
                Calendar dc = Calendar.getInstance();
                dc.setTimeInMillis(it.dateMs <= 0 ? 0 : it.dateMs);
                if (yearFilter > 0 && dc.get(Calendar.YEAR) != yearFilter) continue;
                if (monthFilter > 0 && dc.get(Calendar.MONTH) + 1 != monthFilter) continue;
            }
            if (momentFilter > 0) {
                cc.setTimeInMillis(it.dateMs <= 0 ? 0 : it.dateMs);
                int key = (cc.get(Calendar.MONTH) + 1) * 100 + cc.get(Calendar.DAY_OF_MONTH);
                if (key != momentFilter) continue;
            }
            filtered.add(it);
        }
        items = filtered;
        rebuildRows();
        adapter.notifyDataSetChanged();
        updateSubtitle();
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && stickyHeader != null) stickyHeader.setVisibility(View.GONE);
        if (empty) {
            if (!hasPermission() && !hasPartialAccess()) {
                emptyTitle.setText("没有照片");
                emptySub.setText(needPermissionHint());
                showEmptyAction(true);
            } else {
                emptyTitle.setText("没有照片");
                emptySub.setText("这个筛选下没有照片，换个筛选试试；也可以导入文件。");
                showEmptyAction(false);
            }
        }
    }

    private String needPermissionHint() {
        return "授权照片和视频权限后会自动读取本机照片。";
    }

    private boolean isScreenshot(LivePhotoItem it) {
        String n = it.title == null ? "" : it.title.toLowerCase();
        String b = it.bucketName == null ? "" : it.bucketName.toLowerCase();
        return n.startsWith("screenshot") || n.contains("截图") || b.contains("截屏")
                || b.contains("截图") || b.contains("screenshot");
    }

    private String modeName() {
        switch (mode) {
            case 1: return "实况照片";
            case 2: return "个人收藏";
            case 3: return "视频";
            case 4: return "已隐藏";
            case 5: return "屏幕截图";
            case 6: return "我的卡证";
            case 7: return bucketFilter == null ? "相册" : bucketFilter;
            default:
                if (momentFilter > 0) return (momentFilter / 100) + "月" + (momentFilter % 100) + "日";
                if (yearFilter > 0 && monthFilter > 0) return yearFilter + "年" + monthFilter + "月";
                if (yearFilter > 0) return yearFilter + "年";
                return "全部照片";
        }
    }

    private void updateSubtitle() {
        if (selecting) return;
        if (hasPartialAccess() && !hasPermission()) {
            subtitleView.setText("仅可见 " + allItems.size() + " 项 · 点此管理已选照片");
            subtitleView.setVisibility(View.VISIBLE);
            return;
        }
        if (loading) {
            subtitleView.setText("正在读取…点此取消");
            subtitleView.setVisibility(View.VISIBLE);
            return;
        }
        int lives = 0;
        for (LivePhotoItem it : allItems) if (it.live()) lives++;
        String s = (mode > 0 || momentFilter > 0) ? (modeName() + " · ") : "";
        s += allItems.size() + " 项 · " + lives + " 张实况";
        if (Library.isScanningMotion()) s += " · 识别中";
        subtitleView.setText(s);
        subtitleView.setVisibility(mode > 0 || momentFilter > 0 ? View.VISIBLE : View.GONE);
    }

    private void rebuildRows() {
        rows.clear();
        collageAdded = false;
        if (items.isEmpty()) return;
        Calendar cal = Calendar.getInstance();
        String lastKey = null;
        List<LivePhotoItem> buf = new ArrayList<LivePhotoItem>();
        for (LivePhotoItem it : items) {
            cal.setTimeInMillis(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
            String key = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1)
                    + "-" + cal.get(Calendar.DAY_OF_MONTH);
            if (!key.equals(lastKey)) {
                if (!buf.isEmpty()) flushDay(lastKey, buf);
                buf = new ArrayList<LivePhotoItem>();
                lastKey = key;
            }
            buf.add(it);
        }
        if (!buf.isEmpty()) flushDay(lastKey, buf);
    }

    private void flushDay(String key, List<LivePhotoItem> list) {
        Row h = new Row();
        h.type = 0;
        String[] p = key.split("-");
        int y = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        int d = Integer.parseInt(p[2]);
        Calendar today = Calendar.getInstance();
        Calendar that = Calendar.getInstance();
        that.clear();
        that.set(y, m - 1, d);
        String title;
        long diffDays = (today.getTimeInMillis() - that.getTimeInMillis()) / 86400000L;
        if (diffDays == 0) title = "今天";
        else if (diffDays == 1) title = "昨天";
        else if (diffDays == 2) title = "前天";
        else title = m + "月" + d + "日";
        // 相册名（最多两个）
        java.util.LinkedHashSet<String> buckets = new java.util.LinkedHashSet<String>();
        for (LivePhotoItem it : list) {
            if (it.bucketName != null && !it.bucketName.trim().isEmpty()) {
                buckets.add(it.bucketName);
                if (buckets.size() >= 2) break;
            }
        }
        if (!buckets.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String b : buckets) {
                if (sb.length() > 0) sb.append("，");
                sb.append(b);
            }
            title += " | " + sb;
        }
        h.header = title;
        rows.add(h);
        int startIndex = 0;
        if (Prefs.gridSize(MainActivity.this) == 2 && !collageAdded && list.size() >= 3) {
            Row collage = new Row();
            collage.type = 2;
            collage.items[0] = list.get(0);
            collage.items[1] = list.get(1);
            collage.items[2] = list.get(2);
            rows.add(collage);
            collageAdded = true;
            startIndex = 3;
        }
        for (int i = startIndex; i < list.size(); i += cols) {
            Row r = new Row();
            r.type = 1;
            for (int j = 0; j < cols; j++) {
                int k = i + j;
                if (k < list.size()) r.items[j] = list.get(k);
            }
            rows.add(r);
        }
    }

    private void openViewer(LivePhotoItem item) {
        if (item == null) return;
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra("id", item.id);
        i.putExtra("size", item.imageSize);
        i.putExtra("title", item.title == null ? "" : item.title);
        startActivity(i);
    }

    // ---------- adapter ----------

    private class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.type == 0) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = Ui.text(MainActivity.this, "", 19, Ui.L_TEXT, true);
                    tv.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 16),
                            Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 8));
                }
                tv.setText(row.header);
                return tv;
            }
            if (row.type == 2) {
                int big = cellSize * 2 + gap;
                LinearLayout line = new LinearLayout(MainActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(gap, 0, gap, 0);
                Cell bigCell = new Cell(MainActivity.this);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(big, big);
                blp.setMargins(gap, 0, gap, 0);
                line.addView(bigCell, blp);
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                Cell c1 = new Cell(MainActivity.this);
                Cell c2 = new Cell(MainActivity.this);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cellSize, cellSize);
                col.addView(c1, clp);
                LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(cellSize, cellSize);
                clp2.setMargins(0, gap, 0, 0);
                col.addView(c2, clp2);
                line.addView(col, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                bigCell.bind(row.items[0]);
                c1.bind(row.items[1]);
                c2.bind(row.items[2]);
                return line;
            }
            LinearLayout line;
            if (convertView instanceof LinearLayout) {
                line = (LinearLayout) convertView;
            } else {
                line = new LinearLayout(MainActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(gap, 0, gap, 0);
                for (int i = 0; i < cols; i++) {
                    Cell cell = new Cell(MainActivity.this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellSize, 1f);
                    lp.setMargins(gap, 0, gap, 0);
                    line.addView(cell, lp);
                }
            }
            for (int i = 0; i < cols; i++) {
                Cell cell = (Cell) line.getChildAt(i);
                if (i < row.items.length) cell.bind(row.items[i]);
                else cell.bind(null);
            }
            return line;
        }
    }

    // ---------- cell ----------

    private class Cell extends FrameLayout {
        final ImageView image;
        final TextView badge;
        final TextView check;
        final TextView star;
        LivePhotoItem item;
        boolean suppressClick = false;
        MotionPlayerView preview;
        final Runnable startPreview = new Runnable() {
            @Override
            public void run() {
                doStartPreview();
            }
        };
        final Runnable enterSelectRunnable = new Runnable() {
            @Override
            public void run() {
                if (!selecting && item != null) {
                    suppressClick = true;
                    enterSelect();
                    toggleSelect(item);
                }
            }
        };

        Cell(Context c) {
            super(c);
            setBackground(Ui.rounded(c, 0xFFEDEDF0, 6));
            setClipToOutline(true);
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            badge = Ui.text(c, "LIVE", 9, 0xFFFFFFFF, true);
            badge.setBackground(Ui.rounded(c, 0x66000000, 3));
            badge.setPadding(Ui.dp(c, 4), Ui.dp(c, 1), Ui.dp(c, 4), Ui.dp(c, 1));
            LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.TOP | Gravity.LEFT;
            blp.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), 0, 0);
            addView(badge, blp);

            check = Ui.text(c, "", 12, 0xFFFFFFFF, true);
            check.setGravity(Gravity.CENTER);
            LayoutParams clp = new LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
            clp.gravity = Gravity.TOP | Gravity.RIGHT;
            clp.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 4), 0);
            addView(check, clp);
            check.setVisibility(INVISIBLE);

            star = Ui.text(c, "★", 11, 0xFFFFC107, false);
            LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            slp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            slp.setMargins(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
            addView(star, slp);
            star.setVisibility(INVISIBLE);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (suppressClick) {
                        suppressClick = false;
                        return;
                    }
                    if (selecting) toggleSelect(item);
                    else openViewer(item);
                }
            });
            setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (selecting) return false;
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            ui.removeCallbacks(startPreview);
                            if (item != null && !item.live()) {
                                ui.postDelayed(enterSelectRunnable, 500);
                            } else {
                                ui.postDelayed(startPreview, 180);
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            ui.removeCallbacks(startPreview);
                            ui.removeCallbacks(enterSelectRunnable);
                            stopPreview();
                            break;
                        default:
                            break;
                    }
                    return false;
                }
            });
        }

        void bind(LivePhotoItem it) {
            ui.removeCallbacks(startPreview);
            stopPreview();
            item = it;
            suppressClick = false;
            if (it == null) {
                image.setTag(null);
                image.setImageDrawable(null);
                badge.setVisibility(INVISIBLE);
                check.setVisibility(INVISIBLE);
                star.setVisibility(INVISIBLE);
                return;
            }
            String badgeText = it.badgeText();
            badge.setText(badgeText);
            badge.setVisibility(badgeText.length() > 0 && Prefs.showBadge(MainActivity.this)
                    ? VISIBLE : INVISIBLE);
            if (selecting) {
                check.setVisibility(VISIBLE);
                boolean sel = selected.contains(it);
                check.setBackground(sel ? Ui.rounded(MainActivity.this, 0xFF3478F6, 10)
                        : Ui.strokeRounded(MainActivity.this, 0x00000000, 0xFFFFFFFF, 1.4f, 10));
                check.setTextColor(sel ? 0xFFFFFFFF : 0xFFFFFFFF);
                check.setText(sel ? "✓" : "");
                star.setVisibility(bestPicks.contains(it) ? VISIBLE : INVISIBLE);
            } else {
                check.setVisibility(INVISIBLE);
                star.setVisibility(INVISIBLE);
            }
            ThumbLoader.get(MainActivity.this).loadThumb(it, cellSize, image);
        }

        void doStartPreview() {
            if (item == null || preview != null || !item.live() || !Prefs.longPressPlay(MainActivity.this)) return;
            suppressClick = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            preview = new MotionPlayerView(MainActivity.this);
            preview.setMode(MotionPlayerView.MODE_CROP);
            addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            final LivePhotoItem it = item;
            if (it.videoUri != null) {
                preview.play(it.videoUri, true, true);
            } else if (it.motionPhoto) {
                MotionVideo.get(MainActivity.this, it, new MotionVideo.Callback() {
                    @Override
                    public void onReady(File f) {
                        if (f == null || preview == null || item != it) return;
                        preview.play(Uri.fromFile(f), true, true);
                    }
                });
            }
            activeCell = this;
        }

        void stopPreview() {
            if (preview != null) {
                preview.stop();
                removeView(preview);
                preview = null;
            }
        }
    }

    private void toggleSelect(LivePhotoItem it) {
        if (it == null) return;
        if (selected.contains(it)) selected.remove(it);
        else selected.add(it);
        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void stopActivePreview() {
        if (activeCell != null) {
            activeCell.stopPreview();
            activeCell = null;
        }
    }

    // ---------- 空状态 / 权限 ----------

    private View buildEmptyView() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(0xFFFFFFFF);
        wrap.setVisibility(View.GONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.gravity = Gravity.CENTER;
        wrap.addView(box, boxLp);

        IconView icon = new IconView(this, IconView.GRID);
        icon.setIconColor(Ui.L_TEXT3);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(icon, ilp);

        emptyTitle = Ui.text(this, "没有照片", 17, Ui.L_TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        Ui.margins(emptyTitle, this, 0, 16, 0, 0);
        box.addView(emptyTitle);

        emptySub = Ui.text(this, "", 13, Ui.L_TEXT2, false);
        emptySub.setGravity(Gravity.CENTER);
        emptySub.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.margins(emptySub, this, 32, 8, 32, 0);
        box.addView(emptySub);

        emptyActions = new LinearLayout(this);
        emptyActions.setOrientation(LinearLayout.HORIZONTAL);
        emptyActions.setGravity(Gravity.CENTER);
        Ui.margins(emptyActions, this, 0, 22, 0, 0);
        box.addView(emptyActions);
        return wrap;
    }

    private void showEmptyAction(boolean needPermission) {
        emptyActions.removeAllViews();
        if (needPermission) {
            TextView grant = actionText("允许访问相册", Ui.L_BLUE, true);
            emptyActions.addView(grant);
            grant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPermission();
                }
            });
        }
        TextView imp = actionText("导入文件", Ui.L_TEXT, false);
        Ui.margins(imp, this, 10, 0, 0, 0);
        emptyActions.addView(imp);
        imp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFiles();
            }
        });
    }

    private TextView actionText(String text, int color, boolean filled) {
        TextView t = Ui.text(this, text, 13, filled ? 0xFFFFFFFF : Ui.L_TEXT, true);
        t.setBackground(filled ? Ui.rounded(this, Ui.L_BLUE, 18)
                : Ui.strokeRounded(this, 0xFFFFFFFF, Ui.L_LINE, 1, 18));
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        return t;
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Android 14+ 部分授权：只能看到用户勾选的照片。 */
    private boolean hasPartialAccess() {
        return Build.VERSION.SDK_INT >= 34
                && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, REQ_PERM);
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO}, REQ_PERM);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    /** 冷启动权限引导页。 */
    private void showPermissionIntro() {
        emptyView.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        emptyTitle.setText("允许访问照片");
        emptySub.setText("小瞳心照片需要读取本机照片和视频，用来浏览照片、播放实况照片。\n照片只在本机处理，不会上传。");
        emptyActions.removeAllViews();
        TextView grant = actionText("允许访问", Ui.L_BLUE, true);
        grant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermission();
            }
        });
        emptyActions.addView(grant);
        TextView sys = actionText("去系统相册看看", Ui.L_TEXT, false);
        Ui.margins(sys, this, 10, 0, 0, 0);
        emptyActions.addView(sys);
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    i.setType("image/*");
                    startActivity(i);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "无法打开系统相册", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERM) return;
        if (hasPermission() || hasPartialAccess()) {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            reload();
        } else {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            emptyTitle.setText("未获得照片权限");
            emptySub.setText("可在「系统设置 → 应用 → 小瞳心照片 → 权限」中重新授权。");
            emptyActions.removeAllViews();
            TextView grant = actionText("重新授权", Ui.L_BLUE, true);
            grant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPermission();
                }
            });
            emptyActions.addView(grant);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<Uri>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri u = intent.getData();
            if (u != null) uris.add(u);
        }
        if (!uris.isEmpty()) importUris(uris);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int m = intent.getIntExtra("mode", -1);
        int moment = intent.getIntExtra("moment", 0);
        String bucket = intent.getStringExtra("bucket");
        if (m >= 0) {
            mode = m;
            momentFilter = moment;
            bucketFilter = bucket;
            applyFilter();
            listView.setSelection(0);
        } else if (moment > 0) {
            momentFilter = moment;
            mode = 0;
            applyFilter();
        }
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<Uri>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) importUris(uris);
        } else if (requestCode == REQ_DELETE) {
            afterDelete();
        }
    }

    private void importUris(final List<Uri> uris) {
        Toast.makeText(this, "正在导入…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = Importer.importUris(MainActivity.this, uris);
                Library.refresh(MainActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                n > 0 ? "已导入 " + n + " 个文件" : "没有可导入的文件",
                                Toast.LENGTH_SHORT).show();
                        reload();
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerMediaObserver();
        if (selecting) exitSelect();
        if (hasPermission() && !loading && System.currentTimeMillis() - lastRefresh > 3000) {
            reload();
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterMediaObserver();
        stopActivePreview();
    }
}
