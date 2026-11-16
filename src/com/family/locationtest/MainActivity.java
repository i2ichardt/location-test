package com.family.locationtest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 17;

    private EditText editServer;
    private EditText editUser;
    private EditText editGroup;
    private EditText editLat;
    private EditText editLon;
    private TextView textStatus;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editServer = findViewById(R.id.editServer);
        editUser = findViewById(R.id.editUser);
        editGroup = findViewById(R.id.editGroup);
        editLat = findViewById(R.id.editLat);
        editLon = findViewById(R.id.editLon);
        textStatus = findViewById(R.id.textStatus);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Button buttonHere = findViewById(R.id.buttonHere);
        Button buttonHealth = findViewById(R.id.buttonHealth);
        Button buttonSend = findViewById(R.id.buttonSend);
        Button buttonFetch = findViewById(R.id.buttonFetch);
        clearButton(buttonHere);
        clearButton(buttonHealth);
        clearButton(buttonSend);
        clearButton(buttonFetch);

        buttonHere.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useThisPhone();
            }
        });
        buttonHealth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String base = baseUrl();
                if (base == null) return;
                textStatus.setText("Checking " + base + "/health …");
                runHttp(new Job() {
                    @Override
                    public HttpResult work() throws Exception {
                        return request("GET", base + "/health", null);
                    }

                    @Override
                    public void done(HttpResult result) {
                        if (result.error != null) {
                            textStatus.setText("Cannot reach server.\n" + explain(result.error));
                            return;
                        }
                        textStatus.setText("GET /health → " + result.code + "\n" + result.body);
                    }
                });
            }
        });
        buttonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String base = baseUrl();
                if (base == null) return;
                final String user = editUser.getText().toString().trim();
                final String group = editGroup.getText().toString().trim();
                final String latText = editLat.getText().toString().trim();
                final String lonText = editLon.getText().toString().trim();
                if (user.isEmpty() || group.isEmpty() || latText.isEmpty() || lonText.isEmpty()) {
                    textStatus.setText("Fill user, group, latitude, and longitude.");
                    return;
                }
                final double lat;
                final double lon;
                try {
                    lat = Double.parseDouble(latText);
                    lon = Double.parseDouble(lonText);
                } catch (NumberFormatException e) {
                    textStatus.setText("Latitude and longitude must be numbers.");
                    return;
                }
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    textStatus.setText("Latitude must be -90 to 90, longitude -180 to 180.");
                    return;
                }
                textStatus.setText("Sending…");
                runHttp(new Job() {
                    @Override
                    public HttpResult work() throws Exception {
                        JSONObject json = new JSONObject();
                        json.put("userId", user);
                        json.put("groupId", group);
                        json.put("lat", lat);
                        json.put("lon", lon);
                        json.put("ts", System.currentTimeMillis() / 1000L);
                        return request("POST", base + "/send", json.toString());
                    }

                    @Override
                    public void done(HttpResult result) {
                        showHttp("POST /send", result);
                    }
                });
            }
        });
        buttonFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String base = baseUrl();
                if (base == null) return;
                final String user = editUser.getText().toString().trim();
                final String group = editGroup.getText().toString().trim();
                if (user.isEmpty() || group.isEmpty()) {
                    textStatus.setText("Fill server, user, and group.");
                    return;
                }
                textStatus.setText("Fetching…");
                runHttp(new Job() {
                    @Override
                    public HttpResult work() throws Exception {
                        String url = base + "/fetch?userId=" + enc(user) + "&groupId=" + enc(group);
                        return request("GET", url, null);
                    }

                    @Override
                    public void done(HttpResult result) {
                        showHttp("GET /fetch", result);
                    }
                });
            }
        });
    }

    private static void clearButton(Button button) {
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
    }

    private void useThisPhone() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST);
            textStatus.setText("Allow location, then tap Use this phone again.");
            return;
        }
        Location last = latestKnown();
        if (last != null) {
            fillLocation(last);
            return;
        }
        textStatus.setText("Waiting for a fix…");
        try {
            locationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            fillLocation(location);
                        }
                    },
                    Looper.getMainLooper());
        } catch (Exception e) {
            textStatus.setText("No location yet. Step outside or turn location on, then try again.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST) {
            textStatus.setText("If you allowed location, tap Use this phone again.");
        }
    }

    private Location latestKnown() {
        Location best = null;
        String[] providers = {
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        };
        for (String provider : providers) {
            try {
                Location loc = locationManager.getLastKnownLocation(provider);
                if (loc == null) continue;
                if (best == null || loc.getTime() > best.getTime()) best = loc;
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }

    private void fillLocation(Location location) {
        editLat.setText(String.format(java.util.Locale.US, "%.6f", location.getLatitude()));
        editLon.setText(String.format(java.util.Locale.US, "%.6f", location.getLongitude()));
        textStatus.setText("Filled from this phone. Tap Send location when you are ready.");
    }

    private String baseUrl() {
        String raw = editServer.getText().toString().trim();
        if (raw.isEmpty()) {
            textStatus.setText("Enter the server URL first.");
            return null;
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "http://" + raw;
        }
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        editServer.setText(raw);
        return raw;
    }

    private void showHttp(String label, HttpResult result) {
        if (result.error != null) {
            textStatus.setText(label + " failed.\n" + explain(result.error));
            return;
        }
        textStatus.setText(label + " → " + result.code + "\n" + result.body);
    }

    private static String explain(String error) {
        if (error == null) return "Unknown error.";
        if (error.contains("Cleartext") || error.contains("cleartext")) {
            return error + "\nThe server must be http:// and the app already allows cleartext.";
        }
        if (error.contains("timed out") || error.contains("Timeout")) {
            return error + "\nThe PC did not answer. Check that the server is running and the firewall allows port 8000 on Private networks.";
        }
        if (error.contains("Unable to resolve") || error.contains("UnknownHost")) {
            return error + "\nThat host name did not resolve. Prefer the Tailscale 100.x address.";
        }
        if (error.contains("Connection refused") || error.contains("ConnectException")) {
            return error + "\nNothing is listening on that address and port. Start FamilyLocationServer on the PC.";
        }
        return error;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    private void runHttp(final Job job) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpResult result;
                try {
                    result = job.work();
                } catch (Exception e) {
                    result = new HttpResult();
                    result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final HttpResult done = result;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        job.done(done);
                    }
                });
            }
        }).start();
    }

    private interface Job {
        HttpResult work() throws Exception;

        void done(HttpResult result);
    }

    private static final class HttpResult {
        int code;
        String body = "";
        String error;
    }

    private static HttpResult request(String method, String url, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod(method);
            if (json != null) {
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                conn.getOutputStream().write(bytes);
            }
            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            HttpResult result = new HttpResult();
            result.code = code;
            result.body = readAll(stream);
            return result;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = stream.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        stream.close();
        return out.toString(StandardCharsets.UTF_8);
    }
}
