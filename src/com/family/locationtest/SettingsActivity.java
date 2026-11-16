package com.family.locationtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private static final int PICK_IMAGE = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GroupWatch watch = new GroupWatch();
    private FrameLayout root;
    private int page;
    private Bitmap pendingPhoto;
    private LinearLayout placeList;
    private View.OnClickListener pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF2F1F6);
        setContentView(root);
        showHub();
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
        ScrollView scroll = pageScroll();
        LinearLayout body = pageBody();
        body.addView(title("Settings"));
        body.addView(menuCard("Connection", "Home address, Tailscale address, and which one to use", 1));
        body.addView(menuCard("Sharing", "How often your pin is sent, including a custom time", 2));
        body.addView(menuCard("Group", "The name everyone in the family uses", 3));
        body.addView(menuCard("Profile", "Your name, phone number, and photo", 4));
        body.addView(menuCard("Shared places", "Places the group can see, and alerts for this phone", 5));
        body.addView(closeButton("Close", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        scroll.addView(body);
        root.removeAllViews();
        root.addView(scroll);
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
        String pace = Prefs.pace(this);
        if ("slow".equals(pace)) slow.setChecked(true);
        else if ("custom".equals(pace)) custom.setChecked(true);
        else fast.setChecked(true);
        RadioGroup paceGroup = new RadioGroup(this);
        paceGroup.addView(fast);
        paceGroup.addView(hint("Every 30 seconds while moving, every 5 minutes while still."));
        paceGroup.addView(slow);
        paceGroup.addView(hint("Every 1–2 minutes while moving, every 15 minutes while still."));
        paceGroup.addView(custom);
        final EditText every = field(String.valueOf(Prefs.sp(this).getInt("customEvery", 30)), "30", InputType.TYPE_CLASS_NUMBER);
        final RadioButton seconds = radio("Seconds");
        final RadioButton minutes = radio("Minutes");
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
                Prefs.sp(SettingsActivity.this).edit()
                        .putBoolean("share", share.isChecked())
                        .putString("pace", picked)
                        .putInt("customEvery", n)
                        .putString("customUnit", minutes.isChecked() ? "min" : "sec")
                        .putBoolean("stopped", stopped.isChecked())
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
        body.addView(hint("With the map open, Fast and Slow refresh every few seconds. Custom uses the time you set. Low battery, weak GPS, or deep sleep can delay a pin by up to 30 minutes."));
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
        LinearLayout body = form("Shared places", null);
        body.addView(hint("Saving a place sends it to the PC immediately. Other open phones refresh their list."));
        body.addView(placeList);
        Button add = textButton("Save where I am as a place", false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPlaceName();
            }
        });
        body.addView(add);
        mount(body);
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

    private void mount(LinearLayout body) {
        if (pendingSave != null) {
            Button save = textButton("Save", true);
            save.setOnClickListener(pendingSave);
            body.addView(save);
        }
        ScrollView scroll = pageScroll();
        scroll.addView(body);
        root.removeAllViews();
        root.addView(scroll);
    }

    private LinearLayout form(String name, View.OnClickListener save) {
        pendingSave = save;
        LinearLayout body = pageBody();
        body.addView(title(name));
        body.addView(closeButton("Back", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHub();
            }
        }));
        return body;
    }

    private View menuCard(final String name, String detail, final int which) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        TextView heading = new TextView(this);
        heading.setText(name);
        heading.setTextSize(17);
        heading.setTextColor(0xFF1C1B19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = hint(detail);
        card.addView(heading);
        card.addView(sub);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (which == 1) showConnection();
                else if (which == 2) showSharing();
                else if (which == 3) showGroup();
                else if (which == 4) showProfile();
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
        view.setTextColor(0xFF1C1B19);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(12));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(0xFF1C1B19);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(0xFF6E6A64);
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
        button.setBackgroundResource(primary ? R.drawable.pill_dark : R.drawable.pill_light);
        button.setStateListAnimator(null);
        button.setTextColor(primary ? 0xFFF7F4EE : 0xFF1C1B19);
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
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (bitmap == null) return;
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap square = Bitmap.createBitmap(bitmap, (bitmap.getWidth() - size) / 2, (bitmap.getHeight() - size) / 2, size, size);
            pendingPhoto = Bitmap.createScaledBitmap(square, 256, 256, true);
            ImageView preview = findViewById(R.id.imagePreview);
            if (preview != null) preview.setImageBitmap(pendingPhoto);
        } catch (Exception e) {
            Toast.makeText(this, "Could not use that photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void writePhoto(Bitmap bitmap) {
        try {
            FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "profile.jpg"));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.close();
        } catch (Exception ignored) {
        }
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
                    if (file.exists() && file.length() > 0 && file.length() < 400000) {
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        TextView title = new TextView(this);
        title.setText(place.optString("name") + "  ·  " + (int) place.optDouble("radius", 100) + " m");
        title.setTextColor(0xFF1C1B19);
        title.setTextSize(16);
        box.addView(title);
        final CheckBox arrive = new CheckBox(this);
        arrive.setText("Tell me when someone arrives");
        arrive.setTextColor(0xFF1C1B19);
        arrive.setChecked(Prefs.notifyArrive(this, id));
        arrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setNotify(SettingsActivity.this, id, true, arrive.isChecked());
            }
        });
        final CheckBox leave = new CheckBox(this);
        leave.setText("Tell me when someone leaves");
        leave.setTextColor(0xFF1C1B19);
        leave.setChecked(Prefs.notifyLeave(this, id));
        leave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setNotify(SettingsActivity.this, id, false, leave.isChecked());
            }
        });
        Button remove = textButton("Remove", false);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deletePlace(id);
            }
        });
        box.addView(arrive);
        box.addView(leave);
        box.addView(remove);
        return box;
    }

    private void askPlaceName() {
        if (!Prefs.hasFix(this)) {
            Toast.makeText(this, "No location yet. Open the map and allow location first.", Toast.LENGTH_LONG).show();
            return;
        }
        final EditText name = new EditText(this);
        name.setHint("Home, school, work");
        new AlertDialog.Builder(this)
                .setTitle("Save this place")
                .setMessage("Uses your latest location. About 100 meters across. It is sent to the group right away.")
                .setView(name)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String label = name.getText().toString().trim();
                        if (label.isEmpty()) label = "Place";
                        postPlace(label);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void postPlace(final String label) {
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
                    json.put("id", "p" + System.currentTimeMillis());
                    json.put("name", label);
                    json.put("lat", Prefs.lat(SettingsActivity.this));
                    json.put("lon", Prefs.lon(SettingsActivity.this));
                    json.put("radius", 100);
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
}
