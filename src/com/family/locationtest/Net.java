package com.family.locationtest;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Net {
    private static String cachedBase = "";
    private static long cachedAt = 0;

    private Net() {}

    public static final class Result {
        public int code;
        public String body = "";
        public String error;
        public byte[] raw;
    }

    public static void forgetRoute() {
        cachedBase = "";
        cachedAt = 0;
    }

    public static String base(Context context) {
        String mode = Prefs.mode(context);
        String lan = Prefs.lan(context);
        String tail = Prefs.tail(context);
        if ("lan".equals(mode)) return lan;
        if ("tail".equals(mode)) return tail;
        long now = System.currentTimeMillis();
        if (!cachedBase.isEmpty() && now - cachedAt < 45000) return cachedBase;
        if (!lan.isEmpty() && health(lan)) {
            cachedBase = lan;
            cachedAt = now;
            return lan;
        }
        cachedBase = tail;
        cachedAt = now;
        return tail;
    }

    public static String routeLabel(Context context) {
        String base = base(context);
        if (base.isEmpty()) return "No server address";
        if (base.equals(Prefs.lan(context))) return "Home";
        if (base.equals(Prefs.tail(context))) return "Tailscale";
        return "Server";
    }

    public static boolean health(String base) {
        if (base == null || base.isEmpty()) return false;
        Result r = request("GET", base + "/health", null, 1500, false);
        return r.error == null && r.code >= 200 && r.code < 300;
    }

    public static Result get(String url) {
        return request("GET", url, null, 8000, false);
    }

    public static Result getWait(String url) {
        return request("GET", url, null, 25000, false);
    }

    public static Result getBytes(String url) {
        return request("GET", url, null, 8000, true);
    }

    public static Result post(String url, String json) {
        return request("POST", url, json, 8000, false);
    }

    public static String query(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static Result request(String method, String url, String json, int timeout, boolean bytes) {
        Result result = new Result();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod(method);
            if (json != null) {
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream out = conn.getOutputStream();
                out.write(data);
                out.close();
            }
            result.code = conn.getResponseCode();
            InputStream in = result.code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
                in.close();
                if (bytes) result.raw = buf.toByteArray();
                result.body = buf.toString("UTF-8");
            }
        } catch (Exception e) {
            result.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
        return result;
    }
}
