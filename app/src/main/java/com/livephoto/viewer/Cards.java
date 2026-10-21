package com.livephoto.viewer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 「我的卡证」相簿：手动收录的证件/银行卡类照片（小米相册同款入口）。 */
public final class Cards {
    private Cards() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("livephoto", Context.MODE_PRIVATE);
    }

    public static Set<String> get(Context c) {
        return new HashSet<String>(sp(c).getStringSet("cards", new HashSet<String>()));
    }

    public static boolean isCard(Context c, LivePhotoItem it) {
        return it != null && get(c).contains(it.artKey());
    }

    public static void set(Context c, LivePhotoItem it, boolean card) {
        if (it == null) return;
        Set<String> s = get(c);
        if (card) s.add(it.artKey());
        else s.remove(it.artKey());
        sp(c).edit().putStringSet("cards", s).apply();
    }

    public static int count(Context c) {
        return get(c).size();
    }
}
