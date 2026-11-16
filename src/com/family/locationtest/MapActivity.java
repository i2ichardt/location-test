package com.family.locationtest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class MapActivity extends Activity {
    private WebView map;
    private TextView statusText;
    private LinearLayout sheet;
    private LinearLayout peopleList;
    private Button peopleTab;
    private boolean pageReady;
    private boolean sheetOpen;
    private String lastMarkers = "[]";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashMap<String, Bitmap> faces = new HashMap<String, Bitmap>();
    private final HashMap<String, Long> faceStamps = new HashMap<String, Long>();

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            loadGroup();
            handler.postDelayed(this, Prefs.fetchSeconds(MapActivity.this) * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        map = findViewById(R.id.map);
        statusText = findViewById(R.id.statusText);
        sheet = findViewById(R.id.sheet);
        peopleList = findViewById(R.id.peopleList);
        peopleTab = findViewById(R.id.peopleTab);

        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.getSettings().setAllowFileAccess(true);
        map.addJavascriptInterface(new Bridge(), "Android");
        map.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                pushMarkers();
            }
        });
        map.loadUrl("file:///android_asset/map.html");

        findViewById(R.id.settingsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MapActivity.this, SettingsActivity.class));
            }
        });
        peopleTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheetOpen = !sheetOpen;
                sheet.setVisibility(sheetOpen ? View.VISIBLE : View.GONE);
            }
        });
        askPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Net.forgetRoute();
        if (hasLocation()) ShareService.start(this);
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void askPermissions() {
        if (!hasLocation()) {
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 21);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 22);
        }
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasLocation()) ShareService.start(this);
    }

    private void loadGroup() {
        final String base = Net.base(this);
        final String label = Net.routeLabel(this);
        if (base.isEmpty()) {
            statusText.setText("Add a server address in Settings");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Net.Result result = Net.get(base + "/group?groupId=" + Net.query(Prefs.group(MapActivity.this)));
                if (result.error != null || result.code != 200) {
                    final String err = result.error != null ? result.error : ("HTTP " + result.code);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(label + " · can't reach server");
                        }
                    });
                    return;
                }
                try {
                    JSONObject body = new JSONObject(result.body);
                    JSONArray members = body.optJSONArray("members");
                    if (members == null) members = new JSONArray();
                    JSONArray markers = new JSONArray();
                    int shown = 0;
                    for (int i = 0; i < members.length(); i++) {
                        JSONObject member = members.getJSONObject(i);
                        if (!member.optBoolean("hasLoc")) continue;
                        shown++;
                        JSONObject pin = new JSONObject();
                        String userId = member.optString("userId");
                        String name = member.optString("name", "Someone");
                        pin.put("userId", userId);
                        pin.put("name", name);
                        pin.put("lat", member.optDouble("lat"));
                        pin.put("lon", member.optDouble("lon"));
                        pin.put("letter", name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
                        if (member.optBoolean("hasImage")) {
                            long stamp = member.optLong("imageTs");
                            String icon = faceIcon(base, userId, stamp);
                            if (icon != null) pin.put("icon", icon);
                        }
                        markers.put(pin);
                    }
                    final int count = shown;
                    final String payload = markers.toString();
                    final JSONArray people = members;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(label + " · " + count + (count == 1 ? " person" : " people"));
                            peopleTab.setText(sheetOpen ? "Hide people" : ("People · " + count));
                            lastMarkers = payload;
                            pushMarkers();
                            fillPeople(people);
                        }
                    });
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(label + " · bad reply");
                        }
                    });
                }
            }
        }).start();
    }

    private String faceIcon(String base, String userId, long stamp) {
        Long have = faceStamps.get(userId);
        if (have != null && have.longValue() == stamp && faces.containsKey(userId)) {
            return dataUrl(faces.get(userId));
        }
        Net.Result result = Net.getBytes(base + "/avatar?groupId=" + Net.query(Prefs.group(this)) + "&userId=" + Net.query(userId));
        if (result.error != null || result.raw == null || result.raw.length == 0) return null;
        Bitmap bitmap = BitmapFactory.decodeByteArray(result.raw, 0, result.raw.length);
        if (bitmap == null) return null;
        Bitmap small = Bitmap.createScaledBitmap(bitmap, 84, 84, true);
        faces.put(userId, small);
        faceStamps.put(userId, stamp);
        return dataUrl(small);
    }

    private String dataUrl(Bitmap bitmap) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private void pushMarkers() {
        if (!pageReady) return;
        map.evaluateJavascript("setPeople(" + lastMarkers + ")", null);
    }

    private void fillPeople(JSONArray members) {
        peopleList.removeAllViews();
        if (members.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Nobody in this group yet. Save your profile in Settings.");
            empty.setTextColor(0xFF5C584F);
            empty.setPadding(8, 12, 8, 12);
            peopleList.addView(empty);
            return;
        }
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.optJSONObject(i);
            if (member == null) continue;
            peopleList.addView(personRow(member));
        }
    }

    private View personRow(final JSONObject member) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 8, 4, 8);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView face = new ImageView(this);
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        face.setLayoutParams(faceLp);
        String userId = member.optString("userId");
        if (faces.containsKey(userId)) face.setImageBitmap(faces.get(userId));
        else face.setBackgroundColor(0xFFC8102E);
        row.addView(face);

        TextView name = new TextView(this);
        String label = member.optString("name", "Someone");
        if (!member.optBoolean("hasLoc")) label = label + " · no pin yet";
        name.setText(label);
        name.setTextColor(0xFF1A1916);
        name.setTextSize(16);
        name.setPadding(dp(10), 0, dp(6), 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameLp);
        final double lat = member.optDouble("lat", Double.NaN);
        final double lon = member.optDouble("lon", Double.NaN);
        final boolean hasLoc = member.optBoolean("hasLoc");
        name.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasLoc) openStreet(lat, lon);
            }
        });
        row.addView(name);

        if (hasLoc) {
            row.addView(smallButton("View", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openStreet(lat, lon);
                }
            }));
            row.addView(smallButton("Trip", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openTrip(lat, lon);
                }
            }));
        }
        final String phone = member.optString("phone", "");
        if (!phone.isEmpty()) {
            row.addView(smallButton("Call", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                }
            }));
        }
        return row;
    }

    private Button smallButton(String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFF1A1916);
        button.setTextSize(13);
        button.setBackgroundColor(0xFFE4DDD0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        lp.leftMargin = dp(4);
        button.setLayoutParams(lp);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(click);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openStreet(double lat, double lon) {
        if (pageReady) {
            map.evaluateJavascript("showPerson(" + lat + "," + lon + ")", null);
        }
        Uri uri = Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=" + lat + "," + lon);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void openTrip(double lat, double lon) {
        Uri nav = Uri.parse("google.navigation:q=" + lat + "," + lon);
        Intent intent = new Intent(Intent.ACTION_VIEW, nav);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Uri web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon);
            startActivity(new Intent(Intent.ACTION_VIEW, web));
        }
    }

    private void openPerson(String userId) {
        try {
            JSONArray list = new JSONArray(lastMarkers);
            for (int i = 0; i < list.length(); i++) {
                JSONObject pin = list.getJSONObject(i);
                if (userId.equals(pin.optString("userId"))) {
                    openStreet(pin.optDouble("lat"), pin.optDouble("lon"));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void personClicked(final String userId) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    openPerson(userId);
                }
            });
        }
    }
}
