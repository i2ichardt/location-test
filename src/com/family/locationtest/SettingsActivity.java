package com.family.locationtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    private static final int PICK_IMAGE = 7;
    private EditText lanField;
    private EditText tailField;
    private EditText groupField;
    private EditText nameField;
    private EditText phoneField;
    private CheckBox shareBox;
    private CheckBox stopBox;
    private RadioButton modeAuto;
    private RadioButton modeLan;
    private RadioButton modeTail;
    private RadioButton paceFast;
    private RadioButton paceSlow;
    private ImageView imagePreview;
    private LinearLayout placeList;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GroupWatch watch = new GroupWatch();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        lanField = findViewById(R.id.lanField);
        tailField = findViewById(R.id.tailField);
        groupField = findViewById(R.id.groupField);
        nameField = findViewById(R.id.nameField);
        phoneField = findViewById(R.id.phoneField);
        shareBox = findViewById(R.id.shareBox);
        stopBox = findViewById(R.id.stopBox);
        modeAuto = findViewById(R.id.modeAuto);
        modeLan = findViewById(R.id.modeLan);
        modeTail = findViewById(R.id.modeTail);
        paceFast = findViewById(R.id.paceFast);
        paceSlow = findViewById(R.id.paceSlow);
        imagePreview = findViewById(R.id.imagePreview);
        placeList = findViewById(R.id.placeList);

        lanField.setText(Prefs.sp(this).getString("lan", ""));
        tailField.setText(Prefs.sp(this).getString("tail", ""));
        groupField.setText(Prefs.group(this));
        nameField.setText(Prefs.name(this));
        phoneField.setText(Prefs.phone(this));
        shareBox.setChecked(Prefs.share(this));
        stopBox.setChecked(Prefs.onlyStopped(this));
        String mode = Prefs.mode(this);
        if ("lan".equals(mode)) modeLan.setChecked(true);
        else if ("tail".equals(mode)) modeTail.setChecked(true);
        else modeAuto.setChecked(true);
        if (Prefs.fast(this)) paceFast.setChecked(true);
        else paceSlow.setChecked(true);
        showSavedPhoto();

        findViewById(R.id.imageButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "Profile photo"), PICK_IMAGE);
            }
        });
        findViewById(R.id.addPlace).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askPlaceName();
            }
        });
        findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveLocal();
                uploadProfile();
                ShareService.start(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        loadPlaces();
        watch.start(this, new GroupWatch.Listener() {
            @Override
            public void onGroup(final String body) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject json = new JSONObject(body);
                            showPlaces(json.optJSONArray("places"));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        watch.stop();
        super.onDestroy();
    }

    private void showSavedPhoto() {
        File file = new File(getFilesDir(), "profile.jpg");
        if (!file.exists()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) imagePreview.setImageBitmap(bitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (bitmap == null) return;
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            int x = (bitmap.getWidth() - size) / 2;
            int y = (bitmap.getHeight() - size) / 2;
            Bitmap square = Bitmap.createBitmap(bitmap, x, y, size, size);
            Bitmap small = Bitmap.createScaledBitmap(square, 256, 256, true);
            File file = new File(getFilesDir(), "profile.jpg");
            FileOutputStream out = new FileOutputStream(file);
            small.compress(Bitmap.CompressFormat.JPEG, 80, out);
            out.close();
            imagePreview.setImageBitmap(small);
        } catch (Exception e) {
            Toast.makeText(this, "Could not use that photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLocal() {
        String mode = "auto";
        if (modeLan.isChecked()) mode = "lan";
        if (modeTail.isChecked()) mode = "tail";
        String group = groupField.getText().toString().trim();
        if (group.isEmpty()) group = "Fam1";
        Prefs.sp(this).edit()
                .putString("lan", Prefs.cleanUrl(lanField.getText().toString()))
                .putString("tail", Prefs.cleanUrl(tailField.getText().toString()))
                .putString("mode", mode)
                .putString("pace", paceSlow.isChecked() ? "slow" : "fast")
                .putString("group", group)
                .putString("name", nameField.getText().toString().trim())
                .putString("phone", phoneField.getText().toString().trim())
                .putBoolean("share", shareBox.isChecked())
                .putBoolean("stopped", stopBox.isChecked())
                .apply();
        Net.forgetRoute();
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
                    JSONObject body = new JSONObject(result.body);
                    final JSONArray places = body.optJSONArray("places");
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
        placeList.removeAllViews();
        if (places == null || places.length() == 0) {
            return;
        }
        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.optJSONObject(i);
            if (place != null) placeList.addView(placeRow(place));
        }
    }

    private View placeRow(final JSONObject place) {
        final String id = place.optString("id");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 8, 0, 8);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText(place.optString("name") + "  ·  " + (int) place.optDouble("radius", 100) + " m");
        title.setTextColor(0xFF1A1916);
        title.setTextSize(16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);
        top.addView(title);
        Button remove = new Button(this);
        remove.setText("Remove");
        remove.setAllCaps(false);
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deletePlace(id);
            }
        });
        top.addView(remove);
        box.addView(top);

        final CheckBox arrive = new CheckBox(this);
        arrive.setText("Tell me when someone arrives");
        arrive.setTextColor(0xFF1A1916);
        arrive.setChecked(Prefs.notifyArrive(this, id));
        arrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setNotify(SettingsActivity.this, id, true, arrive.isChecked());
            }
        });
        final CheckBox leave = new CheckBox(this);
        leave.setText("Tell me when someone leaves");
        leave.setTextColor(0xFF1A1916);
        leave.setChecked(Prefs.notifyLeave(this, id));
        leave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setNotify(SettingsActivity.this, id, false, leave.isChecked());
            }
        });
        box.addView(arrive);
        box.addView(leave);
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
                .setMessage("Uses your latest location. About 100 meters across.")
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
        saveLocal();
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
