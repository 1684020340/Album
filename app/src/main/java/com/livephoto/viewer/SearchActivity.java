package com.livephoto.viewer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 搜索：iOS 26 的独立搜索入口（关键词 + 分类过滤）。 */
public class SearchActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<LivePhotoItem> all = new ArrayList<LivePhotoItem>();
    private List<LivePhotoItem> results = new ArrayList<LivePhotoItem>();
    private List<Row> rows = new ArrayList<Row>();
    private EditText input;
    private ListView listView;
    private LinearLayout suggestBox;
    private View emptyView;
    private int category = 0; // 0 全部 1 实况 2 视频 3 收藏 4 屏幕截图
    private String query = "";
    private int cell;

    static class Row {
        LivePhotoItem[] items = new LivePhotoItem[3];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cell = (Ui.screenWidth(this) - Ui.dp(this, 2) * 2) / 3;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.L_BG);
        setContentView(root);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout contentWrap = new FrameLayout(this);
        column.addView(contentWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setBackgroundColor(Ui.L_BG);
        listView.setVerticalScrollBarEnabled(false);
        contentWrap.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyView = buildEmpty();
        contentWrap.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(NavBar.build(this, 2), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        load();
        input.requestFocus();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(row);

        input = new EditText(this);
        input.setHint("搜索照片、视频、实况");
        input.setHintTextColor(Ui.L_TEXT3);
        input.setTextColor(Ui.L_TEXT);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setBackground(Ui.lightPill(this, 18));
        input.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        row.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase();
                apply();
            }
        });

        TextView cancel = Ui.text(this, "取消", 14, Ui.L_BLUE, false);
        cancel.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        row.addView(cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        suggestBox = new LinearLayout(this);
        suggestBox.setOrientation(LinearLayout.HORIZONTAL);
        Ui.margins(suggestBox, this, 0, 10, 0, 0);
        bar.addView(suggestBox);
        String[] chips = new String[]{"全部", "实况", "视频", "收藏", "截图"};
        for (int i = 0; i < chips.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, chips[i], 12, Ui.L_TEXT2, true);
            t.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
            t.setBackground(Ui.rounded(this, 0xFFFFFFFF, 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
            suggestBox.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    category = idx;
                    updateChips();
                    apply();
                }
            });
        }
        updateChips();
        return bar;
    }

    private TextView[] chipViews;

    private void updateChips() {
        if (suggestBox == null || suggestBox.getChildCount() == 0) return;
        chipViews = new TextView[suggestBox.getChildCount()];
        for (int i = 0; i < suggestBox.getChildCount(); i++) {
            TextView t = (TextView) suggestBox.getChildAt(i);
            chipViews[i] = t;
            boolean sel = i == category;
            t.setTextColor(sel ? Ui.L_BLUE : Ui.L_TEXT2);
            t.setBackground(sel
                    ? Ui.rounded(this, 0xFFE8F0FE, 14)
                    : Ui.rounded(this, 0xFFFFFFFF, 14));
        }
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Ui.L_BG);
        box.setVisibility(View.GONE);
        TextView t = Ui.text(this, "没有找到结果", 16, Ui.L_TEXT, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView s = Ui.text(this, "试试「实况」「视频」「收藏」或文件名、日期", 12, Ui.L_TEXT2, false);
        s.setGravity(Gravity.CENTER);
        Ui.margins(s, this, 24, 8, 24, 0);
        box.addView(s);
        return box;
    }

    private void load() {
        List<LivePhotoItem> snap = Library.snapshot();
        if (!snap.isEmpty()) {
            all = snap;
            apply();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Library.refresh(SearchActivity.this);
                final List<LivePhotoItem> s = Library.snapshot();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        all = s;
                        apply();
                    }
                });
            }
        }).start();
    }

    private boolean isScreenshot(LivePhotoItem it) {
        String n = it.title == null ? "" : it.title.toLowerCase();
        String b = it.bucket == null ? "" : it.bucket.toLowerCase();
        return n.startsWith("screenshot") || n.contains("截图")
                || b.contains("screenshot") || b.contains("截图");
    }

    private void apply() {
        List<LivePhotoItem> out = new ArrayList<LivePhotoItem>();
        Calendar cal = Calendar.getInstance();
        for (LivePhotoItem it : all) {
            if (Hidden.isHidden(this, it)) continue;
            if (category == 1 && !it.live()) continue;
            if (category == 2 && !it.isVideo) continue;
            if (category == 3 && !Favs.isFav(this, it)) continue;
            if (category == 4 && !isScreenshot(it)) continue;
            if (!query.isEmpty()) {
                String title = it.title == null ? "" : it.title.toLowerCase();
                String date = it.dateText();
                cal.setTimeInMillis(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
                String y = String.valueOf(cal.get(Calendar.YEAR));
                String m = String.valueOf(cal.get(Calendar.MONTH) + 1);
                if (!title.contains(query) && !date.contains(query)
                        && !y.equals(query) && !m.equals(query)) continue;
            }
            out.add(it);
        }
        results = out;
        rows = new ArrayList<Row>();
        for (int i = 0; i < results.size(); i += 3) {
            Row r = new Row();
            for (int j = 0; j < 3 && i + j < results.size(); j++) r.items[j] = results.get(i + j);
            rows.add(r);
        }
        if (listView != null) listView.setAdapter(new GridAdapter());
        if (emptyView != null) {
            boolean empty = results.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    private class GridAdapter extends BaseAdapter {
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
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout line;
            if (convertView instanceof LinearLayout) {
                line = (LinearLayout) convertView;
            } else {
                line = new LinearLayout(SearchActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < 3; i++) {
                    FrameLayout cellWrap = new FrameLayout(SearchActivity.this);
                    cellWrap.setBackground(Ui.rounded(SearchActivity.this, 0xFFEDEDF0, 6));
                    cellWrap.setClipToOutline(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cell, 1f);
                    lp.setMargins(Ui.dp(SearchActivity.this, 1), Ui.dp(SearchActivity.this, 1),
                            Ui.dp(SearchActivity.this, 1), Ui.dp(SearchActivity.this, 1));
                    line.addView(cellWrap, lp);
                    ImageView iv = new ImageView(SearchActivity.this);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cellWrap.addView(iv, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    TextView badge = Ui.text(SearchActivity.this, "", 9, Ui.TEXT, true);
                    badge.setBackground(Ui.rounded(SearchActivity.this, 0x59000000, 3));
                    badge.setPadding(Ui.dp(SearchActivity.this, 4), Ui.dp(SearchActivity.this, 1),
                            Ui.dp(SearchActivity.this, 4), Ui.dp(SearchActivity.this, 1));
                    FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    blp.gravity = Gravity.TOP | Gravity.LEFT;
                    blp.setMargins(Ui.dp(SearchActivity.this, 4), Ui.dp(SearchActivity.this, 4), 0, 0);
                    cellWrap.addView(badge, blp);
                    cellWrap.setTag(new View[]{iv, badge});
                }
            }
            Row row = rows.get(position);
            for (int i = 0; i < 3; i++) {
                FrameLayout cellWrap = (FrameLayout) line.getChildAt(i);
                View[] views = (View[]) cellWrap.getTag();
                ImageView iv = (ImageView) views[0];
                TextView badge = (TextView) views[1];
                final LivePhotoItem item = row.items[i];
                if (item == null) {
                    iv.setImageDrawable(null);
                    badge.setVisibility(View.INVISIBLE);
                    cellWrap.setClickable(false);
                    continue;
                }
                badge.setVisibility(item.live() || item.isVideo ? View.VISIBLE : View.INVISIBLE);
                badge.setText(item.isVideo ? "▶" + item.durationText() : "LIVE");
                ThumbLoader.get(SearchActivity.this).loadThumb(item, cell, iv);
                cellWrap.setClickable(true);
                cellWrap.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(SearchActivity.this, ViewerActivity.class);
                        i.putExtra("id", item.id);
                        i.putExtra("size", item.imageSize);
                        i.putExtra("title", item.title == null ? "" : item.title);
                        startActivity(i);
                    }
                });
            }
            return line;
        }
    }
}
