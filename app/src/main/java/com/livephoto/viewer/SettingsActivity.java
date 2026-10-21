package com.livephoto.viewer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 设置：仿小米相册设置页（分组开关）。 */
public class SettingsActivity extends Activity {

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
        column.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 36));
        scroll.addView(content);

        // ---- 智能整理 ----
        content.addView(section("智能整理"));
        LinearLayout card1 = card(content);
        card1.addView(switchRow(card1, "相似照片优选",
                "批量选择照片时，会在相似照片中优选一张，并标记为星",
                Prefs.similarBest(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setSimilarBest(SettingsActivity.this, v);
                    }
                }));
        card1.addView(switchRow(card1, "那年今日",
                "为您聚合过往今日的美好影像。关闭后，相册内将不再显示此功能",
                Prefs.yearAgo(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setYearAgo(SettingsActivity.this, v);
                    }
                }));
        card1.addView(switchRow(card1, "我的卡证",
                "为您整理证件、银行卡类照片（手动收录）",
                Prefs.cardsAlbum(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setCardsAlbum(SettingsActivity.this, v);
                    }
                }));

        // ---- 浏览 ----
        content.addView(section("浏览"));
        LinearLayout card2 = card(content);
        card2.addView(clickRow(card2, "查看屏蔽相册", "已隐藏的照片", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(SettingsActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                i.putExtra("mode", 4);
                startActivity(i);
                finish();
            }
        }));
        card2.addView(clickRow(card2, "提取图片内容", "选择图片中的文本进行操作（需 OCR 引擎）",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(SettingsActivity.this,
                                "本机没有内置 OCR 引擎，暂不支持", Toast.LENGTH_SHORT).show();
                    }
                }));
        card2.addView(switchRow(card2, "超动态显示",
                "以 HDR 高动态范围呈现更多明暗细节（当前设备/解码不支持）",
                false, null));
        card2.addView(switchRow(card2, "视频自动播放且静音",
                "打开照片时视频自动静音播放",
                Prefs.videoAutoplayMuted(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setVideoAutoplayMuted(SettingsActivity.this, v);
                    }
                }));
        card2.addView(switchRow(card2, "循环播放视频", "视频播放结束后自动重播",
                Prefs.videoLoop(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setVideoLoop(SettingsActivity.this, v);
                    }
                }));

        // ---- 发送 ----
        content.addView(section("发送"));
        LinearLayout card3 = card(content);
        card3.addView(switchRow(card3, "安全分享", "分享时去除照片的位置和拍摄信息",
                Prefs.safeShare(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setSafeShare(SettingsActivity.this, v);
                    }
                }));
        card3.addView(switchRow(card3, "发送时转换HEIF图片", "发送时将 HEIF 图片转为 JPEG",
                Prefs.heifToJpeg(this), new Toggle() {
                    @Override
                    public void onChanged(boolean v) {
                        Prefs.setHeifToJpeg(SettingsActivity.this, v);
                    }
                }));
    }

    private interface Toggle {
        void onChanged(boolean v);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        IconView back = new IconView(this, IconView.BACK);
        back.setIconColor(Ui.L_TEXT);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView title = Ui.text(this, "设置", 20, Ui.L_TEXT, true);
        Ui.margins(title, this, 4, 0, 0, 0);
        bar.addView(title);
        return bar;
    }

    private View section(String s) {
        TextView tv = Ui.text(this, s, 13, Ui.L_TEXT2, false);
        tv.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 20), 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.lightCard(this, 16));
        card.setElevation(Ui.dp(this, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        parent.addView(card, lp);
        return card;
    }

    private View switchRow(LinearLayout card, String title, String sub, boolean initial,
                           final Toggle toggle) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        if (card.getChildCount() > 0) wrap.addView(divider());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 15, Ui.L_TEXT, false));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 12, Ui.L_TEXT2, false);
            Ui.margins(s, this, 0, 3, 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        try {
            sw.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
            sw.setTrackTintList(ColorStateList.valueOf(initial ? Ui.L_BLUE : 0xFFD1D1D6));
        } catch (Throwable ignored) {
        }
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                try {
                    buttonView.setButtonTintList(ColorStateList.valueOf(
                            isChecked ? Ui.L_BLUE : 0xFFD1D1D6));
                } catch (Throwable ignored) {
                }
                if (toggle != null) toggle.onChanged(isChecked);
            }
        });
        row.addView(sw);
        wrap.addView(row);
        return wrap;
    }

    private View clickRow(LinearLayout card, String title, String sub, View.OnClickListener l) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        if (card.getChildCount() > 0) wrap.addView(divider());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 15, Ui.L_TEXT, false));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 12, Ui.L_TEXT2, false);
            Ui.margins(s, this, 0, 3, 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, "›", 22, Ui.L_TEXT3, false));
        row.setOnClickListener(l);
        wrap.addView(row);
        return wrap;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.L_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(Ui.dp(this, 16), 0, 0, 0);
        v.setLayoutParams(lp);
        return v;
    }
}
