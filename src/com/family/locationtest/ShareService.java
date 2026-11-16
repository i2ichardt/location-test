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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import java.io.File;
import java.io.FileInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShareService extends Service {
    public static final String ACTION_RELOAD = "com.family.locationtest.RELOAD";
    private static final int NOTE_ID = 7;
    private static final String CH_SHARE = "sharing";
    private static final String CH_PLACES = "places";

    private HandlerThread thread;
    private Handler handler;
    private LocationManager locations;
    private Location lastFix;
    private boolean moving;
    private long lastSend;
    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
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
            if (lastFix != null) considerSend(lastFix);
            if (handler != null) handler.postDelayed(this, sendWait());
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.share(this)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        goForeground("Sharing with " + Prefs.group(this));
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
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    private void arm() {
        handler.removeCallbacks(fetchTask);
        handler.removeCallbacks(sendTask);
        try {
            locations.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        if (!hasLocationPermission()) {
            goForeground("Location permission is off");
            return;
        }
        long interval = Presence.mapOpen ? 5000L : (Prefs.fast(this) ? 30000L : 60000L);
        try {
            if (locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0f, listener, thread.getLooper());
            }
        } catch (Exception ignored) {
        }
        try {
            if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, interval, 0f, listener, thread.getLooper());
            }
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
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void considerSend(Location location) {
        boolean stoppedOnly = Prefs.onlyStopped(this);
        boolean isMoving = location.hasSpeed() && location.getSpeed() > 0.8f;
        if (stoppedOnly && isMoving) {
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

    private long sendWait() {
        boolean movingNow = lastFix != null && lastFix.hasSpeed() && lastFix.getSpeed() > 0.8f;
        if (Presence.mapOpen) return 5000L;
        if (Prefs.fast(this)) return movingNow ? 30000L : 300000L;
        return movingNow ? 90000L : 900000L;
    }

    private long fetchWait() {
        if (Presence.mapOpen) return 15000L;
        return Prefs.fast(this) ? 120000L : 900000L;
    }

    private boolean postLocation(Location location) {
        String base = Net.base(this);
        if (base.isEmpty()) return false;
        try {
            JSONObject json = new JSONObject();
            json.put("userId", Prefs.userId(this));
            json.put("groupId", Prefs.group(this));
            json.put("lat", location.getLatitude());
            json.put("lon", location.getLongitude());
            json.put("ts", System.currentTimeMillis() / 1000L);
            Net.Result result = Net.post(base + "/send", json.toString());
            if (result.error == null && result.code >= 200 && result.code < 300) {
                goForeground("Sharing with " + Prefs.group(this));
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
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
        if (!file.exists() || file.length() == 0 || file.length() > 400000) return null;
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
                    if (inside && !was && wantIn) {
                        alert(who + " arrived at " + place.optString("name"));
                    } else if (!inside && was && wantOut) {
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
        Intent open = new Intent(this, MapActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_SHARE)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Family")
                .setContentText(text)
                .setOngoing(true)
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
