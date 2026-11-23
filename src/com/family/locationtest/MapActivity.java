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
import android.graphics.Path;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
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
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private LinearLayout navBanner;
    private TextView navTitle;
    private TextView navDetail;
    private boolean navigating;
    private int navToken;
    private double navDestLat;
    private double navDestLon;
    private String navDestName = "";
    private int navStep;
    private long navRerouteAt;
    private String navSpoken = "";
    private TextToSpeech voice;
    private boolean voiceReady;
    private final List<NavStep> navSteps = new ArrayList<NavStep>();
    private final List<double[]> navLine = new ArrayList<double[]>();
    private final LocationListener navListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (!navigating || location == null) return;
            Prefs.saveFix(MapActivity.this, location.getLatitude(), location.getLongitude());
            onNavFix(location.getLatitude(), location.getLongitude());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
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
        personCard = findViewById(R.id.personCard);
        cardName = findViewById(R.id.cardName);
        cardArrived = findViewById(R.id.cardArrived);
        cardStay = findViewById(R.id.cardStay);
        navBanner = findViewById(R.id.navBanner);
        navTitle = findViewById(R.id.navTitle);
        navDetail = findViewById(R.id.navDetail);
        findViewById(R.id.navEnd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopNavigation();
            }
        });

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
        if (navigating) resumeNavigationGps();
    }

    @Override
    protected void onPause() {
        Presence.mapOpen = false;
        watch.stop();
        pauseNavigationGps();
        if (hasLocation()) ShareService.start(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopNavigation();
        if (voice != null) voice.shutdown();
        super.onDestroy();
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

    private void applyGroup(String raw) {
        final String label = Net.routeLabel(this);
        try {
            JSONObject body = new JSONObject(raw);
            int aliveBefore = Prefs.aliveEvery(this);
            ShareService.noteGroup(this, body);
            if (Prefs.aliveEvery(this) != aliveBefore) ShareService.nudge(this);
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
                pin.put("traveling", member.optBoolean("traveling"));
                pin.put("speed", member.optDouble("speed", 0));
                pin.put("ts", member.optLong("ts", 0));
                pin.put("silent", member.optInt("signal", -1) <= -2);
                pin.put("gpsOff", member.optBoolean("locationOff"));
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
        try {
            FileOutputStream full = new FileOutputStream(faceFullFile(userId, stamp));
            full.write(result.raw);
            full.close();
        } catch (Exception ignored) {
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(result.raw, 0, result.raw.length);
        if (bitmap == null) return null;
        Bitmap small = bitmap.getWidth() <= 96 ? bitmap : Bitmap.createScaledBitmap(bitmap, 96, 96, true);
        rememberFace(userId, stamp, small);
        return dataUrl(small);
    }

    private File faceFullFile(String userId, long stamp) {
        return new File(getCacheDir(), "full_" + userId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + stamp + ".jpg");
    }

    private void showLargePhoto(final String userId, final long stamp) {
        File file = faceFullFile(userId, stamp);
        if (file.exists()) {
            Bitmap big = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (big != null) {
                showPhoto(big);
                return;
            }
        }
        Bitmap small = faces.get(userId);
        if (small != null) showPhoto(small);
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Net.Result result = Net.getBytes(base + "/avatar?groupId=" + Net.query(Prefs.group(MapActivity.this)) + "&userId=" + Net.query(userId));
                if (result.error != null || result.raw == null || result.raw.length == 0) return;
                try {
                    FileOutputStream full = new FileOutputStream(faceFullFile(userId, stamp));
                    full.write(result.raw);
                    full.close();
                } catch (Exception ignored) {
                    return;
                }
                final Bitmap big = BitmapFactory.decodeByteArray(result.raw, 0, result.raw.length);
                if (big == null) return;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showPhoto(big);
                    }
                });
            }
        }).start();
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
        final long faceStamp = member.optLong("imageTs");
        face.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLargePhoto(faceId, faceStamp);
            }
        });
        top.addView(face);

        TextView name = new TextView(this);
        name.setText(member.optString("name", "Someone"));
        name.setTextColor(0xFF1C1B19);
        name.setTextSize(17);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = dp(10);
        nameLp.rightMargin = dp(6);
        name.setLayoutParams(nameLp);
        top.addView(name);
        int battery = member.optInt("battery", -1);
        top.addView(statusBadge(member, battery));
        card.addView(top);

        TextView line1 = new TextView(this);
        TextView line2 = new TextView(this);
        line1.setTextColor(0xFF5C584F);
        line2.setTextColor(0xFF5C584F);
        line1.setTextSize(14);
        line2.setTextSize(14);
        widen(line1);
        widen(line2);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lineLp.topMargin = dp(6);
        line1.setLayoutParams(lineLp);
        LinearLayout.LayoutParams line2Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        line2Lp.topMargin = dp(2);
        line2.setLayoutParams(line2Lp);
        fillStatus(member, line1, line2);
        card.addView(line1);
        card.addView(line2);

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
                startNavigation(lat, lon, member.optString("name", ""));
            }
        }));
        actions.addView(wideButton("Call", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) placeCall(phone);
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

    private void startNavigation(double lat, double lon, String name) {
        if (!Prefs.hasFix(this)) {
            Toast.makeText(this, "Still waiting for this phone's location", Toast.LENGTH_SHORT).show();
            return;
        }
        double fromLat = Prefs.lat(this);
        double fromLon = Prefs.lon(this);
        if (meters(fromLat, fromLon, lat, lon) < 40) {
            Toast.makeText(this, "You're already there", Toast.LENGTH_SHORT).show();
            return;
        }
        navigating = true;
        navDestLat = lat;
        navDestLon = lon;
        navDestName = name == null || name.trim().isEmpty() ? "your stop" : name.trim();
        navStep = 0;
        navSpoken = "";
        navSteps.clear();
        navLine.clear();
        navBanner.setVisibility(View.VISIBLE);
        navTitle.setText("Finding the road");
        navDetail.setText("to " + navDestName);
        hidePersonCard();
        sheetOpen = false;
        slideCurtain(curtainClosed, 180);
        ensureVoice();
        resumeNavigationGps();
        fetchRoute(fromLat, fromLon, ++navToken);
    }

    private void stopNavigation() {
        navigating = false;
        navToken++;
        navSteps.clear();
        navLine.clear();
        navSpoken = "";
        pauseNavigationGps();
        if (navBanner != null) navBanner.setVisibility(View.GONE);
        if (pageReady) map.evaluateJavascript("clearRoute()", null);
    }

    private void resumeNavigationGps() {
        if (!navigating || !hasLocation()) return;
        try {
            LocationManager locations = (LocationManager) getSystemService(LOCATION_SERVICE);
            String provider = locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;
            if (!locations.isProviderEnabled(provider)) return;
            locations.requestLocationUpdates(provider, 2000L, 4f, navListener, Looper.getMainLooper());
            Location last = locations.getLastKnownLocation(provider);
            if (last != null) onNavFix(last.getLatitude(), last.getLongitude());
        } catch (Exception ignored) {
        }
    }

    private void pauseNavigationGps() {
        try {
            LocationManager locations = (LocationManager) getSystemService(LOCATION_SERVICE);
            locations.removeUpdates(navListener);
        } catch (Exception ignored) {
        }
    }

    private void fetchRoute(final double fromLat, final double fromLon, final int token) {
        final double toLat = navDestLat;
        final double toLon = navDestLon;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = String.format(Locale.US,
                            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                            fromLon, fromLat, toLon, toLat);
                    String body = httpGet(url);
                    final JSONObject root = new JSONObject(body);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!navigating || token != navToken) return;
                            applyRoute(root);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!navigating || token != navToken) return;
                            navTitle.setText("No route");
                            navDetail.setText("A data connection is needed to find the roads");
                        }
                    });
                }
            }
        }).start();
    }

    private void applyRoute(JSONObject root) {
        navSteps.clear();
        navLine.clear();
        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) {
            navTitle.setText("No route");
            navDetail.setText("No driving road to " + navDestName);
            return;
        }
        JSONObject route = routes.optJSONObject(0);
        JSONObject geometry = route == null ? null : route.optJSONObject("geometry");
        JSONArray coords = geometry == null ? null : geometry.optJSONArray("coordinates");
        if (coords != null) {
            int stride = coords.length() > 1500 ? coords.length() / 1500 : 1;
            for (int i = 0; i < coords.length(); i += stride) {
                JSONArray pair = coords.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;
                navLine.add(new double[] { pair.optDouble(1), pair.optDouble(0) });
            }
            JSONArray last = coords.optJSONArray(coords.length() - 1);
            if (last != null && last.length() >= 2) {
                double[] end = new double[] { last.optDouble(1), last.optDouble(0) };
                if (navLine.isEmpty() || navLine.get(navLine.size() - 1)[0] != end[0]) navLine.add(end);
            }
        }
        JSONArray legs = route == null ? null : route.optJSONArray("legs");
        JSONObject leg = legs == null ? null : legs.optJSONObject(0);
        JSONArray steps = leg == null ? null : leg.optJSONArray("steps");
        if (steps != null) {
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) continue;
                JSONObject maneuver = step.optJSONObject("maneuver");
                JSONArray at = maneuver == null ? null : maneuver.optJSONArray("location");
                if (at == null || at.length() < 2) continue;
                NavStep one = new NavStep();
                one.lon = at.optDouble(0);
                one.lat = at.optDouble(1);
                one.distance = step.optDouble("distance", 0);
                one.type = maneuver.optString("type", "");
                one.modifier = maneuver.optString("modifier", "");
                one.name = step.optString("name", "");
                navSteps.add(one);
            }
        }
        if (navLine.isEmpty() || navSteps.isEmpty()) {
            navTitle.setText("No route");
            navDetail.setText("No driving road to " + navDestName);
            return;
        }
        navStep = 0;
        navSpoken = "";
        navRerouteAt = System.currentTimeMillis();
        if (pageReady) {
            StringBuilder js = new StringBuilder("setRoute([");
            for (int i = 0; i < navLine.size(); i++) {
                if (i > 0) js.append(',');
                js.append(String.format(Locale.US, "[%.5f,%.5f]", navLine.get(i)[0], navLine.get(i)[1]));
            }
            js.append("])");
            map.evaluateJavascript(js.toString(), null);
        }
        if (Prefs.hasFix(this)) onNavFix(Prefs.lat(this), Prefs.lon(this));
    }

    private void onNavFix(double lat, double lon) {
        if (!navigating || navSteps.isEmpty()) return;
        if (pageReady) {
            map.evaluateJavascript(String.format(Locale.US, "showMe(%.6f,%.6f,true)", lat, lon), null);
        }
        while (navStep + 1 < navSteps.size() - 1 && meters(lat, lon, navSteps.get(navStep + 1).lat, navSteps.get(navStep + 1).lon) < 35) {
            navStep++;
        }
        for (int i = navStep + 1; i < navSteps.size(); i++) {
            if (meters(lat, lon, navSteps.get(i).lat, navSteps.get(i).lon) < 28) navStep = Math.min(i, navSteps.size() - 1);
        }
        boolean last = navStep >= navSteps.size() - 1;
        NavStep here = navSteps.get(navStep);
        NavStep upcoming = last ? here : navSteps.get(navStep + 1);
        double remain = meters(lat, lon, upcoming.lat, upcoming.lon);
        if ("arrive".equals(upcoming.type) && remain < 45) last = true;
        if (last) {
            navTitle.setText("You've arrived");
            navDetail.setText(navDestName);
            speakOnce("arrive", "You have arrived");
            return;
        }
        navTitle.setText(maneuverTitle(upcoming));
        String road = upcoming.name == null ? "" : upcoming.name.trim();
        String onto = road.isEmpty() ? "" : "onto " + road;
        navDetail.setText((onto.isEmpty() ? "" : onto + " · ") + distancePhrase(remain));
        String say = maneuverTitle(upcoming) + (road.isEmpty() ? "" : " onto " + road);
        if (remain < 180) speakOnce("soon-" + navStep, say);
        else speakOnce("step-" + navStep, "In " + distancePhrase(remain) + ", " + say);

        long now = System.currentTimeMillis();
        if (nearestRoute(lat, lon) > 80 && now - navRerouteAt > 20000) {
            navRerouteAt = now;
            navTitle.setText("Recalculating");
            navDetail.setText("You left the route");
            fetchRoute(lat, lon, ++navToken);
        }
    }

    private String maneuverTitle(NavStep step) {
        String type = step.type == null ? "" : step.type;
        String mod = step.modifier == null ? "" : step.modifier;
        if ("arrive".equals(type)) return "Arrive";
        if ("depart".equals(type)) return "Head out";
        if ("roundabout".equals(type) || "rotary".equals(type)) return "Enter the roundabout";
        if ("merge".equals(type)) return "Merge";
        if ("fork".equals(type)) return "Keep " + side(mod);
        if ("uturn".equals(mod)) return "Make a U-turn";
        if ("turn".equals(type) || "end of road".equals(type)) return "Turn " + side(mod);
        if ("slight right".equals(mod) || "slight left".equals(mod)) return "Bear " + side(mod);
        if ("sharp right".equals(mod) || "sharp left".equals(mod)) return "Turn sharp " + side(mod);
        if ("right".equals(mod) || "left".equals(mod)) return "Turn " + side(mod);
        return "Continue";
    }

    private String side(String modifier) {
        if (modifier == null) return "";
        if (modifier.endsWith("right")) return "right";
        if (modifier.endsWith("left")) return "left";
        if ("straight".equals(modifier)) return "straight";
        return modifier;
    }

    private String distancePhrase(double meters) {
        double feet = meters * 3.28084;
        if (feet < 600) return ((int) (Math.max(50, Math.round(feet / 50.0) * 50))) + " ft";
        double miles = meters / 1609.344;
        if (miles < 10) return String.format(Locale.US, "%.1f mi", miles);
        return ((int) Math.round(miles)) + " mi";
    }

    private void speakOnce(String key, String text) {
        if (key.equals(navSpoken) || text == null || text.trim().isEmpty()) return;
        navSpoken = key;
        if (!voiceReady || voice == null) return;
        voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav");
    }

    private void ensureVoice() {
        if (voice != null) return;
        voice = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                voiceReady = status == TextToSpeech.SUCCESS;
                if (voiceReady) voice.setLanguage(Locale.US);
            }
        });
    }

    private double nearestRoute(double lat, double lon) {
        double best = 1e12;
        for (int i = 0; i < navLine.size(); i++) {
            double d = meters(lat, lon, navLine.get(i)[0], navLine.get(i)[1]);
            if (d < best) best = d;
        }
        return best;
    }

    private static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "FamilyLocation/2.15");
        InputStream in = conn.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
        in.close();
        conn.disconnect();
        return buf.toString("UTF-8");
    }

    private static class NavStep {
        double lat;
        double lon;
        double distance;
        String type;
        String modifier;
        String name;
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
            line1.setText(member.optBoolean("locationOff") ? "Location is off" : "No location yet");
            line2.setText("");
            line2.setVisibility(View.GONE);
            addReach(member, line2);
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
            addReach(member, line2);
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
        addReach(member, line2);
    }

    private void addReach(JSONObject member, TextView line2) {
        String extra = reachText(member);
        if (extra.isEmpty()) return;
        if (line2.getVisibility() != View.VISIBLE || line2.getText() == null || line2.getText().length() == 0) {
            line2.setText(extra);
            line2.setVisibility(View.VISIBLE);
            return;
        }
        line2.setText(line2.getText().toString() + "\n" + extra);
    }

    private String reachText(JSONObject member) {
        StringBuilder text = new StringBuilder();
        long seen = member.optLong("seen", 0);
        if (seen > 0) text.append("Last response ").append(responseAgo(seen));
        if (member.optBoolean("unreachable")) {
            if (text.length() > 0) text.append('\n');
            if (member.optInt("signal", -1) <= -2) text.append("No signal in their area");
            else text.append("Tailscale may be off, the phone may be off, or there is no signal");
        }
        return text.toString();
    }

    private String responseAgo(long seen) {
        long now = System.currentTimeMillis() / 1000L;
        long sec = now - seen;
        if (sec < 0) sec = 0;
        if (sec < 45) return "just now";
        if (sec < 60) return sec + " seconds ago";
        return agoPhrase(sec / 60L);
    }

    private String paceText(JSONObject member) {
        int every = member.optInt("every", 0);
        if (every <= 0) return "";
        if (every < 60) return "Updates every " + every + (every == 1 ? " second" : " seconds");
        int mins = every / 60;
        int rem = every % 60;
        if (rem == 0) return "Updates every " + mins + (mins == 1 ? " minute" : " minutes");
        return "Updates every " + mins + (mins == 1 ? " minute " : " minutes ") + rem + (rem == 1 ? " second" : " seconds");
    }

    private void widen(TextView view) {
        view.setSingleLine(false);
        view.setEllipsize(null);
        view.setHorizontallyScrolling(false);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(lp);
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
        if (member.optBoolean("locationOff")) return "Location is off";
        if (member.optString("userId").equals(Prefs.userId(this))) return "";
        if (!Prefs.hasFix(this)) return "";
        double meters = haversine(Prefs.lat(this), Prefs.lon(this), member.optDouble("lat"), member.optDouble("lon"));
        if (meters < 30) return "Here with you";
        return "";
    }

    private String pendingCall;

    private void placeCall(String phone) {
        if (phone == null || phone.isEmpty()) return;
        Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(call);
                return;
            } catch (Exception ignored) {
            }
        }
        pendingCall = phone;
        requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, 41);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 41) {
            String phone = pendingCall;
            pendingCall = null;
            if (phone != null && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone)));
                    return;
                } catch (Exception ignored) {
                }
            }
            if (phone != null) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (hasLocation()) ShareService.start(this);
    }

    private View statusBadge(JSONObject member, int percent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int signal = member.optInt("signal", -1);
        boolean none = signal <= -2;
        SignalMeter bars = new SignalMeter(this);
        bars.setLevel(none ? -2 : signal);
        LinearLayout.LayoutParams barsLp = new LinearLayout.LayoutParams(dp(22), dp(16));
        barsLp.rightMargin = dp(6);
        bars.setLayoutParams(barsLp);
        row.addView(bars);
        LinkMeter link = new LinkMeter(this);
        link.setConnected(!member.optBoolean("unreachable"));
        row.addView(iconSlot(link));
        LocMeter loc = new LocMeter(this);
        loc.setOn(!member.optBoolean("locationOff"));
        row.addView(iconSlot(loc));
        if (member.optBoolean("wifi")) row.addView(iconSlot(new WifiMark(this)));
        if (percent >= 0) row.addView(batteryBadge(percent));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = android.view.Gravity.TOP;
        rowLp.leftMargin = dp(8);
        row.setLayoutParams(rowLp);
        return row;
    }

    private View iconSlot(View icon) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(16), dp(16));
        lp.rightMargin = dp(5);
        icon.setLayoutParams(lp);
        return icon;
    }

    private static final class SignalMeter extends View {
        private int level = -1;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SignalMeter(android.content.Context context) {
            super(context);
        }

        void setLevel(int level) {
            this.level = level;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            boolean none = level <= -2;
            int bars = none ? 0 : Math.max(0, Math.min(4, level));
            float gap = w * 0.08f;
            float barW = (w * 0.62f - gap * 3f) / 4f;
            float[] height = {0.35f, 0.55f, 0.75f, 1f};
            for (int i = 0; i < 4; i++) {
                float bh = h * height[i];
                float x = i * (barW + gap);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(!none && i < bars ? 0xFF1A1916 : 0xFFC5C9D1);
                canvas.drawRoundRect(new RectF(x, h - bh, x + barW, h), barW * 0.35f, barW * 0.35f, paint);
            }
            if (!none) return;
            float r = h * 0.42f;
            float cx = w - r;
            float cy = h * 0.42f;
            paint.setColor(0xFFE23B3B);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1.6f, r * 0.32f));
            float d = r * 0.42f;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
        }
    }

    private static final class LinkMeter extends View {
        private boolean connected = true;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LinkMeter(android.content.Context context) {
            super(context);
        }

        void setConnected(boolean connected) {
            this.connected = connected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cy = h * 0.5f;
            float r = h * 0.18f;
            float x1 = r + h * 0.06f;
            float x2 = w - r - h * 0.06f;
            paint.setColor(connected ? 0xFF2F6B3A : 0xFFE23B3B);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1.6f, h * 0.12f));
            paint.setStyle(Paint.Style.STROKE);
            if (connected) {
                canvas.drawLine(x1, cy, x2, cy, paint);
            } else {
                canvas.drawLine(x1, cy, w * 0.36f, cy, paint);
                canvas.drawLine(w * 0.64f, cy, x2, cy, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x1, cy, r, paint);
            canvas.drawCircle(x2, cy, r, paint);
            if (connected) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.6f, h * 0.12f));
            float s = r * 0.95f;
            canvas.drawLine(w * 0.5f - s, cy - s, w * 0.5f + s, cy + s, paint);
            canvas.drawLine(w * 0.5f + s, cy - s, w * 0.5f - s, cy + s, paint);
        }
    }

    private static final class LocMeter extends View {
        private boolean on = true;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LocMeter(android.content.Context context) {
            super(context);
        }

        void setOn(boolean on) {
            this.on = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f;
            float r = w * 0.26f;
            float cy = r + h * 0.06f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(on ? 0xFF2F6B3A : 0xFFE23B3B);
            canvas.drawCircle(cx, cy, r, paint);
            Path tip = new Path();
            tip.moveTo(cx - r * 0.62f, cy + r * 0.2f);
            tip.lineTo(cx + r * 0.62f, cy + r * 0.2f);
            tip.lineTo(cx, h * 0.94f);
            tip.close();
            canvas.drawPath(tip, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, r * 0.38f, paint);
        }
    }

    private static final class WifiMark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WifiMark(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w * 0.5f;
            float cy = h * 0.78f;
            paint.setColor(0xFF1A1916);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, Math.max(1.4f, h * 0.08f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(Math.max(1.4f, h * 0.09f));
            for (int i = 1; i <= 3; i++) {
                float rad = h * 0.22f * i;
                canvas.drawArc(new RectF(cx - rad, cy - rad, cx + rad, cy + rad), 210, 120, false, paint);
            }
        }
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
        widen(cardArrived);
        widen(cardStay);
        LinearLayout batterySlot = findViewById(R.id.cardBattery);
        batterySlot.removeAllViews();
        batterySlot.addView(statusBadge(member, member.optInt("battery", -1)));
        fillStatus(member, cardArrived, cardStay);
        String pace = paceText(member);
        if (!pace.isEmpty()) {
            if (cardStay.getVisibility() == View.VISIBLE && cardStay.getText() != null && cardStay.getText().length() > 0) {
                cardStay.setText(pace + "\n" + cardStay.getText());
            } else {
                cardStay.setText(pace);
                cardStay.setVisibility(View.VISIBLE);
            }
        }
        final double lat = member.optDouble("lat", Double.NaN);
        final double lon = member.optDouble("lon", Double.NaN);
        final boolean hasLoc = member.optBoolean("hasLoc");
        final String phone = member.optString("phone", "");
        findViewById(R.id.cardView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasLoc) startNavigation(lat, lon, member.optString("name", ""));
            }
        });
        findViewById(R.id.cardCall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!phone.isEmpty()) placeCall(phone);
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
        if (!hasLoc || !pageReady) return;
        boolean traveling = member.optBoolean("traveling");
        if (traveling) {
            map.evaluateJavascript("followPerson(" + lat + "," + lon + "," + (moveMap ? "true" : "false") + ")", null);
        } else if (moveMap) {
            map.evaluateJavascript("showPerson(" + lat + "," + lon + ")", null);
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
