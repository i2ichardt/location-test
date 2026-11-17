package com.family.locationtest;

import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MapActivity extends Activity {
    private WebView map;
    private TextView statusText;
    private LinearLayout sheet;
    private LinearLayout peopleList;
    private View peopleTab;
    private LinearLayout personCard;
    private TextView cardName;
    private TextView cardArrived;
    private TextView cardStay;
    private JSONArray lastMembers = new JSONArray();
    private String lastPlaces = "[]";
    private String openCardId = "";
    private boolean pageReady;
    private boolean sheetOpen;
    private boolean curtainDrag;
    private boolean curtainAnimating;
    private float curtainAnchorY;
    private float curtainAnchorTy;
    private FrameLayout photoPop;
    private ImageView photoPopImage;
    private boolean curtainWaiting;
    private float curtainClosed;
    private float curtainY = -1f;
    private int laidSheetHeight;
    private boolean satellite;
    private String lastMarkers = "[]";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashMap<String, Bitmap> faces = new HashMap<String, Bitmap>();
    private final HashMap<String, Long> faceStamps = new HashMap<String, Long>();
    private final GroupWatch watch = new GroupWatch();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        map = findViewById(R.id.map);
        statusText = findViewById(R.id.statusText);
        sheet = findViewById(R.id.sheet);
        peopleList = findViewById(R.id.peopleList);
        peopleTab = findViewById(R.id.peopleTab);
        personCard = findViewById(R.id.personCard);
        cardName = findViewById(R.id.cardName);
        cardArrived = findViewById(R.id.cardArrived);
        cardStay = findViewById(R.id.cardStay);

        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.getSettings().setAllowFileAccess(true);
        map.addJavascriptInterface(new Bridge(), "Android");
        map.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (satellite) map.evaluateJavascript("setSatellite(true)", null);
                pushMarkers();
                pushPlaces();
            }
        });
        map.loadUrl("file:///android_asset/map.html");

        findViewById(R.id.settingsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MapActivity.this, SettingsActivity.class));
            }
        });
        ((SwipeCard) personCard).setOnDismiss(new Runnable() {
            @Override
            public void run() {
                hidePersonCard();
            }
        });
        satellite = Prefs.satellite(this);
        final Button satelliteButton = findViewById(R.id.satelliteButton);
        satelliteButton.setText(satellite ? "Map" : "Satellite");
        sheet.setTranslationY(4000);
        ((CurtainSheet) sheet).setPull(new CurtainSheet.Pull() {
            @Override
            public void onDown(float rawY) {
                curtainAnchorY = rawY;
                curtainAnchorTy = sheet.getTranslationY();
            }

            @Override
            public void onMove(float rawY) {
                if (!curtainDrag) {
                    curtainDrag = true;
                    curtainAnimating = false;
                    sheet.animate().cancel();
                }
                float next = curtainAnchorTy + (rawY - curtainAnchorY);
                if (next < 0) next = 0;
                if (next > curtainClosed) next = curtainClosed;
                curtainY = next;
                sheet.setTranslationY(next);
                followCurtain();
            }

            @Override
            public void onUp(float rawY, boolean tap) {
                curtainDrag = false;
                boolean open;
                if (tap) open = sheet.getTranslationY() > curtainClosed * 0.5f;
                else open = sheet.getTranslationY() < curtainClosed * 0.5f;
                sheetOpen = open;
                slideCurtain(open ? 0 : curtainClosed, 180);
            }
        });
        satelliteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                satellite = !satellite;
                Prefs.setSatellite(MapActivity.this, satellite);
                satelliteButton.setText(satellite ? "Map" : "Satellite");
                if (pageReady) map.evaluateJavascript("setSatellite(" + satellite + ")", null);
            }
        });
        sheet.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top != oldBottom - oldTop) placeCurtain(false);
            }
        });
        sheet.post(new Runnable() {
            @Override
            public void run() {
                placeCurtain(false);
            }
        });
        statusText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pageReady) map.evaluateJavascript("fitGroup()", null);
            }
        });
        askPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Presence.mapOpen = true;
        Net.forgetRoute();
        satellite = Prefs.satellite(this);
        ((Button) findViewById(R.id.satelliteButton)).setText(satellite ? "Map" : "Satellite");
        if (pageReady) map.evaluateJavascript("setSatellite(" + satellite + ")", null);
        if (hasLocation()) ShareService.start(this);
        watch.start(this, new GroupWatch.Listener() {
            @Override
            public void onGroup(final String body) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        applyGroup(body);
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        Presence.mapOpen = false;
        watch.stop();
        if (hasLocation()) ShareService.start(this);
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

    private void applyGroup(String raw) {
        final String label = Net.routeLabel(this);
        try {
            JSONObject body = new JSONObject(raw);
            JSONArray members = body.optJSONArray("members");
            if (members == null) members = new JSONArray();
            JSONArray markers = new JSONArray();
            JSONArray placePins = new JSONArray();
            int shown = 0;
            JSONArray places = body.optJSONArray("places");
            if (places != null && Prefs.showPlaceNames(this)) {
                for (int i = 0; i < places.length(); i++) {
                    JSONObject place = places.optJSONObject(i);
                    if (place == null) continue;
                    JSONObject pin = new JSONObject();
                    pin.put("name", place.optString("name"));
                    pin.put("lat", place.optDouble("lat"));
                    pin.put("lon", place.optDouble("lon"));
                    placePins.put(pin);
                }
            }
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
                long imageTs = member.optLong("imageTs");
                boolean hasImage = member.optBoolean("hasImage");
                if (hasImage && ensureFace(userId, imageTs)) {
                    pin.put("icon", dataUrl(faces.get(userId)));
                } else if (hasImage) {
                    pin.put("waitImage", true);
                }
                markers.put(pin);
            }
            statusText.setText(label + " · " + shown + (shown == 1 ? " person" : " people"));
            lastMarkers = markers.toString();
            lastPlaces = placePins.toString();
            lastMembers = members;
            pushMarkers();
            pushPlaces();
            fillPeople(members);
            if (!openCardId.isEmpty() && personCard.getVisibility() == View.VISIBLE) {
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.optJSONObject(i);
                    if (member != null && openCardId.equals(member.optString("userId"))) bindPersonCard(member, false);
                }
            }
            fetchMissingFaces(members);
        } catch (Exception e) {
            statusText.setText(label + " · bad reply");
        }
    }

    private void fetchMissingFaces(final JSONArray members) {
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean changed = false;
                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.optJSONObject(i);
                    if (member == null || !member.optBoolean("hasImage")) continue;
                    String userId = member.optString("userId");
                    long stamp = member.optLong("imageTs");
                    Long have = faceStamps.get(userId);
                    if (have != null && have.longValue() == stamp && faces.containsKey(userId)) continue;
                    String icon = faceIcon(base, userId, stamp);
                    if (icon != null) changed = true;
                }
                if (changed) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loadGroup();
                        }
                    });
                }
            }
        }).start();
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
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(label + " · can't reach server");
                        }
                    });
                    return;
                }
                final String body = result.body;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        applyGroup(body);
                    }
                });
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
        rememberFace(userId, stamp, small);
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

    private void pushPlaces() {
        if (!pageReady) return;
        map.evaluateJavascript("setPlaces(" + (Prefs.showPlaceNames(this) ? lastPlaces : "[]") + ")", null);
    }

    private void placeCurtain(boolean animate) {
        int handle = peopleTab.getHeight();
        if (handle <= 0 || sheet.getHeight() <= 0) {
            if (!curtainWaiting) {
                curtainWaiting = true;
                sheet.post(new Runnable() {
                    @Override
                    public void run() {
                        curtainWaiting = false;
                        placeCurtain(false);
                    }
                });
            }
            return;
        }
        int closed = sheet.getHeight() - handle;
        if (closed < 0) closed = 0;
        curtainClosed = closed;
        if (curtainDrag || curtainAnimating) return;
        int height = sheet.getHeight();
        if (curtainY < 0) curtainY = closed;
        else if (laidSheetHeight > 0 && height != laidSheetHeight) {
            float visible = laidSheetHeight - curtainY;
            curtainY = height - visible;
        }
        laidSheetHeight = height;
        if (curtainY < 0) curtainY = 0;
        if (curtainY > closed) curtainY = closed;
        if (animate) slideCurtain(curtainY, 180);
        else if (Math.abs(sheet.getTranslationY() - curtainY) > 0.5f) {
            sheet.setTranslationY(curtainY);
            followCurtain();
        }
    }

    private void slideCurtain(final float target, long duration) {
        curtainY = target;
        if (duration <= 0 || Math.abs(sheet.getTranslationY() - target) < 1f) {
            curtainAnimating = false;
            sheet.setTranslationY(target);
            followCurtain();
            return;
        }
        curtainAnimating = true;
        sheet.animate().translationY(target).setDuration(duration).setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                followCurtain();
            }
        }).withEndAction(new Runnable() {
            @Override
            public void run() {
                curtainAnimating = false;
                followCurtain();
            }
        }).start();
    }

    private void followCurtain() {
        if (personCard.getVisibility() != View.VISIBLE) return;
        float visible = sheet.getHeight() - sheet.getTranslationY();
        if (visible < 0) visible = 0;
        personCard.setTranslationY(-(visible + dp(8)));
    }

    private void fitPeopleScroll() {
        ScrollView scroll = findViewById(R.id.peopleScroll);
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.48f);
        int width = scroll.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels - dp(32);
        peopleList.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int next = Math.min(Math.max(peopleList.getMeasuredHeight(), dp(4)), max);
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        if (lp.height != next) {
            lp.height = next;
            scroll.setLayoutParams(lp);
        }
        sheet.post(new Runnable() {
            @Override
            public void run() {
                placeCurtain(false);
            }
        });
    }

    private void hidePersonCard() {
        openCardId = "";
        personCard.setTranslationX(0);
        personCard.setVisibility(View.GONE);
    }

    private File faceFile(String userId, long stamp) {
        return new File(getCacheDir(), "face_" + userId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + stamp + ".jpg");
    }

    private void rememberFace(String userId, long stamp, Bitmap bitmap) {
        faces.put(userId, bitmap);
        faceStamps.put(userId, Long.valueOf(stamp));
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(faceFile(userId, stamp));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.close();
            File[] old = getCacheDir().listFiles();
            String prefix = "face_" + userId.replaceAll("[^A-Za-z0-9_-]", "_") + "_";
            String keep = faceFile(userId, stamp).getName();
            if (old != null) {
                for (int i = 0; i < old.length; i++) {
                    if (old[i].getName().startsWith(prefix) && !old[i].getName().equals(keep)) old[i].delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean ensureFace(String userId, long stamp) {
        Long have = faceStamps.get(userId);
        if (have != null && have.longValue() == stamp && faces.containsKey(userId)) return true;
        File file = faceFile(userId, stamp);
        if (!file.exists()) return false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return false;
        faces.put(userId, bitmap);
        faceStamps.put(userId, Long.valueOf(stamp));
        return true;
    }

    private void fillPeople(JSONArray members) {
        peopleList.removeAllViews();
        if (members.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Nobody in this group yet. Save your profile in Settings.");
            empty.setTextColor(0xFF5C584F);
            empty.setPadding(8, 12, 8, 12);
            peopleList.addView(empty);
        } else {
            for (int i = 0; i < members.length(); i++) {
                JSONObject member = members.optJSONObject(i);
                if (member == null) continue;
                peopleList.addView(personBubble(member));
            }
        }
        fitPeopleScroll();
    }

    private View personBubble(final JSONObject member) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setClickable(true);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView face = new ImageView(this);
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        face.setLayoutParams(faceLp);
        String userId = member.optString("userId");
        if (faces.containsKey(userId)) face.setImageBitmap(faces.get(userId));
        else face.setBackgroundColor(0xFFE7E2D6);
        final String faceId = userId;
        face.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bitmap = faces.get(faceId);
                if (bitmap != null) showPhoto(bitmap);
            }
        });
        top.addView(face);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(member.optString("name", "Someone"));
        name.setTextColor(0xFF1C1B19);
        name.setTextSize(17);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView line1 = new TextView(this);
        TextView line2 = new TextView(this);
        line1.setTextColor(0xFF5C584F);
        line2.setTextColor(0xFF5C584F);
        line1.setTextSize(14);
        line2.setTextSize(14);
        fillStatus(member, line1, line2);
        words.addView(name);
        words.addView(line1);
        words.addView(line2);
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        words.setLayoutParams(wordsLp);
        top.addView(words);
        int battery = member.optInt("battery", -1);
        if (battery >= 0) top.addView(batteryBadge(battery));
        card.addView(top);

        final double lat = member.optDouble("lat", Double.NaN);
        final double lon = member.optDouble("lon", Double.NaN);
        final boolean hasLoc = member.optBoolean("hasLoc");
        final String phone = member.optString("phone", "");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        if (hasLoc) actions.addView(wideButton("Center", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                centerOn(lat, lon);
            }
        }));
        if (hasLoc) actions.addView(wideButton("Directions", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTrip(lat, lon);
            }
        }));
        actions.addView(wideButton("Call", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            }
        }));
        actions.addView(wideButton("Text", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + phone)));
            }
        }));
        card.addView(actions);
        return card;
    }

    private Button wideButton(String text, View.OnClickListener click) {
        Button button = smallButton(text, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        button.setLayoutParams(lp);
        return button;
    }

    private Button smallButton(String text, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFF1A1916);
        button.setTextSize(13);
        button.setBackgroundResource(R.drawable.pill_light);
        button.setStateListAnimator(null);
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

    private void centerOn(double lat, double lon) {
        if (pageReady) map.evaluateJavascript("showPerson(" + lat + "," + lon + ")", null);
    }

    private void openStreet(double lat, double lon) {
        centerOn(lat, lon);
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
        for (int i = 0; i < lastMembers.length(); i++) {
            JSONObject member = lastMembers.optJSONObject(i);
            if (member != null && userId.equals(member.optString("userId"))) {
                showPersonCard(member);
                return;
            }
        }
    }

    private void showPersonCard(final JSONObject member) {
        String id = member.optString("userId");
        if (id.equals(openCardId) && personCard.getVisibility() == View.VISIBLE) {
            hidePersonCard();
            return;
        }
        openCardId = id;
        bindPersonCard(member, true);
    }

    private void fillStatus(JSONObject member, TextView line1, TextView line2) {
        if (!member.optBoolean("hasLoc")) {
            line1.setText("No location yet");
            line2.setText("");
            line2.setVisibility(View.GONE);
            return;
        }
        line2.setVisibility(View.VISIBLE);
        long now = System.currentTimeMillis() / 1000L;
        if (member.optBoolean("traveling")) {
            int mph = (int) Math.round(member.optDouble("speed", 0) * 2.23694);
            if (mph < 1) mph = 1;
            long departed = member.optLong("departed", member.optLong("ts"));
            long mins = departed > 0 && now > departed ? (now - departed) / 60L : 0;
            String from = member.optString("departedFrom", "");
            if (from.isEmpty()) from = "this spot";
            line1.setText("Traveling at " + mph + " MPH");
            String departedLine = mins < 1 ? "Departed from " + from + " - just now" : "Departed from " + from + " - " + agoPhrase(mins);
            String more = extraLine(member);
            line2.setText(more.isEmpty() ? departedLine : departedLine + "\n" + more);
            return;
        }
        long arrived = member.optLong("arrived", member.optLong("ts"));
        long mins = arrived > 0 && now > arrived ? (now - arrived) / 60L : 0;
        String area = member.optString("area", "");
        if (area.isEmpty()) area = "this spot";
        line1.setText(mins < 1 ? "Arrived to " + area + " - just now" : "Arrived to " + area + " - " + agoPhrase(mins));
        String more = extraLine(member);
        if (more.isEmpty()) {
            line2.setText("");
            line2.setVisibility(View.GONE);
        } else {
            line2.setText(more);
            line2.setVisibility(View.VISIBLE);
        }
    }

    private String agoPhrase(long mins) {
        if (mins < 60) return mins + (mins == 1 ? " min ago" : " mins ago");
        long hours = mins / 60;
        long rest = mins % 60;
        String text = hours + (hours == 1 ? " hour" : " hours");
        if (rest > 0) text += " " + rest + (rest == 1 ? " min" : " mins");
        return text + " ago";
    }

    private String extraLine(JSONObject member) {
        if (!member.optBoolean("hasLoc")) return "";
        return awayText(member.optDouble("lat"), member.optDouble("lon"));
    }

    private View batteryBadge(int percent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        BatteryMeter meter = new BatteryMeter(this);
        int level = percent;
        if (level < 0) level = 0;
        if (level > 100) level = 100;
        meter.setPercent(level);
        LinearLayout.LayoutParams meterLp = new LinearLayout.LayoutParams(dp(28), dp(14));
        meterLp.rightMargin = dp(4);
        meter.setLayoutParams(meterLp);
        TextView pct = new TextView(this);
        pct.setText(level + "%");
        pct.setTextColor(0xFF1A1916);
        pct.setTextSize(13);
        pct.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(meter);
        row.addView(pct);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = android.view.Gravity.TOP;
        rowLp.leftMargin = dp(8);
        row.setLayoutParams(rowLp);
        return row;
    }

    private static final class BatteryMeter extends View {
        private int percent;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BatteryMeter(android.content.Context context) {
            super(context);
        }

        void setPercent(int percent) {
            this.percent = percent;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float stroke = Math.max(1.5f, h * 0.12f);
            float nub = Math.max(2f, w * 0.08f);
            float bodyRight = w - nub - 1f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(0xFF1A1916);
            RectF body = new RectF(stroke / 2f, stroke / 2f, bodyRight - stroke / 2f, h - stroke / 2f);
            canvas.drawRoundRect(body, h * 0.22f, h * 0.22f, paint);
            paint.setStyle(Paint.Style.FILL);
            float nubH = h * 0.36f;
            canvas.drawRoundRect(new RectF(bodyRight - 0.5f, (h - nubH) / 2f, w - 0.5f, (h + nubH) / 2f), 1.2f, 1.2f, paint);
            float pad = stroke + 1.2f;
            float inner = (body.right - body.left - pad * 2f) * percent / 100f;
            if (percent > 0 && inner < 1.5f) inner = 1.5f;
            if (percent <= 15) paint.setColor(0xFFC8102E);
            else if (percent <= 40) paint.setColor(0xFFC47B16);
            else paint.setColor(0xFF2F6B3A);
            if (inner > 0) {
                canvas.drawRoundRect(new RectF(body.left + pad, body.top + pad, body.left + pad + inner, body.bottom - pad), 2f, 2f, paint);
            }
        }
    }

    private String awayText(double lat, double lon) {
        if (!Prefs.hasFix(this) || Double.isNaN(lat) || Double.isNaN(lon)) return "";
        double meters = haversine(Prefs.lat(this), Prefs.lon(this), lat, lon);
        if (meters < 30) return "Here with you";
        if (meters < 160) return Math.round(meters * 3.28084) + " ft away";
        double miles = meters / 1609.344;
        if (miles < 10) return String.format(Locale.US, "%.1f miles away", miles);
        return Math.round(miles) + " miles away";
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dphi / 2) * Math.sin(dphi / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void showPhoto(Bitmap bitmap) {
        if (photoPop == null) {
            photoPop = new FrameLayout(this);
            photoPop.setBackgroundColor(0xE61A1916);
            photoPopImage = new ImageView(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            int gap = dp(36);
            lp.setMargins(gap, dp(72), gap, dp(72));
            photoPopImage.setLayoutParams(lp);
            photoPopImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            photoPop.addView(photoPopImage);
            addContentView(photoPop, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            photoPop.setOnTouchListener(new View.OnTouchListener() {
                float x;
                float y;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        x = event.getRawX();
                        y = event.getRawY();
                        return true;
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float dx = event.getRawX() - x;
                        float dy = event.getRawY() - y;
                        if (Math.abs(dx) > dp(24) || Math.abs(dy) > dp(24) || (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12))) {
                            photoPop.setVisibility(View.GONE);
                        }
                        return true;
                    }
                    return true;
                }
            });
        }
        photoPopImage.setImageBitmap(bitmap);
        photoPop.setVisibility(View.VISIBLE);
        photoPop.bringToFront();
    }

    private void bindPersonCard(final JSONObject member, boolean moveMap) {
        cardName.setText(member.optString("name", "Someone"));
        LinearLayout batterySlot = findViewById(R.id.cardBattery);
        batterySlot.removeAllViews();
        int battery = member.optInt("battery", -1);
        if (battery >= 0) batterySlot.addView(batteryBadge(battery));
        fillStatus(member, cardArrived, cardStay);
        final double lat = member.optDouble("lat", Double.NaN);
        final double lon = member.optDouble("lon", Double.NaN);
        final boolean hasLoc = member.optBoolean("hasLoc");
        final String phone = member.optString("phone", "");
        findViewById(R.id.cardView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasLoc) openTrip(lat, lon);
            }
        });
        findViewById(R.id.cardCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            }
        });
        findViewById(R.id.cardText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + phone)));
            }
        });
        personCard.setVisibility(View.VISIBLE);
        followCurtain();
        if (moveMap && hasLoc && pageReady) map.evaluateJavascript("showPerson(" + lat + "," + lon + ")", null);
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
