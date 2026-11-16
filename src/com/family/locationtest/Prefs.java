package com.family.locationtest;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;
import org.json.JSONObject;

public final class Prefs {
    private static final String FILE = "family";

    private Prefs() {}

    public static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String userId(Context context) {
        SharedPreferences p = sp(context);
        String id = p.getString("userId", "");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            p.edit().putString("userId", id).apply();
        }
        return id;
    }

    public static String cleanUrl(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        return s;
    }

    public static String lan(Context context) {
        return cleanUrl(sp(context).getString("lan", ""));
    }

    public static String tail(Context context) {
        return cleanUrl(sp(context).getString("tail", ""));
    }

    public static String mode(Context context) {
        String mode = sp(context).getString("mode", "auto");
        if (mode == null || mode.isEmpty()) return "auto";
        return mode;
    }

    public static String group(Context context) {
        String g = sp(context).getString("group", "Fam1");
        if (g == null || g.trim().isEmpty()) return "Fam1";
        return g.trim();
    }

    public static String name(Context context) {
        String n = sp(context).getString("name", "");
        return n == null ? "" : n.trim();
    }

    public static String phone(Context context) {
        String n = sp(context).getString("phone", "");
        return n == null ? "" : n.trim();
    }

    public static boolean share(Context context) {
        return sp(context).getBoolean("share", true);
    }

    public static String pace(Context context) {
        String pace = sp(context).getString("pace", "fast");
        if (pace == null || pace.isEmpty()) return "fast";
        return pace;
    }

    public static boolean fast(Context context) {
        return "fast".equals(pace(context));
    }

    public static long customMillis(Context context) {
        int n = sp(context).getInt("customEvery", 30);
        if (n < 1) n = 1;
        if (n > 999) n = 999;
        long ms = "min".equals(sp(context).getString("customUnit", "sec")) ? n * 60000L : n * 1000L;
        if (ms < 5000L) ms = 5000L;
        if (ms > 3600000L) ms = 3600000L;
        return ms;
    }

    public static boolean onlyStopped(Context context) {
        return sp(context).getBoolean("stopped", false);
    }

    public static int seconds(Context context, String key, int fallback) {
        int n = sp(context).getInt(key, fallback);
        if (n < 10) return 10;
        if (n > 3600) return 3600;
        return n;
    }

    public static int sendSeconds(Context context) {
        return seconds(context, "sendEvery", 30);
    }

    public static int fetchSeconds(Context context) {
        return seconds(context, "fetchEvery", 15);
    }

    public static boolean satellite(Context context) {
        return sp(context).getBoolean("satellite", false);
    }

    public static void setSatellite(Context context, boolean on) {
        sp(context).edit().putBoolean("satellite", on).apply();
    }

    public static boolean showPlaceNames(Context context) {
        return sp(context).getBoolean("showPlaceNames", false);
    }

    public static void setShowPlaceNames(Context context, boolean on) {
        sp(context).edit().putBoolean("showPlaceNames", on).apply();
    }

    public static boolean notifyArrive(Context context, String placeId) {
        return sp(context).getBoolean("arrive:" + placeId, false);
    }

    public static boolean notifyLeave(Context context, String placeId) {
        return sp(context).getBoolean("leave:" + placeId, false);
    }

    public static void setNotify(Context context, String placeId, boolean arrive, boolean on) {
        sp(context).edit().putBoolean((arrive ? "arrive:" : "leave:") + placeId, on).apply();
    }

    public static JSONObject fenceState(Context context) {
        try {
            return new JSONObject(sp(context).getString("fence", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void fenceState(Context context, JSONObject state) {
        sp(context).edit().putString("fence", state.toString()).apply();
    }

    public static void saveFix(Context context, double lat, double lon) {
        sp(context).edit().putFloat("lat", (float) lat).putFloat("lon", (float) lon).putBoolean("hasFix", true).apply();
    }

    public static boolean hasFix(Context context) {
        return sp(context).getBoolean("hasFix", false);
    }

    public static double lat(Context context) {
        return sp(context).getFloat("lat", 0f);
    }

    public static double lon(Context context) {
        return sp(context).getFloat("lon", 0f);
    }
}
