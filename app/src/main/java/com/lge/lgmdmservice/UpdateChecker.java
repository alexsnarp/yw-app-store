package com.lge.lgmdmservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks catalog versions against installed packages and posts one notification per update set. */
public final class UpdateChecker {
    private static final String API = "https://yw-app-store-api.yw-app-installer-store-2026.workers.dev";
    private static final String STORE_MANIFEST = "https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/latest.json";
    private static final String CHANNEL = "yw_available_updates";
    private static final int NOTIFICATION_ID = 2302;

    private UpdateChecker() { }

    public static void checkNow(Context context) {
        Context app = context.getApplicationContext();
        if (!NetworkAccess.isAllowed(app)) return;
        checkStoreSelfUpdate(app);
        android.content.SharedPreferences prefs = app.getSharedPreferences("yw_store", Context.MODE_PRIVATE);
        String token = prefs.getString("device_token", null);
        String deviceId = prefs.getString("device_id", null);
        if (token == null || token.length() < 16 || deviceId == null || deviceId.isEmpty()) return;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API + "/api/catalog").openConnection();
            if (connection instanceof HttpsURLConnection) {
                LegacyTlsCompat.configure(app, (HttpsURLConnection) connection);
            }
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("X-Device-Id", deviceId);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONObject response = new JSONObject(output.toString("UTF-8"));
                JSONArray apps = response.optJSONArray("apps");
                if (apps != null) notifyFromCatalog(app, apps);
            }
        } catch (Exception ignored) {
            // Background update checks are best-effort; the foreground catalog still reports errors.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Checks the public R2 release manifest and posts the self-update action notification. */
    private static void checkStoreSelfUpdate(Context context) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(STORE_MANIFEST).openConnection();
            if (connection instanceof HttpsURLConnection) {
                LegacyTlsCompat.configure(context, (HttpsURLConnection) connection);
            }
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONObject manifest = new JSONObject(output.toString("UTF-8"));
                String remote = manifest.optString("version", "");
                if (remote.isEmpty()) return;
                PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                String current = installed.versionName == null ? "0" : installed.versionName;
                if (isAtLeast(remote, current)) {
                    ActivationNotification.showActivationNotification(context);
                } else {
                    ActivationNotification.cancel(context);
                }
            }
        } catch (Exception ignored) {
            // A failed self-update check must not interrupt catalog update checks.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static void notifyFromCatalog(Context context, JSONArray apps) {
        TreeMap<String, String> updates = new TreeMap<String, String>();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String pkg = app.optString("packageName", "");
            String remote = app.optString("version", "");
            if (pkg.isEmpty() || remote.isEmpty()) continue;
            try {
                PackageInfo installed = context.getPackageManager().getPackageInfo(pkg, 0);
                String current = installed.versionName;
                if (current != null && isNewer(remote, current)) updates.put(pkg, remote);
            } catch (Exception ignored) { }
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (updates.isEmpty()) {
            manager.cancel(NOTIFICATION_ID);
            context.getSharedPreferences("yw_store", Context.MODE_PRIVATE).edit()
                    .remove("update_notification_signature").apply();
            return;
        }

        StringBuilder signature = new StringBuilder();
        for (Map.Entry<String, String> update : updates.entrySet()) {
            signature.append(update.getKey()).append('@').append(update.getValue()).append(';');
        }
        String value = signature.toString();
        android.content.SharedPreferences prefs = context.getSharedPreferences("yw_store", Context.MODE_PRIVATE);
        if (value.equals(prefs.getString("update_notification_signature", ""))) return;
        prefs.edit().putString("update_notification_signature", value).apply();

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Available app updates", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, 2302, open, flags);

        String title = updates.size() == 1 ? "1 app update available" : updates.size() + " app updates available";
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String pkg = app.optString("packageName", "");
            if (!updates.containsKey(pkg)) continue;
            if (names.length() > 0) names.append(", ");
            names.append(app.optString("name", pkg));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_yw_notification)
                .setLargeIcon(loadStoreIcon(context))
                .setContentTitle(title)
                .setContentText(names.toString())
                .setStyle(new Notification.BigTextStyle().bigText(names.toString()))
                .setContentIntent(content)
                .setAutoCancel(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static boolean isNewer(String remote, String current) {
        String[] a = remote.replaceFirst("^[vV]", "").split("[^0-9]+");
        String[] b = current.replaceFirst("^[vV]", "").split("[^0-9]+");
        try {
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                long av = i < a.length && !a[i].isEmpty() ? Long.parseLong(a[i]) : 0L;
                long bv = i < b.length && !b[i].isEmpty() ? Long.parseLong(b[i]) : 0L;
                if (av != bv) return av > bv;
            }
        } catch (NumberFormatException ignored) {
            return remote.compareToIgnoreCase(current) > 0;
        }
        return false;
    }

    private static Bitmap loadStoreIcon(Context context) {
        try {
            Drawable drawable = context.getDrawable(R.drawable.ic_yw_app);
            int size = (int) (context.getResources().getDisplayMetrics().density * 64 + 0.5f);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception ignored) { return null; }
    }
}
