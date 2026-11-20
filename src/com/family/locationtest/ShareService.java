package com.family.locationtest;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShareService extends Service {
    public static final String ACTION_RELOAD = "com.family.locationtest.RELOAD";
    public static final String ACTION_STOP = "com.family.locationtest.STOP";
    public static final String ACTION_ALIVE = "com.family.locationtest.ALIVE";
    private static final int NOTE_ID = 7;
    private static final String CH_SHARE = "sharing";
    private static final String CH_PLACES = "places";

    private HandlerThread thread;
    private Handler handler;
    private LocationManager locations;
    private Location lastFix;
    private boolean moving;
    private boolean looping;
    private long lastSend;
    private long armedListen = -1;
    private int cellLevel = -1;
    private TelephonyManager telephony;
    private PhoneStateListener phoneListener;
    private boolean hasAccepted;
    private double acceptedLat;
    private double acceptedLon;
    private long acceptedMono;
    private String acceptedProv = "";
    private boolean aliveQueued;
    private int queuedAlive = -1;
    private long lastAliveAt;
    private long lastAliveTry;
    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location == null || dropFix(location)) return;
            rememberFix(location);
            lastFix = location;
            Prefs.saveFix(ShareService.this, location.getLatitude(), location.getLongitude());
            considerSend(location);
        }
    };

    private final Runnable fetchTask = new Runnable() {
        @Override
        public void run() {
            pullGroup();
            if (handler != null) handler.postDelayed(this, fetchWait());
        }
    };

    private final Runnable sendTask = new Runnable() {
        @Override
        public void run() {
            if (locationOff() || !hasLocationPermission()) maybeHeartbeat();
            else if (lastFix != null) considerSend(lastFix);
            ensureListen();
            if (handler != null) handler.postDelayed(this, sendWait());
        }
    };

    private final Runnable aliveTask = new Runnable() {
        @Override
        public void run() {
            aliveQueued = false;
            if (handler == null) return;
            int every = Prefs.aliveEvery(ShareService.this);
            if (every <= 0) {
                queuedAlive = 0;
                return;
            }
            long period = every * 1000L;
            long now = System.currentTimeMillis();
            if (lastAliveAt <= 0 || now - lastAliveAt >= period) {
                if (postAlive()) lastAliveAt = System.currentTimeMillis();
                else lastAliveTry = System.currentTimeMillis();
            }
            scheduleAlive();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        thread = new HandlerThread("family-share");
        thread.start();
        handler = new Handler(thread.getLooper());
        channels();
        watchSignal();
        loadAccepted();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.setShare(this, false);
        }
        if (!Prefs.share(this)) {
            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean aliveOnly = intent != null && ACTION_ALIVE.equals(intent.getAction());
        goForeground(locationOff() ? "Location is off" : "Sharing with " + Prefs.group(this));
        if (aliveOnly && looping) {
            ensureAlive();
            return START_STICKY;
        }
        arm();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        try {
            locations.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        if (telephony != null && phoneListener != null) {
            try {
                telephony.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
            } catch (Exception ignored) {
            }
        }
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    private void arm() {
        looping = true;
        handler.removeCallbacks(fetchTask);
        handler.removeCallbacks(sendTask);
        try {
            locations.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                sendProfile();
            }
        });
        handler.post(fetchTask);
        handler.post(sendTask);
        ensureAlive();
        if (!hasLocationPermission() || locationOff()) {
            goForeground("Location is off");
            return;
        }
        armedListen = -1;
        ensureListen();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void considerSend(Location location) {
        if (locationOff() || !hasLocationPermission()) {
            maybeHeartbeat();
            return;
        }
        boolean stoppedOnly = Prefs.onlyStopped(this);
        boolean isMoving = location.hasSpeed() && location.getSpeed() > 0.8f;
        boolean faster = highway(location) || (Prefs.chargeFast(this) && isCharging());
        if (stoppedOnly && isMoving && !faster) {
            moving = true;
            return;
        }
        long now = System.currentTimeMillis();
        long need = sendWait();
        boolean due = lastSend == 0 || now - lastSend >= need;
        boolean justStopped = stoppedOnly && moving;
        if (!justStopped && !due) return;
        moving = false;
        if (postLocation(location)) lastSend = now;
    }

    private long paceWait() {
        if ("custom".equals(Prefs.pace(this))) return Prefs.customMillis(this);
        boolean movingNow = lastFix != null && lastFix.hasSpeed() && lastFix.getSpeed() > 0.8f;
        if (Presence.mapOpen) return 5000L;
        if (Prefs.fast(this)) return movingNow ? 30000L : 300000L;
        return movingNow ? 90000L : 900000L;
    }

    private long sendWait() {
        long wait = paceWait();
        if (Prefs.chargeFast(this) && isCharging()) {
            long charge = Prefs.chargeSeconds(this) * 1000L;
            if (charge < 5000L) charge = 5000L;
            if (charge < wait) wait = charge;
        }
        if (highway(lastFix)) wait = Math.min(wait, 5000L);
        return wait;
    }

    private long listenInterval() {
        if (Prefs.realtimeFast(this)) {
            if (highway(lastFix)) return 5000L;
            if (lastFix != null && lastFix.hasSpeed() && lastFix.getSpeed() >= 8f) return 5000L;
            return Math.min(sendWait(), 15000L);
        }
        return sendWait();
    }

    private boolean highway(Location location) {
        return Prefs.realtimeFast(this) && location != null && location.hasSpeed() && location.getSpeed() >= 13.4112f;
    }

    private boolean isCharging() {
        try {
            android.content.Intent battery = registerReceiver(null, new IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean locationOff() {
        if (!hasLocationPermission()) return true;
        try {
            boolean gps = locations.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean net = locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            return !gps && !net;
        } catch (Exception e) {
            return true;
        }
    }

    private void ensureListen() {
        if (locationOff() || !hasLocationPermission() || thread == null) {
            armedListen = -1;
            return;
        }
        long want = listenInterval();
        if (want == armedListen) return;
        armedListen = want;
        try {
            locations.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        try {
            if (locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, want, 0f, listener, thread.getLooper());
            }
        } catch (Exception ignored) {
        }
        try {
            if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, want, 0f, listener, thread.getLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private long fetchWait() {
        if (highway(lastFix) || (Prefs.chargeFast(this) && isCharging())) return Math.min(sendWait(), 15000L);
        if ("custom".equals(Prefs.pace(this))) return Prefs.customMillis(this);
        if (Presence.mapOpen) return 15000L;
        return Prefs.fast(this) ? 120000L : 900000L;
    }

    private void stamp(JSONObject json) throws Exception {
        json.put("userId", Prefs.userId(this));
        json.put("groupId", Prefs.group(this));
        json.put("ts", System.currentTimeMillis() / 1000L);
        json.put("signal", signalBars());
        json.put("every", (int) Math.max(1L, sendWait() / 1000L));
        int battery = batteryPercent();
        if (battery >= 0) json.put("battery", battery);
        json.put("mono", SystemClock.elapsedRealtime());
        json.put("wifi", wifiConnected(this));
        json.put("ssid", wifiSsid(this));
    }

    private boolean postLocation(Location location) {
        String base = Net.base(this);
        if (base.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject();
            stamp(json);
            json.put("locationOff", false);
            json.put("lat", location.getLatitude());
            json.put("lon", location.getLongitude());
            json.put("mono", fixElapsed(location));
            if (location.hasAccuracy()) json.put("acc", (double) location.getAccuracy());
            json.put("prov", LocationManager.GPS_PROVIDER.equals(location.getProvider()) ? "gps" : "network");
            if (location.hasSpeed()) json.put("speed", location.getSpeed());
            Net.Result result = Net.post(base + "/send", json.toString());
            if (result.error == null && result.code >= 200 && result.code < 300) {
                goForeground("Sharing with " + Prefs.group(this));
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void maybeHeartbeat() {
        long now = System.currentTimeMillis();
        long need = sendWait();
        if (lastSend != 0 && now - lastSend < need) return;
        if (postHeartbeat()) lastSend = now;
    }

    private boolean postHeartbeat() {
        String base = Net.base(this);
        if (base.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject();
            stamp(json);
            json.put("locationOff", true);
            if (lastFix != null) {
                json.put("lat", lastFix.getLatitude());
                json.put("lon", lastFix.getLongitude());
            }
            Net.Result result = Net.post(base + "/send", json.toString());
            if (result.error == null && result.code >= 200 && result.code < 300) {
                goForeground("Location is off");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void loadAccepted() {
        if (!Prefs.hasAccepted(this)) return;
        hasAccepted = true;
        acceptedLat = Prefs.acceptedLat(this);
        acceptedLon = Prefs.acceptedLon(this);
        acceptedMono = Prefs.acceptedMono(this);
        acceptedProv = Prefs.acceptedProv(this);
    }

    private long fixElapsed(Location location) {
        try {
            long nanos = location.getElapsedRealtimeNanos();
            if (nanos > 0L) return nanos / 1000000L;
        } catch (Exception ignored) {
        }
        return SystemClock.elapsedRealtime();
    }

    private boolean dropFix(Location location) {
        if (!hasAccepted || location == null) return false;
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        long mono = fixElapsed(location);
        long gap = mono - acceptedMono;
        boolean gps = LocationManager.GPS_PROVIDER.equals(location.getProvider());
        float acc = location.hasAccuracy() ? location.getAccuracy() : -1f;
        if (gap >= 0 && gap < 120000L) {
            double dist = meters(acceptedLat, acceptedLon, lat, lon);
            double sec = gap / 1000.0;
            double speed = sec < 0.05 ? (dist > 1.0 ? 1000.0 : 0.0) : dist / sec;
            if (speed > 70.0) return true;
        }
        boolean coarse = !gps || acc > 80f;
        boolean recentGps = "gps".equals(acceptedProv) && gap >= 0 && gap < 90000L;
        if (coarse && recentGps) return true;
        String ssid = wifiSsid(this);
        String place = Prefs.placeSsid(this);
        if (!ssid.isEmpty() && ssid.equals(place)) {
            double dist = meters(acceptedLat, acceptedLon, lat, lon);
            if (dist > 80.0) return true;
        }
        return false;
    }

    private void rememberFix(Location location) {
        acceptedLat = location.getLatitude();
        acceptedLon = location.getLongitude();
        acceptedMono = fixElapsed(location);
        acceptedProv = LocationManager.GPS_PROVIDER.equals(location.getProvider()) ? "gps" : "network";
        hasAccepted = true;
        float acc = location.hasAccuracy() ? location.getAccuracy() : -1f;
        Prefs.saveAccepted(this, acceptedLat, acceptedLon, acceptedMono, acceptedProv, acc);
    }

    private boolean postAlive() {
        String base = Net.base(this);
        if (base.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject();
            stamp(json);
            json.put("alive", true);
            json.put("locationOff", locationOff());
            Net.Result result = Net.post(base + "/send", json.toString());
            return result.error == null && result.code >= 200 && result.code < 300;
        } catch (Exception ignored) {
        }
        return false;
    }

    private void ensureAlive() {
        if (handler == null) return;
        if (Looper.myLooper() == handler.getLooper()) scheduleAlive();
        else handler.post(new Runnable() {
            @Override
            public void run() {
                scheduleAlive();
            }
        });
    }

    private void scheduleAlive() {
        if (handler == null) return;
        int every = Prefs.aliveEvery(this);
        if (every <= 0) {
            handler.removeCallbacks(aliveTask);
            aliveQueued = false;
            queuedAlive = 0;
            return;
        }
        long period = every * 1000L;
        long now = System.currentTimeMillis();
        long wait;
        if (lastAliveAt > 0 && now - lastAliveAt < period) {
            wait = lastAliveAt + period - now;
        } else if (lastAliveTry > lastAliveAt) {
            wait = lastAliveTry + period - now;
            if (wait < 0) wait = 0;
        } else {
            wait = 0;
        }
        if (aliveQueued && queuedAlive == every) return;
        handler.removeCallbacks(aliveTask);
        aliveQueued = true;
        queuedAlive = every;
        handler.postDelayed(aliveTask, wait);
    }

    static boolean wifiConnected(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi == null) return false;
            android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
            return info != null && info.getNetworkId() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    static String wifiSsid(Context context) {
        try {
            if (!wifiConnected(context)) return "";
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi == null) return "";
            android.net.wifi.WifiInfo info = wifi.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return "";
            return cleanSsid(info.getSSID());
        } catch (Exception e) {
            return "";
        }
    }

    static String cleanSsid(String ssid) {
        if (ssid == null) return "";
        String s = ssid.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isEmpty() || "<unknown ssid>".equalsIgnoreCase(s) || "0x".equalsIgnoreCase(s)) return "";
        return s;
    }

    public static void noteGroup(Context context, JSONObject body) {
        if (context == null || body == null) return;
        try {
            Prefs.setAliveEvery(context, body.optInt("aliveEvery", 0));
            rememberPlace(context, body);
        } catch (Exception ignored) {
        }
    }

    public static void nudge(Context context) {
        if (context == null || !Prefs.share(context)) return;
        Intent intent = new Intent(context, ShareService.class);
        intent.setAction(ACTION_ALIVE);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void rememberPlace(Context context, JSONObject body) {
        JSONArray members = body.optJSONArray("members");
        if (members == null) return;
        String me = Prefs.userId(context);
        String area = "";
        boolean hasLoc = false;
        double lat = 0;
        double lon = 0;
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null || !me.equals(member.optString("userId"))) continue;
            area = member.optString("area", "");
            hasLoc = member.optBoolean("hasLoc");
            lat = member.optDouble("lat", 0);
            lon = member.optDouble("lon", 0);
            break;
        }
        if (area == null) area = "";
        area = area.trim();
        if (area.isEmpty() && hasLoc) {
            JSONArray places = body.optJSONArray("places");
            if (places != null) {
                double best = Double.MAX_VALUE;
                String found = "";
                for (int i = 0; i < places.length(); i++) {
                    JSONObject place = places.optJSONObject(i);
                    if (place == null) continue;
                    double radius = place.optDouble("radius", 100);
                    if (radius < 20) radius = 20;
                    double dist = meters(lat, lon, place.optDouble("lat"), place.optDouble("lon"));
                    if (dist <= radius && dist < best) {
                        best = dist;
                        found = place.optString("name", "");
                    }
                }
                area = found == null ? "" : found.trim();
            }
        }
        if (area.isEmpty() || !wifiConnected(context)) return;
        String ssid = wifiSsid(context);
        if (ssid.isEmpty()) return;
        Prefs.rememberPlaceWifi(context, area, ssid);
    }

    private void watchSignal() {
        try {
            telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (telephony == null) return;
            phoneListener = new PhoneStateListener() {
                @Override
                public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                    if (signalStrength == null) return;
                    if (Build.VERSION.SDK_INT >= 23) {
                        cellLevel = signalStrength.getLevel();
                    } else {
                        int gsm = signalStrength.getGsmSignalStrength();
                        if (gsm == 99 || gsm < 0) cellLevel = -2;
                        else if (gsm >= 12) cellLevel = 4;
                        else if (gsm >= 8) cellLevel = 3;
                        else if (gsm >= 5) cellLevel = 2;
                        else if (gsm >= 1) cellLevel = 1;
                        else cellLevel = 0;
                    }
                }
            };
            telephony.listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        } catch (Exception ignored) {
        }
    }

    private int signalBars() {
        if (cellLevel >= 0 && cellLevel <= 4) return cellLevel;
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null && wifi.getConnectionInfo() != null) {
                int rssi = wifi.getConnectionInfo().getRssi();
                if (rssi >= -55 && rssi < 0) return 4;
                if (rssi >= -67) return 3;
                if (rssi >= -75) return 2;
                if (rssi >= -85 && rssi < 0) return 1;
                if (rssi < -100) return cellLevel;
            }
        } catch (Exception ignored) {
        }
        return cellLevel;
    }

    private int batteryPercent() {
        try {
            BatteryManager battery = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (battery == null) return -1;
            int pct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (pct < 0 || pct > 100) return -1;
            return pct;
        } catch (Exception e) {
            return -1;
        }
    }

    private void sendProfile() {
        String base = Net.base(this);
        if (base.isEmpty()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("userId", Prefs.userId(this));
            json.put("groupId", Prefs.group(this));
            json.put("name", Prefs.name(this));
            json.put("phone", Prefs.phone(this));
            String image = readProfileImage();
            if (image != null) json.put("image", image);
            Net.post(base + "/profile", json.toString());
        } catch (Exception ignored) {
        }
    }

    private String readProfileImage() {
        File file = new File(getFilesDir(), "profile.jpg");
        if (!file.exists() || file.length() == 0 || file.length() > 1400000) return null;
        try {
            FileInputStream in = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private void pullGroup() {
        String base = Net.base(this);
        if (base.isEmpty()) return;
        String group = Prefs.group(this);
        Net.Result result = Net.get(base + "/group?groupId=" + Net.query(group));
        if (result.error != null || result.code != 200) return;
        try {
            JSONObject body = new JSONObject(result.body);
            noteGroup(this, body);
            ensureAlive();
            JSONArray members = body.optJSONArray("members");
            JSONArray places = body.optJSONArray("places");
            if (members == null || places == null) return;
            JSONObject state = Prefs.fenceState(this);
            String me = Prefs.userId(this);
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.getJSONObject(i);
                if (!member.optBoolean("hasLoc")) continue;
                String userId = member.optString("userId");
                if (userId.equals(me)) continue;
                double lat = member.optDouble("lat");
                double lon = member.optDouble("lon");
                String who = member.optString("name", "Someone");
                for (int p = 0; p < places.length(); p++) {
                    JSONObject place = places.getJSONObject(p);
                    String placeId = place.optString("id");
                    boolean wantIn = Prefs.notifyArrive(this, placeId);
                    boolean wantOut = Prefs.notifyLeave(this, placeId);
                    if (!wantIn && !wantOut) continue;
                    boolean inside = meters(lat, lon, place.optDouble("lat"), place.optDouble("lon")) <= place.optDouble("radius", 100);
                    String key = userId + "|" + placeId;
                    if (!state.has(key)) {
                        state.put(key, inside);
                        continue;
                    }
                    boolean was = state.optBoolean(key);
                    boolean master = Prefs.placeAlerts(this);
                    if (inside && !was && wantIn && master) {
                        alert(who + " arrived at " + place.optString("name"));
                    } else if (!inside && was && wantOut && master) {
                        alert(who + " left " + place.optString("name"));
                    }
                    state.put(key, inside);
                }
            }
            Prefs.fenceState(this, state);
        } catch (Exception ignored) {
        }
    }

    private static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dphi / 2) * Math.sin(dphi / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void channels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel share = new NotificationChannel(CH_SHARE, "Location sharing", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel places = new NotificationChannel(CH_PLACES, "Place alerts", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(share);
        nm.createNotificationChannel(places);
    }

    private void goForeground(String text) {
        boolean off = text != null && text.startsWith("Location is off");
        String group = Prefs.group(this);
        String body = off
                ? "Location is off. Swipe this away to stop sharing your location."
                : "Sharing with " + group + ". Swipe this away to stop sharing your location.";
        Intent open = new Intent(this, MapActivity.class);
        Intent stop = new Intent(this, ShareService.class);
        stop.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
        PendingIntent delete = PendingIntent.getService(this, 2, stop, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_SHARE)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Family")
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setOngoing(false)
                .setDeleteIntent(delete)
                .setContentIntent(pending);
        Notification note = builder.build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTE_ID, note);
        }
    }

    private void alert(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_PLACES)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Family")
                .setContentText(text)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, ShareService.class);
        intent.setAction(ACTION_RELOAD);
        if (!Prefs.share(context)) {
            context.stopService(intent);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }
}
