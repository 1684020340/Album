package com.livephoto.viewer;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用设置（模仿 iOS 设置页的分组开关）。 */
public final class Prefs {
    private static final String FILE = "settings";

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean onlyLive(Context c) {
        return sp(c).getBoolean("only_live", false);
    }

    public static void setOnlyLive(Context c, boolean v) {
        sp(c).edit().putBoolean("only_live", v).apply();
    }

    public static boolean showBadge(Context c) {
        return sp(c).getBoolean("show_badge", true);
    }

    public static void setShowBadge(Context c, boolean v) {
        sp(c).edit().putBoolean("show_badge", v).apply();
    }

    public static boolean longPressPlay(Context c) {
        return sp(c).getBoolean("lp_play", true);
    }

    public static void setLongPressPlay(Context c, boolean v) {
        sp(c).edit().putBoolean("lp_play", v).apply();
    }

    public static boolean similarBest(Context c) {
        return sp(c).getBoolean("similar_best", true);
    }

    public static void setSimilarBest(Context c, boolean v) {
        sp(c).edit().putBoolean("similar_best", v).apply();
    }

    public static boolean yearAgo(Context c) {
        return sp(c).getBoolean("year_ago", true);
    }

    public static void setYearAgo(Context c, boolean v) {
        sp(c).edit().putBoolean("year_ago", v).apply();
    }

    public static boolean cardsAlbum(Context c) {
        return sp(c).getBoolean("cards_album", true);
    }

    public static void setCardsAlbum(Context c, boolean v) {
        sp(c).edit().putBoolean("cards_album", v).apply();
    }

    public static boolean videoAutoplayMuted(Context c) {
        return sp(c).getBoolean("video_autoplay", true);
    }

    public static void setVideoAutoplayMuted(Context c, boolean v) {
        sp(c).edit().putBoolean("video_autoplay", v).apply();
    }

    public static boolean videoLoop(Context c) {
        return sp(c).getBoolean("video_loop", true);
    }

    public static void setVideoLoop(Context c, boolean v) {
        sp(c).edit().putBoolean("video_loop", v).apply();
    }

    public static boolean safeShare(Context c) {
        return sp(c).getBoolean("safe_share", true);
    }

    public static void setSafeShare(Context c, boolean v) {
        sp(c).edit().putBoolean("safe_share", v).apply();
    }

    public static boolean heifToJpeg(Context c) {
        return sp(c).getBoolean("heif_jpeg", true);
    }

    public static void setHeifToJpeg(Context c, boolean v) {
        sp(c).edit().putBoolean("heif_jpeg", v).apply();
    }

    public static int sortMode(Context c) {
        return sp(c).getInt("sort_mode", 0);
    }

    public static void setSortMode(Context c, int v) {
        sp(c).edit().putInt("sort_mode", v).apply();
    }

    public static int gridSize(Context c) {
        return sp(c).getInt("grid_size", 1);
    }

    public static void setGridSize(Context c, int v) {
        sp(c).edit().putInt("grid_size", v).apply();
    }

    public static boolean sound(Context c) {
        return sp(c).getBoolean("lp_sound", true);
    }

    public static void setSound(Context c, boolean v) {
        sp(c).edit().putBoolean("lp_sound", v).apply();
    }
}
