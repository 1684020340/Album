package com.livephoto.viewer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 收藏（个人收藏相簿）。 */
public final class Favs {
    private Favs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("livephoto", Context.MODE_PRIVATE);
    }

    public static Set<String> get(Context c) {
        return new HashSet<String>(sp(c).getStringSet("fav", new HashSet<String>()));
    }

    public static boolean isFav(Context c, LivePhotoItem it) {
        return it != null && get(c).contains(it.artKey());
    }

    public static void set(Context c, LivePhotoItem it, boolean fav) {
        if (it == null) return;
        Set<String> s = get(c);
        if (fav) s.add(it.artKey());
        else s.remove(it.artKey());
        sp(c).edit().putStringSet("fav", s).apply();
    }

    public static int count(Context c) {
        return get(c).size();
    }
}
