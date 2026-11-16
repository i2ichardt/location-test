package com.family.locationtest;

import android.content.Context;
import org.json.JSONObject;

public final class GroupWatch {
    public interface Listener {
        void onGroup(String body);
    }

    private volatile boolean run;
    private Thread thread;

    public void start(final Context context, final Listener listener) {
        stop();
        run = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                long rev = 0;
                while (run) {
                    String base = Net.base(context);
                    if (base.isEmpty()) {
                        sleep(3000);
                        continue;
                    }
                    String group = Prefs.group(context);
                    Net.Result wait = Net.getWait(base + "/wait?groupId=" + Net.query(group) + "&rev=" + rev);
                    if (!run) return;
                    if (wait.error == null && wait.body != null && wait.body.contains("rev")) {
                        try {
                            rev = new JSONObject(wait.body).optLong("rev", rev);
                        } catch (Exception ignored) {
                        }
                    } else {
                        sleep(4000);
                    }
                    if (!run) return;
                    Net.Result groupResult = Net.get(base + "/group?groupId=" + Net.query(group));
                    if (!run) return;
                    if (groupResult.error == null && groupResult.code == 200 && listener != null) {
                        listener.onGroup(groupResult.body);
                    }
                }
            }
        });
        thread.start();
    }

    public void stop() {
        run = false;
        if (thread != null) thread.interrupt();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
