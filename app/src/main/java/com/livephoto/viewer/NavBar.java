package com.livephoto.viewer;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 小米相册风格底部导航：照片 / 影集 / 搜索（浮动胶囊）。 */
public final class NavBar {
    private NavBar() {
    }

    public static View build(final Activity a, int selected) {
        FrameLayout wrap = new FrameLayout(a);
        wrap.setPadding(Ui.dp(a, 40), Ui.dp(a, 6), Ui.dp(a, 40), Ui.dp(a, 14));

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackground(Ui.lightPill(a, 30));
        bar.setElevation(Ui.dp(a, 10));
        bar.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        wrap.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar.addView(item(a, IconView.GRID, "照片", selected == 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (a instanceof MainActivity) return;
                Intent i = new Intent(a, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                i.putExtra("mode", 0);
                a.startActivity(i);
                a.finish();
            }
        }));
        bar.addView(item(a, IconView.FOLDER, "影集", selected == 1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (a instanceof AlbumsActivity) return;
                a.startActivity(new Intent(a, AlbumsActivity.class));
                a.finish();
            }
        }));
        bar.addView(item(a, IconView.SEARCH, "搜索", selected == 2, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (a instanceof SearchActivity) return;
                a.startActivity(new Intent(a, SearchActivity.class));
                a.finish();
            }
        }));
        return wrap;
    }

    private static LinearLayout item(Activity a, int icon, String label, boolean sel,
                                     View.OnClickListener l) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        IconView iv = new IconView(a, icon);
        iv.setIconColor(sel ? Ui.L_TEXT : Ui.L_TEXT2);
        box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
        TextView tv = Ui.text(a, label, 10, sel ? Ui.L_TEXT : Ui.L_TEXT2, sel);
        tv.setGravity(Gravity.CENTER);
        Ui.margins(tv, a, 0, 3, 0, 0);
        box.addView(tv);
        box.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(lp);
        return box;
    }
}
