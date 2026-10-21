package com.livephoto.viewer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 隐藏的照片（仿 iOS 的「已隐藏」相簿）。 */
public final class Hidden {
    private Hidden() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("livephoto", Context.MODE_PRIVATE);
    }

    public static Set<String> get(Context c) {
        return new HashSet<String>(sp(c).getStringSet("hidden", new HashSet<String>()));
    }

    public static boolean isHidden(Context c, LivePhotoItem it) {
        return it != null && get(c).contains(it.artKey());
    }

    public static void set(Context c, LivePhotoItem it, boolean hidden) {
        if (it == null) return;
        Set<String> s = get(c);
        if (hidden) s.add(it.artKey());
        else s.remove(it.artKey());
        sp(c).edit().putStringSet("hidden", s).apply();
    }

    public static int count(Context c) {
        return get(c).size();
    }
}
