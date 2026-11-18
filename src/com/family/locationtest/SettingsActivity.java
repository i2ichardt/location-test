package com.family.locationtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private static final int PICK_IMAGE = 7;
    private static final int PICK_PLACE = 8;
    private static final int TAKE_PLACE = 9;
    private static final int REQ_CAMERA = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GroupWatch watch = new GroupWatch();
    private FrameLayout root;
    private int page;
    private Bitmap pendingPhoto;
    private Bitmap pendingPlacePhoto;
    private ImageView placePreview;
    private String editPlaceId;
    private double editLat;
    private double editLon;
    private int editMeters;
    private LinearLayout placeList;
    private View.OnClickListener pendingSave;
    private TextView updateStatus;
    private String updateUrl = "";
    private boolean pendingInstall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFFE7F0FB);
        setContentView(root);
        showHub();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingInstall) {
            pendingInstall = false;
            installDownloaded();
        }
    }

    @Override
    public void onBackPressed() {
        if (page != 0) showHub();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        watch.stop();
        super.onDestroy();
    }

    private void showHub() {
        page = 0;
        watch.stop();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(menuCard("Connection", "Home address, Tailscale address, and which one to use", 1, 0xFF2F6BFF));
        body.addView(menuCard("Sharing", "How often your pin is sent, including a custom time", 2, 0xFF1F8A70));
        body.addView(menuCard("Group", "The name everyone in the family uses", 3, 0xFF5B4BDB));
        body.addView(menuCard("Profile", "Your name, phone number, and photo", 4, 0xFFE07A2F));
        body.addView(menuCard("Shared places", "Places on the map, and alerts for this phone", 5, 0xFF2F6BFF));
        body.addView(menuCard("Updates", "Check the release page and install a newer app", 6, 0xFF2E9B4F));
        showPage("Settings", "Connection, sharing, your profile, and the places you share.", false, body);
    }

    private void showConnection() {
        page = 1;
        final EditText lan = field(Prefs.sp(this).getString("lan", ""), "http://192.168.1.5:8000", InputType.TYPE_TEXT_VARIATION_URI);
        final EditText tail = field(Prefs.sp(this).getString("tail", ""), "http://100.x.x.x:8000", InputType.TYPE_TEXT_VARIATION_URI);
        final RadioButton auto = radio("Auto — home first, then Tailscale");
        final RadioButton home = radio("Always use home");
        final RadioButton ts = radio("Always use Tailscale");
        String mode = Prefs.mode(this);
        if ("lan".equals(mode)) home.setChecked(true);
        else if ("tail".equals(mode)) ts.setChecked(true);
        else auto.setChecked(true);
        RadioGroup group = new RadioGroup(this);
        group.addView(auto);
        group.addView(home);
        group.addView(ts);
        LinearLayout body = form("Connection", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String picked = "auto";
                if (home.isChecked()) picked = "lan";
                if (ts.isChecked()) picked = "tail";
                Prefs.sp(SettingsActivity.this).edit()
                        .putString("lan", Prefs.cleanUrl(lan.getText().toString()))
                        .putString("tail", Prefs.cleanUrl(tail.getText().toString()))
                        .putString("mode", picked)
                        .apply();
                Net.forgetRoute();
                ShareService.start(SettingsActivity.this);
                showHub();
            }
        });
        body.addView(label("Home Wi-Fi"));
        body.addView(hint("The address on the PC, including :8000"));
        body.addView(lan);
        body.addView(label("Tailscale"));
        body.addView(hint("Used when the phone is away from home Wi-Fi"));
        body.addView(tail);
        body.addView(group);
        mount(body);
    }

    private void showSharing() {
        page = 2;
        final CheckBox share = new CheckBox(this);
        share.setText("Share my location");
        share.setTextColor(0xFF1C1B19);
        share.setChecked(Prefs.share(this));
        final RadioButton fast = radio("Fast updates");
        final RadioButton slow = radio("Slow updates");
        final RadioButton custom = radio("Custom");
        fast.setId(View.generateViewId());
        slow.setId(View.generateViewId());
        custom.setId(View.generateViewId());
        String pace = Prefs.pace(this);
        if ("slow".equals(pace)) slow.setChecked(true);
        else if ("custom".equals(pace)) custom.setChecked(true);
        else fast.setChecked(true);
        RadioGroup paceGroup = new RadioGroup(this);
        paceGroup.addView(fast);
        paceGroup.addView(paceHint("Every 30 seconds while moving, and every 5 minutes while still. With the map open, about every 5 seconds."));
        paceGroup.addView(slow);
        paceGroup.addView(paceHint("Every 1–2 minutes while moving, and every 15 minutes while still. With the map open, about every 5 seconds."));
        paceGroup.addView(custom);
        paceGroup.addView(paceHint("Uses the time below for your pin and for fetching the group, including while the map is open."));
        final EditText every = field(String.valueOf(Prefs.sp(this).getInt("customEvery", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        final RadioButton seconds = radio("Seconds");
        final RadioButton minutes = radio("Minutes");
        seconds.setId(View.generateViewId());
        minutes.setId(View.generateViewId());
        if ("min".equals(Prefs.sp(this).getString("customUnit", "sec"))) minutes.setChecked(true);
        else seconds.setChecked(true);
        RadioGroup unit = new RadioGroup(this);
        unit.setOrientation(LinearLayout.HORIZONTAL);
        unit.addView(seconds);
        unit.addView(minutes);
        final CheckBox stopped = new CheckBox(this);
        stopped.setText("Update my pin only when I stop moving");
        stopped.setTextColor(0xFF1C1B19);
        stopped.setChecked(Prefs.onlyStopped(this));
        final CheckBox charging = new CheckBox(this);
        charging.setText("Faster updates while charging");
        charging.setTextColor(0xFF1C1B19);
        charging.setChecked(Prefs.chargeFast(this));
        final EditText chargeEvery = field(String.valueOf(Prefs.chargeSeconds(this)), "10", InputType.TYPE_CLASS_NUMBER);
        final CheckBox highway = new CheckBox(this);
        highway.setText("Real-time updates above 30 MPH");
        highway.setTextColor(0xFF1C1B19);
        highway.setChecked(Prefs.realtimeFast(this));
        LinearLayout body = form("Sharing", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String picked = "fast";
                if (slow.isChecked()) picked = "slow";
                if (custom.isChecked()) picked = "custom";
                int n = 30;
                try {
                    n = Integer.parseInt(every.getText().toString().trim());
                } catch (Exception ignored) {
                }
                if (n < 1) n = 1;
                if (n > 999) n = 999;
                int chargeN = 10;
                try {
                    chargeN = Integer.parseInt(chargeEvery.getText().toString().trim());
                } catch (Exception ignored) {
                }
                if (chargeN < 5) chargeN = 5;
                if (chargeN > 3600) chargeN = 3600;
                Prefs.sp(SettingsActivity.this).edit()
                        .putBoolean("share", share.isChecked())
                        .putString("pace", picked)
                        .putInt("customEvery", n)
                        .putString("customUnit", minutes.isChecked() ? "min" : "sec")
                        .putBoolean("stopped", stopped.isChecked())
                        .putBoolean("chargeFast", charging.isChecked())
                        .putInt("chargeEvery", chargeN)
                        .putBoolean("realtimeFast", highway.isChecked())
                        .apply();
                ShareService.start(SettingsActivity.this);
                showHub();
            }
        });
        body.addView(share);
        body.addView(paceGroup);
        body.addView(label("Custom amount"));
        body.addView(every);
        body.addView(unit);
        body.addView(stopped);
        body.addView(paceHint("Sends a pin when you stop, unless a faster rule below is on."));
        body.addView(charging);
        body.addView(paceHint("While the phone is plugged in, use this many seconds instead of the slower pace above."));
        body.addView(chargeEvery);
        body.addView(highway);
        body.addView(paceHint("About every 5 seconds while you are going faster than 30 miles per hour."));
        mount(body);
    }

    private void showGroup() {
        page = 3;
        final EditText group = field(Prefs.group(this), "Fam1", InputType.TYPE_CLASS_TEXT);
        LinearLayout body = form("Group", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = group.getText().toString().trim();
                if (name.isEmpty()) name = "Fam1";
                Prefs.sp(SettingsActivity.this).edit().putString("group", name).apply();
                Net.forgetRoute();
                uploadProfile();
                ShareService.start(SettingsActivity.this);
                showHub();
            }
        });
        body.addView(label("Group name"));
        body.addView(hint("Everyone who should see each other uses this same name. It is not your personal name."));
        body.addView(group);
        mount(body);
    }

    private void showProfile() {
        page = 4;
        final EditText name = field(Prefs.name(this), "Your name", InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        final EditText phone = field(formatPhone(Prefs.phone(this)), "111-111-1111", InputType.TYPE_CLASS_PHONE);
        phone.addTextChangedListener(new PhoneFormat());
        final ImageView preview = new ImageView(this);
        LinearLayout.LayoutParams face = new LinearLayout.LayoutParams(dp(72), dp(72));
        preview.setLayoutParams(face);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundResource(R.drawable.field_bg);
        File file = new File(getFilesDir(), "profile.jpg");
        if (file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) preview.setImageBitmap(bitmap);
        }
        Button choose = textButton("Choose photo", false);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Profile photo"), PICK_IMAGE);
            }
        });
        LinearLayout body = form("Profile", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pendingPhoto != null) writePhoto(pendingPhoto);
                Prefs.sp(SettingsActivity.this).edit()
                        .putString("name", name.getText().toString().trim())
                        .putString("phone", phone.getText().toString().trim())
                        .apply();
                uploadProfile();
                showHub();
            }
        });
        body.addView(label("Name"));
        body.addView(name);
        body.addView(label("Phone"));
        body.addView(hint("Digits only. Dashes are added for you."));
        body.addView(phone);
        body.addView(label("Photo"));
        body.addView(hint("Used as your pin on the map."));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(preview);
        choose.setId(R.id.imageButton);
        preview.setId(R.id.imagePreview);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        gap.leftMargin = dp(12);
        choose.setLayoutParams(gap);
        row.addView(choose);
        body.addView(row);
        mount(body);
    }

    private void showPlaces() {
        page = 5;
        placeList = new LinearLayout(this);
        placeList.setOrientation(LinearLayout.VERTICAL);

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.settings_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new GlyphBadge(this, 5, 0xFF2F6BFF));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, dp(8), 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titles.setLayoutParams(titleLp);
        TextView saved = new TextView(this);
        saved.setText("Saved Places");
        saved.setTextColor(0xFF1B2559);
        saved.setTextSize(20);
        saved.setTypeface(Typeface.DEFAULT_BOLD);
        TextView manage = hint("Manage your saved places.");
        manage.setPadding(0, dp(2), 0, 0);
        titles.addView(saved);
        titles.addView(manage);
        head.addView(titles);
        Button add = new Button(this);
        add.setText("+  Add Place");
        add.setAllCaps(false);
        add.setTextSize(14);
        add.setTextColor(0xFFFFFFFF);
        add.setBackgroundResource(R.drawable.settings_blue);
        add.setStateListAnimator(null);
        add.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        add.setLayoutParams(addLp);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPlaceName();
            }
        });
        head.addView(add);
        card.addView(head);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(12);
        placeList.setLayoutParams(listLp);
        card.addView(placeList);
        stack.addView(card);

        LinearLayout notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.HORIZONTAL);
        notes.setGravity(Gravity.CENTER_VERTICAL);
        notes.setBackgroundResource(R.drawable.settings_card);
        notes.setPadding(dp(14), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(14);
        notes.setLayoutParams(noteLp);
        notes.addView(new GlyphBadge(this, 7, 0xFF7C4DFF));
        LinearLayout noteWords = new LinearLayout(this);
        noteWords.setOrientation(LinearLayout.VERTICAL);
        noteWords.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams nw = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        noteWords.setLayoutParams(nw);
        TextView noteTitle = new TextView(this);
        noteTitle.setText("Notifications");
        noteTitle.setTextColor(0xFF1B2559);
        noteTitle.setTextSize(18);
        noteTitle.setTypeface(Typeface.DEFAULT_BOLD);
        TextView noteSub = hint("Get notified about your places.");
        noteSub.setPadding(0, dp(2), 0, 0);
        noteWords.addView(noteTitle);
        noteWords.addView(noteSub);
        notes.addView(noteWords);
        final PillSwitch master = new PillSwitch(this, Prefs.placeAlerts(this));
        master.setOnToggle(new PillSwitch.Listener() {
            @Override
            public void onToggle(boolean on) {
                Prefs.setPlaceAlerts(SettingsActivity.this, on);
            }
        });
        notes.addView(master);
        stack.addView(notes);

        LinearLayout namesRow = new LinearLayout(this);
        namesRow.setOrientation(LinearLayout.HORIZONTAL);
        namesRow.setGravity(Gravity.CENTER_VERTICAL);
        namesRow.setBackgroundResource(R.drawable.settings_card);
        namesRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams namesLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        namesLp.topMargin = dp(14);
        namesRow.setLayoutParams(namesLp);
        TextView namesLabel = new TextView(this);
        namesLabel.setText("Show place names on the map");
        namesLabel.setTextColor(0xFF1B2559);
        namesLabel.setTextSize(15);
        LinearLayout.LayoutParams namesText = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        namesLabel.setLayoutParams(namesText);
        namesRow.addView(namesLabel);
        final PillSwitch names = new PillSwitch(this, Prefs.showPlaceNames(this));
        names.setOnToggle(new PillSwitch.Listener() {
            @Override
            public void onToggle(boolean on) {
                Prefs.setShowPlaceNames(SettingsActivity.this, on);
            }
        });
        namesRow.addView(names);
        stack.addView(namesRow);

        showPage("Shared Places", "Save places to the map and get notified when someone arrives or leaves.", true, stack);
        loadPlaces();
        watch.start(this, new GroupWatch.Listener() {
            @Override
            public void onGroup(final String raw) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            showPlaces(new JSONObject(raw).optJSONArray("places"));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        });
    }

    private void showUpdates() {
        page = 6;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.settings_card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView heading = new TextView(this);
        heading.setText("App updates");
        heading.setTextColor(0xFF1B2559);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading);
        card.addView(hint("This phone is on version " + versionName() + ". Check looks at the GitHub release page."));
        updateStatus = new TextView(this);
        updateStatus.setText("Not checked yet.");
        updateStatus.setTextColor(0xFF1B2559);
        updateStatus.setTextSize(15);
        updateStatus.setPadding(0, dp(8), 0, dp(8));
        card.addView(updateStatus);
        Button check = textButton("Check for updates", true);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkUpdates();
            }
        });
        card.addView(check);
        Button install = textButton("Download and install", true);
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (updateUrl == null || updateUrl.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, "Check for updates first", Toast.LENGTH_SHORT).show();
                    return;
                }
                downloadUpdate();
            }
        });
        card.addView(install);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(card);
        showPage("Updates", "Look on the release page for a newer install.", true, wrap);
    }

    private void mount(LinearLayout body) {
        if (pendingSave != null) {
            Button save = textButton("Save", true);
            save.setOnClickListener(pendingSave);
            body.addView(save);
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(R.drawable.settings_card);
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        wrap.addView(body);
        showPage(pageTitle(), pageSubtitle(), true, wrap);
    }

    private String pageTitle() {
        if (page == 1) return "Connection";
        if (page == 2) return "Sharing";
        if (page == 3) return "Group";
        if (page == 4) return "Profile";
        if (page == 6) return "Updates";
        return "Settings";
    }

    private String pageSubtitle() {
        if (page == 1) return "The address of the PC at home, and on Tailscale.";
        if (page == 2) return "How often this phone sends its pin.";
        if (page == 3) return "Everyone who should see each other uses this name.";
        if (page == 4) return "Your name, phone, and the photo on your pin.";
        if (page == 6) return "Look on the release page for a newer install.";
        return "";
    }

    private LinearLayout form(String name, View.OnClickListener save) {
        pendingSave = save;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        return body;
    }

    private void showPage(String title, String subtitle, boolean back, LinearLayout content) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFFE7F0FB);
        HillsView hills = new HillsView(this);
        FrameLayout.LayoutParams hillLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(160), Gravity.BOTTOM);
        frame.addView(hills, hillLp);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout head = new FrameLayout(this);
        ImageView art = new ImageView(this);
        art.setImageResource(bannerForToday());
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        head.addView(art, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(236)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(20), dp(64), dp(20), dp(28));
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTextSize(30);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(heading);
        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(0xF2FFFFFF);
        sub.setTextSize(14);
        sub.setPadding(0, dp(6), 0, 0);
        words.addView(sub);
        FrameLayout.LayoutParams wordLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(236), Gravity.BOTTOM);
        head.addView(words, wordLp);
        column.addView(head);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(16);
        clp.rightMargin = dp(16);
        clp.topMargin = -dp(18);
        clp.bottomMargin = dp(130);
        content.setLayoutParams(clp);
        column.addView(content);

        ScrollView scroll = pageScroll();
        scroll.addView(column);
        frame.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        BackChevron backBtn = new BackChevron(this);
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        backLp.leftMargin = dp(10);
        backLp.topMargin = dp(10);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page == 0) finish();
                else showHub();
            }
        });
        frame.addView(backBtn, backLp);
        root.removeAllViews();
        root.addView(frame);
    }

    private View menuCard(final String name, String detail, final int which, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.settings_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        card.addView(new GlyphBadge(this, which, color));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        words.setLayoutParams(wordLp);
        TextView heading = new TextView(this);
        heading.setText(name);
        heading.setTextSize(17);
        heading.setTextColor(0xFF1B2559);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = hint(detail);
        sub.setPadding(0, dp(2), 0, 0);
        words.addView(heading);
        words.addView(sub);
        card.addView(words);
        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(26);
        chevron.setTextColor(0xFF9AA6C2);
        card.addView(chevron);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (which == 1) showConnection();
                else if (which == 2) showSharing();
                else if (which == 3) showGroup();
                else if (which == 4) showProfile();
                else if (which == 6) showUpdates();
                else showPlaces();
            }
        });
        return card;
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        return scroll;
    }

    private LinearLayout pageBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(18), dp(16), dp(24));
        return body;
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(28);
        view.setTextColor(0xFF1B2559);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(0xFF1B2559);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView paceHint(String text) {
        TextView view = hint(text);
        view.setPadding(dp(32), 0, dp(8), dp(8));
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(0xFF7E8AA8);
        view.setPadding(0, dp(2), 0, dp(6));
        return view;
    }

    private EditText field(String value, String placeholder, int type) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setHint(placeholder);
        edit.setInputType(type);
        edit.setTextColor(0xFF1C1B19);
        edit.setHintTextColor(0xFF8A847A);
        edit.setBackgroundResource(R.drawable.field_bg);
        edit.setMinHeight(dp(48));
        edit.setPadding(dp(12), dp(10), dp(12), dp(10));
        return edit;
    }

    private RadioButton radio(String text) {
        RadioButton button = new RadioButton(this);
        button.setText(text);
        button.setTextColor(0xFF1C1B19);
        return button;
    }

    private Button textButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setBackgroundResource(primary ? R.drawable.settings_blue : R.drawable.pill_light);
        button.setStateListAnimator(null);
        button.setTextColor(primary ? 0xFFFFFFFF : 0xFF1B2559);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private Button closeButton(String text, View.OnClickListener click) {
        Button button = textButton(text, false);
        button.setOnClickListener(click);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatPhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(0, 10);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i == 3 || i == 6) out.append('-');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    private static final class PhoneFormat implements TextWatcher {
        private boolean busy;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (busy) return;
            String next = formatPhone(s.toString());
            if (!next.equals(s.toString())) {
                busy = true;
                s.replace(0, s.length(), next);
                busy = false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TAKE_PLACE && resultCode == RESULT_OK) {
            File file = new File(getCacheDir(), "place_camera.jpg");
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) usePlaceBitmap(bitmap);
            return;
        }
        if (requestCode != PICK_IMAGE && requestCode != PICK_PLACE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (bitmap == null) return;
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - size) / 2, (bitmap.getHeight() - size) / 2, size, size);
            int edge = Math.min(requestCode == PICK_PLACE ? 512 : 1024, square.getWidth());
            Bitmap scaled = edge == square.getWidth() ? square : Bitmap.createScaledBitmap(square, edge, edge, true);
            if (requestCode == PICK_PLACE) {
                usePlaceBitmap(scaled);
                return;
            }
            pendingPhoto = scaled;
            ImageView preview = findViewById(R.id.imagePreview);
            if (preview != null) preview.setImageBitmap(pendingPhoto);
        } catch (Exception e) {
            Toast.makeText(this, "Could not use that photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void usePlaceBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - size) / 2, (bitmap.getHeight() - size) / 2, size, size);
        int edge = Math.min(512, square.getWidth());
        pendingPlacePhoto = edge == square.getWidth() ? square : Bitmap.createScaledBitmap(square, edge, edge, true);
        if (placePreview != null) placePreview.setImageBitmap(pendingPlacePhoto);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            takePlacePhoto();
        }
    }

    private void writePhoto(Bitmap bitmap) {
        try {
            int quality = 84;
            byte[] bytes = jpegBytes(bitmap, quality);
            while (bytes.length > 1100000 && quality > 52) {
                quality -= 8;
                bytes = jpegBytes(bitmap, quality);
            }
            FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "profile.jpg"));
            out.write(bytes);
            out.close();
        } catch (Exception ignored) {
        }
    }

    private byte[] jpegBytes(Bitmap bitmap, int quality) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    private void uploadProfile() {
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject json = new JSONObject();
                    json.put("userId", Prefs.userId(SettingsActivity.this));
                    json.put("groupId", Prefs.group(SettingsActivity.this));
                    json.put("name", Prefs.name(SettingsActivity.this));
                    json.put("phone", Prefs.phone(SettingsActivity.this));
                    File file = new File(getFilesDir(), "profile.jpg");
                    if (file.exists() && file.length() > 0 && file.length() < 1400000) {
                        java.io.FileInputStream in = new java.io.FileInputStream(file);
                        byte[] data = new byte[(int) file.length()];
                        int off = 0;
                        while (off < data.length) {
                            int n = in.read(data, off, data.length - off);
                            if (n < 0) break;
                            off += n;
                        }
                        in.close();
                        json.put("image", android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP));
                    }
                    Net.post(base + "/profile", json.toString());
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void loadPlaces() {
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Net.Result result = Net.get(base + "/group?groupId=" + Net.query(Prefs.group(SettingsActivity.this)));
                if (result.error != null || result.code != 200) return;
                try {
                    final JSONArray places = new JSONObject(result.body).optJSONArray("places");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showPlaces(places);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void showPlaces(JSONArray places) {
        if (placeList == null) return;
        placeList.removeAllViews();
        if (places == null) return;
        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.optJSONObject(i);
            if (place != null) placeList.addView(placeRow(place));
        }
    }

    private View placeRow(final JSONObject place) {
        final String id = place.optString("id");
        final String name = place.optString("name", "Place");
        final int meters = (int) place.optDouble("radius", 100);
        final double lat = place.optDouble("lat");
        final double lon = place.optDouble("lon");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.settings_inner);
        box.setPadding(dp(10), dp(10), dp(8), dp(10));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(10);
        box.setLayoutParams(boxLp);

        ImageView thumb = new ImageView(this);
        thumb.setImageResource(placeArt(name));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setClipToOutline(true);
        thumb.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(dp(78), dp(78));
        thumb.setLayoutParams(thumbLp);
        thumb.setBackgroundResource(R.drawable.settings_inner);
        box.addView(thumb);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(10), 0, dp(6), 0);
        LinearLayout.LayoutParams wordLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        words.setLayoutParams(wordLp);
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(0xFF1B2559);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(title);

        TextView radius = new TextView(this);
        radius.setText("📍  " + meters + " m");
        radius.setTextColor(0xFF6D7896);
        radius.setTextSize(14);
        radius.setPadding(0, dp(4), 0, dp(6));
        radius.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askRadius(id, name, lat, lon, meters);
            }
        });
        words.addView(radius);
        View line = new View(this);
        line.setBackgroundColor(0xFFE1E6F2);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lineLp.bottomMargin = dp(6);
        line.setLayoutParams(lineLp);
        words.addView(line);
        words.addView(checkLine("Notify when someone arrives", Prefs.notifyArrive(this, id), new PillSwitch.Listener() {
            @Override
            public void onToggle(boolean on) {
                Prefs.setNotify(SettingsActivity.this, id, true, on);
            }
        }));
        words.addView(checkLine("Notify when someone leaves", Prefs.notifyLeave(this, id), new PillSwitch.Listener() {
            @Override
            public void onToggle(boolean on) {
                Prefs.setNotify(SettingsActivity.this, id, false, on);
            }
        }));
        box.addView(words);

        View rule = new View(this);
        rule.setBackgroundColor(0xFFE1E6F2);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(dp(1), dp(72));
        ruleLp.leftMargin = dp(4);
        ruleLp.rightMargin = dp(6);
        rule.setLayoutParams(ruleLp);
        box.addView(rule);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_HORIZONTAL);
        PencilButton pencil = new PencilButton(this);
        pencil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askEditPlace(id, name, lat, lon, meters, place.optLong("imageTs"));
            }
        });
        tools.addView(pencil);
        TrashButton trash = new TrashButton(this);
        trash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deletePlace(id);
            }
        });
        tools.addView(trash);
        box.addView(tools);
        if (place.optBoolean("hasImage")) loadPlaceThumb(thumb, id, place.optLong("imageTs"), name);
        return box;
    }

    private int placeArt(String name) {
        String key = name == null ? "" : name.toLowerCase();
        if (key.contains("home") || key.contains("house")) return R.drawable.place_home;
        if (key.contains("work") || key.contains("office") || key.contains("job")) return R.drawable.place_work;
        return R.drawable.place_other;
    }

    private View checkLine(String text, boolean on, final PillSwitch.Listener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        final CheckDot dot = new CheckDot(this, on);
        row.addView(dot);
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFF1B2559);
        label.setTextSize(14);
        label.setPadding(dp(8), 0, 0, 0);
        row.addView(label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !dot.on;
                dot.setOn(next);
                listener.onToggle(next);
            }
        });
        return row;
    }

    private void askPlaceName() {
        if (!Prefs.hasFix(this)) {
            Toast.makeText(this, "No location yet. Open the map and allow location first.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText name = new EditText(this);
        name.setHint("Home, school, work");
        final EditText radius = new EditText(this);
        radius.setHint("Distance in meters");
        radius.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        radius.setText("100");
        form.addView(name);
        form.addView(radius);
        new AlertDialog.Builder(this)
                .setTitle("Add place")
                .setMessage("Uses where you are right now.")
                .setView(form)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String label = name.getText().toString().trim();
                        if (label.isEmpty()) label = "Place";
                        int meters = 100;
                        try {
                            meters = Integer.parseInt(radius.getText().toString().trim());
                        } catch (Exception ignored) {
                        }
                        if (meters < 20) meters = 20;
                        if (meters > 5000) meters = 5000;
                        postPlace("p" + System.currentTimeMillis(), label, Prefs.lat(SettingsActivity.this), Prefs.lon(SettingsActivity.this), meters, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askRadius(final String id, final String name, final double lat, final double lon, int current) {
        final EditText radius = new EditText(this);
        radius.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        radius.setText(String.valueOf(current));
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("Arrive and leave distance, in meters.")
                .setView(radius)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        int meters = current;
                        try {
                            meters = Integer.parseInt(radius.getText().toString().trim());
                        } catch (Exception ignored) {
                        }
                        if (meters < 20) meters = 20;
                        if (meters > 5000) meters = 5000;
                        postPlace(id, name, lat, lon, meters, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askEditPlace(final String id, final String name, final double lat, final double lon, final int meters, final long imageTs) {
        editPlaceId = id;
        editLat = lat;
        editLon = lon;
        editMeters = meters;
        pendingPlacePhoto = null;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);
        placePreview = new ImageView(this);
        LinearLayout.LayoutParams face = new LinearLayout.LayoutParams(dp(96), dp(96));
        face.bottomMargin = dp(8);
        placePreview.setLayoutParams(face);
        placePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        placePreview.setBackgroundResource(R.drawable.settings_inner);
        placePreview.setImageResource(placeArt(name));
        final EditText label = new EditText(this);
        label.setText(name);
        Button choose = textButton("Choose photo", false);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Place photo"), PICK_PLACE);
            }
        });
        Button camera = textButton("Take photo", false);
        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePlacePhoto();
            }
        });
        form.addView(placePreview);
        form.addView(label);
        form.addView(choose);
        form.addView(camera);
        new AlertDialog.Builder(this)
                .setTitle("Edit place")
                .setMessage("Rename it, or use a photo from your gallery or camera.")
                .setView(form)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String next = label.getText().toString().trim();
                        if (next.isEmpty()) next = name;
                        String image = pendingPlacePhoto == null ? null : placeImageB64(pendingPlacePhoto);
                        postPlace(id, next, lat, lon, meters, image);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        if (imageTs > 0) loadPlaceThumb(placePreview, id, imageTs, name);
    }

    private void takePlacePhoto() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        try {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            android.net.Uri uri = android.net.Uri.parse("content://com.family.locationtest.camera/place.jpg");
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
            intent.setClipData(android.content.ClipData.newRawUri("place", uri));
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, TAKE_PLACE);
        } catch (Exception e) {
            Toast.makeText(this, "No camera app on this phone", Toast.LENGTH_SHORT).show();
        }
    }

    private void postPlace(final String id, final String label, final double lat, final double lon, final int meters, final String image) {
        final String base = Net.base(this);
        if (base.isEmpty()) {
            Toast.makeText(this, "Add a server address first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject json = new JSONObject();
                    json.put("groupId", Prefs.group(SettingsActivity.this));
                    json.put("id", id);
                    json.put("name", label);
                    json.put("lat", lat);
                    json.put("lon", lon);
                    json.put("radius", meters);
                    if (image != null) json.put("image", image);
                    Net.Result result = Net.post(base + "/place", json.toString());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (result.error != null || result.code >= 400) {
                                Toast.makeText(SettingsActivity.this, "Could not save the place", Toast.LENGTH_SHORT).show();
                            } else {
                                loadPlaces();
                            }
                        }
                    });
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SettingsActivity.this, "Could not save the place", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void deletePlace(final String id) {
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject json = new JSONObject();
                    json.put("groupId", Prefs.group(SettingsActivity.this));
                    json.put("id", id);
                    Net.post(base + "/place/delete", json.toString());
                } catch (Exception ignored) {
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadPlaces();
                    }
                });
            }
        }).start();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "2.9";
        }
    }

    private void checkUpdates() {
        if (updateStatus != null) updateStatus.setText("Checking the release page…");
        updateUrl = "";
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String found = readLatestRelease();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (updateStatus == null) return;
                        if (found == null) {
                            updateStatus.setText("Could not reach the release page.");
                            return;
                        }
                        if (found.startsWith("same:")) {
                            updateStatus.setText("You're on " + found.substring(5) + ". That's the latest.");
                            return;
                        }
                        int bar = found.indexOf('|');
                        String tag = bar < 0 ? found : found.substring(0, bar);
                        updateUrl = bar < 0 ? "" : found.substring(bar + 1);
                        updateStatus.setText(tag + " is ready. Download and install replaces this app.");
                    }
                });
            }
        }).start();
    }

    private String readLatestRelease() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("https://api.github.com/repos/i2ichardt/location-test/releases/latest").openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "FamilyLocation");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            if (conn.getResponseCode() != 200) return null;
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
            in.close();
            JSONObject json = new JSONObject(buf.toString("UTF-8"));
            String tag = json.optString("tag_name", "");
            String latest = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            JSONArray assets = json.optJSONArray("assets");
            String url = "";
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && "LocationTest.apk".equals(asset.optString("name"))) {
                        url = asset.optString("browser_download_url", "");
                    }
                }
            }
            if (compareVersions(latest, versionName()) <= 0) return "same:" + latest;
            if (url.isEmpty()) return null;
            return latest + "|" + url;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? parsePart(a[i]) : 0;
            int bv = i < b.length ? parsePart(b[i]) : 0;
            if (av != bv) return av - bv;
        }
        return 0;
    }

    private int parsePart(String raw) {
        String digits = raw.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private void downloadUpdate() {
        if (updateStatus != null) updateStatus.setText("Downloading…");
        final String url = updateUrl;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setRequestProperty("User-Agent", "FamilyLocation");
                    conn.setRequestProperty("Accept", "application/octet-stream");
                    if (conn.getResponseCode() == 200) {
                        InputStream in = conn.getInputStream();
                        FileOutputStream out = new FileOutputStream(new File(getCacheDir(), "update.apk"));
                        byte[] tmp = new byte[8192];
                        int n;
                        while ((n = in.read(tmp)) >= 0) out.write(tmp, 0, n);
                        out.close();
                        in.close();
                        ok = new File(getCacheDir(), "update.apk").length() > 10000;
                    }
                } catch (Exception ignored) {
                    ok = false;
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final boolean ready = ok;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!ready) {
                            if (updateStatus != null) updateStatus.setText("The download did not finish.");
                            return;
                        }
                        installDownloaded();
                    }
                });
            }
        }).start();
    }

    private void installDownloaded() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = true;
            if (updateStatus != null) updateStatus.setText("Allow installs from this app, then come back.");
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + getPackageName())));
            return;
        }
        File file = new File(getCacheDir(), "update.apk");
        if (!file.exists()) return;
        android.net.Uri uri = android.net.Uri.parse("content://com.family.locationtest.apk/update.apk");
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private String placeImageB64(Bitmap bitmap) {
        int quality = 80;
        byte[] bytes = jpegBytes(bitmap, quality);
        while (bytes.length > 400000 && quality > 40) {
            quality -= 8;
            bytes = jpegBytes(bitmap, quality);
        }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private File placeFile(String id, long imageTs) {
        return new File(getFilesDir(), "place_" + id + "_" + imageTs + ".jpg");
    }

    private void loadPlaceThumb(final ImageView thumb, final String id, final long imageTs, final String name) {
        if (imageTs <= 0) return;
        File cached = placeFile(id, imageTs);
        if (cached.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bitmap != null) {
                thumb.setImageBitmap(bitmap);
                return;
            }
        }
        final String base = Net.base(this);
        if (base.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Net.Result result = Net.getBytes(base + "/placeimg?groupId=" + Net.query(Prefs.group(SettingsActivity.this)) + "&id=" + Net.query(id));
                if (result.error != null || result.raw == null || result.raw.length == 0) return;
                try {
                    FileOutputStream out = new FileOutputStream(placeFile(id, imageTs));
                    out.write(result.raw);
                    out.close();
                } catch (Exception ignored) {
                }
                final Bitmap bitmap = BitmapFactory.decodeByteArray(result.raw, 0, result.raw.length);
                if (bitmap == null) return;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        thumb.setImageBitmap(bitmap);
                    }
                });
            }
        }).start();
    }

    private int bannerForToday() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int month = now.get(java.util.Calendar.MONTH);
        int day = now.get(java.util.Calendar.DAY_OF_MONTH);
        if ((month == java.util.Calendar.DECEMBER && day >= 27) || (month == java.util.Calendar.JANUARY && day <= 2)) {
            return R.drawable.banner_newyear;
        }
        if (month == java.util.Calendar.DECEMBER && day >= 18) return R.drawable.banner_christmas;
        if (month == java.util.Calendar.OCTOBER && day >= 24) return R.drawable.banner_halloween;
        if (month == java.util.Calendar.NOVEMBER && thanksgivingWeek(now)) return R.drawable.banner_thanksgiving;
        if (month == java.util.Calendar.FEBRUARY && day >= 12 && day <= 15) return R.drawable.banner_valentine;
        if (month == java.util.Calendar.JULY && day <= 5) return R.drawable.banner_july4;
        int md = (month + 1) * 100 + day;
        if (md >= 320 && md < 621) return R.drawable.banner_spring;
        if (md >= 621 && md < 922) return R.drawable.banner_places;
        if (md >= 922 && md < 1221) return R.drawable.banner_fall;
        return R.drawable.banner_winter;
    }

    private boolean thanksgivingWeek(java.util.Calendar now) {
        java.util.Calendar first = java.util.Calendar.getInstance();
        first.set(java.util.Calendar.MONTH, java.util.Calendar.NOVEMBER);
        first.set(java.util.Calendar.DAY_OF_MONTH, 1);
        int weekday = first.get(java.util.Calendar.DAY_OF_WEEK);
        int add = (java.util.Calendar.THURSDAY - weekday + 7) % 7;
        int fourth = 1 + add + 21;
        int day = now.get(java.util.Calendar.DAY_OF_MONTH);
        return day >= fourth - 1 && day <= fourth + 3;
    }

    private static final class BackChevron extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BackChevron(android.content.Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float radius = Math.min(w, h) * 0.46f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xF5FFFFFF);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(10f, w * 0.1f));
            paint.setColor(0xFF1B3A6B);
            float arm = radius * 0.46f;
            Path chevron = new Path();
            chevron.moveTo(cx + arm * 0.38f, cy - arm);
            chevron.lineTo(cx - arm * 0.52f, cy);
            chevron.lineTo(cx + arm * 0.38f, cy + arm);
            canvas.drawPath(chevron, paint);
        }
    }

    private static final class HillsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HillsView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(0xFFD5E4F8);
            Path back = new Path();
            back.moveTo(0, h * 0.55f);
            back.quadTo(w * 0.25f, h * 0.15f, w * 0.5f, h * 0.48f);
            back.quadTo(w * 0.75f, h * 0.82f, w, h * 0.35f);
            back.lineTo(w, h);
            back.lineTo(0, h);
            back.close();
            canvas.drawPath(back, paint);
            paint.setColor(0xFFC5D8F4);
            Path front = new Path();
            front.moveTo(0, h * 0.72f);
            front.quadTo(w * 0.3f, h * 0.4f, w * 0.62f, h * 0.7f);
            front.quadTo(w * 0.82f, h * 0.88f, w, h * 0.58f);
            front.lineTo(w, h);
            front.lineTo(0, h);
            front.close();
            canvas.drawPath(front, paint);
            paint.setColor(0xFF8FB0E4);
            tree(canvas, w * 0.08f, h * 0.62f, h * 0.28f);
            tree(canvas, w * 0.16f, h * 0.7f, h * 0.22f);
            tree(canvas, w * 0.88f, h * 0.5f, h * 0.3f);
            tree(canvas, w * 0.95f, h * 0.62f, h * 0.22f);
        }

        private void tree(Canvas canvas, float x, float base, float height) {
            Path pine = new Path();
            pine.moveTo(x, base - height);
            pine.lineTo(x + height * 0.28f, base);
            pine.lineTo(x - height * 0.28f, base);
            pine.close();
            canvas.drawPath(pine, paint);
        }
    }

    private static final class GlyphBadge extends View {
        private final int kind;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlyphBadge(android.content.Context context, int kind, int color) {
            super(context);
            this.kind = kind;
            this.color = color;
            int size = Math.round(46 * getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(size, size));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, w * 0.06f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float c = w / 2f;
            float r = w * 0.16f;
            if (kind == 7) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(c, h * 0.42f, r * 0.85f, paint);
                canvas.drawRoundRect(new RectF(c - r, h * 0.38f, c + r, h * 0.62f), 4f, 4f, paint);
                canvas.drawRoundRect(new RectF(c - r * 1.35f, h * 0.6f, c + r * 1.35f, h * 0.72f), 6f, 6f, paint);
                return;
            }
            if (kind == 5) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(c, h * 0.4f, r, paint);
                Path pin = new Path();
                pin.moveTo(c - r, h * 0.46f);
                pin.lineTo(c, h * 0.78f);
                pin.lineTo(c + r, h * 0.46f);
                pin.close();
                canvas.drawPath(pin, paint);
                paint.setColor(color);
                canvas.drawCircle(c, h * 0.4f, r * 0.38f, paint);
                return;
            }
            canvas.drawCircle(c, c, r * 1.15f, paint);
        }
    }

    private static final class PillSwitch extends View {
        interface Listener {
            void onToggle(boolean on);
        }

        private boolean on;
        private Listener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PillSwitch(android.content.Context context, boolean on) {
            super(context);
            this.on = on;
            int w = Math.round(52 * getResources().getDisplayMetrics().density);
            int h = Math.round(30 * getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(w, h));
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    PillSwitch.this.on = !PillSwitch.this.on;
                    invalidate();
                    if (listener != null) listener.onToggle(PillSwitch.this.on);
                }
            });
        }

        void setOnToggle(Listener listener) {
            this.listener = listener;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(on ? 0xFF2F6BFF : 0xFFD5DDEB);
            canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, paint);
            paint.setColor(0xFFFFFFFF);
            float d = h - 6f;
            float x = on ? w - d - 3f : 3f;
            canvas.drawCircle(x + d / 2f, h / 2f, d / 2f, paint);
        }
    }

    private static final class CheckDot extends View {
        boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CheckDot(android.content.Context context, boolean on) {
            super(context);
            this.on = on;
            int size = Math.round(22 * getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(size, size));
        }

        void setOn(boolean on) {
            this.on = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            if (on) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF2F6BFF);
                canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, s * 0.1f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                Path mark = new Path();
                mark.moveTo(s * 0.28f, s * 0.52f);
                mark.lineTo(s * 0.44f, s * 0.68f);
                mark.lineTo(s * 0.74f, s * 0.34f);
                canvas.drawPath(mark, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, s * 0.08f));
                paint.setColor(0xFFC5CEDF);
                canvas.drawCircle(s / 2f, s / 2f, s / 2f - paint.getStrokeWidth(), paint);
            }
        }
    }

    private static final class PencilButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PencilButton(android.content.Context context) {
            super(context);
            int size = Math.round(40 * getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(size, size));
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFEEF1F6);
            canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(1.7f, s * 0.045f));
            paint.setColor(0xFF2F6BFF);
            Path pencil = new Path();
            pencil.moveTo(s * 0.62f, s * 0.28f);
            pencil.lineTo(s * 0.72f, s * 0.38f);
            pencil.lineTo(s * 0.40f, s * 0.70f);
            pencil.lineTo(s * 0.28f, s * 0.74f);
            pencil.lineTo(s * 0.32f, s * 0.60f);
            pencil.close();
            canvas.drawPath(pencil, paint);
            canvas.drawLine(s * 0.56f, s * 0.34f, s * 0.66f, s * 0.44f, paint);
        }
    }

    private static final class TrashButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        TrashButton(android.content.Context context) {
            super(context);
            int size = Math.round(40 * getResources().getDisplayMetrics().density);
            setLayoutParams(new LinearLayout.LayoutParams(size, size));
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFEEF1F6);
            canvas.drawCircle(s / 2f, s / 2f, s / 2f, paint);
            paint.setColor(0xFF8B95AA);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.6f, s * 0.045f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            float l = s * 0.34f;
            float r = s * 0.66f;
            float top = s * 0.38f;
            float bot = s * 0.7f;
            canvas.drawRoundRect(new RectF(l, top, r, bot), 2f, 2f, paint);
            canvas.drawLine(l - 2f, top, r + 2f, top, paint);
            canvas.drawLine(s * 0.44f, s * 0.3f, s * 0.56f, s * 0.3f, paint);
            canvas.drawLine(s * 0.44f, top + 4f, s * 0.44f, bot - 3f, paint);
            canvas.drawLine(s * 0.56f, top + 4f, s * 0.56f, bot - 3f, paint);
        }
    }
}
