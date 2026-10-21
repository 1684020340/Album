package com.livephoto.viewer;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF000000;
    public static final int SURFACE = 0xFF1C1C1E;
    public static final int SURFACE2 = 0xFF2C2C2E;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT2 = 0xFF8E8E93;
    public static final int TEXT3 = 0xFF636366;
    public static final int BLUE = 0xFF0A84FF;
    public static final int PINK = 0xFFFF375F;
    public static final int YELLOW = 0xFFFFD60A;

    // 小米相册风格（浅色）
    public static final int L_BG = 0xFFF2F2F7;
    public static final int L_CARD = 0xFFFFFFFF;
    public static final int L_TEXT = 0xFF101010;
    public static final int L_TEXT2 = 0xFF8A8A8E;
    public static final int L_TEXT3 = 0xFFC0C0C4;
    public static final int L_LINE = 0xFFE6E6EA;
    public static final int L_BLUE = 0xFF3478F6;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) {
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    public static GradientDrawable lightCard(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(L_CARD);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable lightPill(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xF2FFFFFF);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 0.8f), 0xFFE2E2E7);
        return d;
    }

    public static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable strokeRounded(Context c, int color, int strokeColor,
                                                 float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setStroke(dp(c, strokeDp), strokeColor);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** iOS 26 风格的「液态玻璃」面板。 */
    public static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x3DFFFFFF);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 0.8f), 0x59FFFFFF);
        return d;
    }

    public static GradientDrawable glassDark(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x66000000);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 0.8f), 0x40FFFFFF);
        return d;
    }

    public static GradientDrawable glassSelected(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xF2FFFFFF);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 0.8f), 0x33FFFFFF);
        return d;
    }

    public static void glassify(View v, Context c, float radiusDp) {
        v.setBackground(glassDark(c, radiusDp));
        v.setElevation(dp(c, 4));
        v.setClipToOutline(true);
    }

    public static void padding(View v, Context c, float l, float t, float r, float b) {
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static android.widget.LinearLayout.LayoutParams lp(int w, int h) {
        return new android.widget.LinearLayout.LayoutParams(w, h);
    }

    public static android.widget.FrameLayout.LayoutParams flp(int w, int h) {
        return new android.widget.FrameLayout.LayoutParams(w, h);
    }

    public static void margins(View v, Context c, float l, float t, float r, float b) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (p instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) p;
            m.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
            v.setLayoutParams(m);
        }
    }

    public static String fmtSize(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static String fmtDuration(long ms) {
        if (ms <= 0) return "未知";
        long s = Math.round(ms / 1000.0);
        return s + " 秒";
    }
}
