package com.livephoto.viewer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 云服务页：接入中国移动云盘（跳转 + 文件导入）。 */
public class CloudActivity extends Activity {
    private static final int REQ_PICK = 201;
    private static final String CMCC_PKG = "com.chinamobile.mcloud";

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
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
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 18), Ui.dp(this, 30));
        scroll.addView(content);

        content.addView(buildStatusCard());
        content.addView(sectionTitle("如何查看移动云盘里的实况照片"));
        content.addView(stepsCard());
        content.addView(sectionTitle("导入"));
        content.addView(actionButton("从文件导入实况照片", "支持 .livp 压缩包、HEIC/JPG + MOV 配对文件", true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickFiles();
                    }
                }));
        content.addView(actionButton("打开移动云盘网页版", "yun.139.com，登录后可下载照片到本机", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openWeb();
                    }
                }));
        content.addView(actionButton("刷新本地图库", "重新扫描系统相册与导入的文件", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        refreshLibrary();
                    }
                }));

        TextView note = Ui.text(this,
                "说明：移动云盘官方开放接口需要企业开发者资质，本应用采用「云盘下载 / 分享 → 本应用自动读取」的接入方式，" +
                        "不会索取你的云盘账号密码。",
                12, Ui.TEXT3, false);
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.margins(note, this, 2, 20, 2, 0);
        content.addView(note);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));

        IconView back = new IconView(this, IconView.BACK);
        back.setIconColor(Ui.TEXT);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView title = Ui.text(this, "云服务 · 中国移动云盘", 16, Ui.TEXT, true);
        Ui.margins(title, this, 4, 0, 0, 0);
        bar.addView(title);
        return bar;
    }

    private View sectionTitle(String s) {
        TextView tv = Ui.text(this, s, 13, Ui.TEXT2, true);
        Ui.margins(tv, this, 4, 22, 0, 8);
        return tv;
    }

    private View buildStatusCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        Ui.margins(card, this, 0, 14, 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        IconView icon = new IconView(this, IconView.CLOUD);
        icon.setIconColor(Ui.BLUE);
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        Ui.margins(names, this, 12, 0, 0, 0);
        head.addView(names, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        names.addView(Ui.text(this, "中国移动云盘", 16, Ui.TEXT, true));

        String version = null;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(CMCC_PKG, 0);
            version = pi.versionName;
        } catch (Throwable ignored) {
        }
        TextView status;
        if (version != null) {
            status = Ui.text(this, "已安装 · " + version, 12, 0xFF30D158, false);
        } else {
            status = Ui.text(this, "未安装（可先安装，或使用网页版）", 12, Ui.TEXT2, false);
        }
        Ui.margins(status, this, 0, 3, 0, 0);
        names.addView(status);

        TextView open = Ui.text(this, "打开", 13, Ui.BLUE, true);
        open.setBackground(Ui.strokeRounded(this, 0x00000000, 0xFF3A3A3C, 1, 16));
        open.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        head.addView(open);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCloudApp();
            }
        });
        return card;
    }

    private View stepsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(this, Ui.SURFACE, 16));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));

        String[] steps = new String[]{
                "在移动云盘中找到实况照片（iPhone 实况通常为 HEIC + MOV 一对，或 .livp）。",
                "下载到手机；也可以长按选择「分享 / 用其他应用打开」，直接选「实况照片」。",
                "回到本应用，下拉刷新或重新进入，即可在「图库」里长按播放。"
        };
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (i > 0) Ui.margins(row, this, 0, 12, 0, 0);

            TextView num = Ui.text(this, String.valueOf(i + 1), 11, 0xFF000000, true);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.rounded(this, 0xFFFFFFFF, 9));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            nlp.setMargins(0, Ui.dp(this, 1), 0, 0);
            row.addView(num, nlp);

            TextView body = Ui.text(this, steps[i], 13, 0xFFE5E5EA, false);
            body.setLineSpacing(Ui.dp(this, 3), 1f);
            Ui.margins(body, this, 10, 0, 0, 0);
            row.addView(body, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }
        return card;
    }

    private View actionButton(String title, String sub, boolean primary, View.OnClickListener l) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setBackground(Ui.rounded(this, primary ? 0xFF0A84FF : Ui.SURFACE, 14));
        btn.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        Ui.margins(btn, this, 0, 10, 0, 0);

        btn.addView(Ui.text(this, title, 15, primary ? 0xFFFFFFFF : Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 12, primary ? 0xCCFFFFFF : Ui.TEXT2, false);
        Ui.margins(s, this, 0, 4, 0, 0);
        btn.addView(s);
        btn.setOnClickListener(l);
        return btn;
    }

    private void openCloudApp() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(CMCC_PKG);
            if (i == null) throw new Exception();
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "未安装中国移动云盘，正在打开网页版", Toast.LENGTH_SHORT).show();
            openWeb();
        }
    }

    private void openWeb() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://yun.139.com"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
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
            if (!uris.isEmpty()) importFiles(uris);
        }
    }

    private void importFiles(final List<Uri> uris) {
        Toast.makeText(this, "正在导入…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = Importer.importUris(CloudActivity.this, uris);
                Library.refresh(CloudActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CloudActivity.this,
                                n > 0 ? "已导入 " + n + " 个文件，可在图库中查看" : "没有可导入的文件",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void refreshLibrary() {
        Toast.makeText(this, "正在刷新…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int n = Library.refresh(CloudActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(CloudActivity.this, "共找到 " + n + " 张实况照片",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
}
