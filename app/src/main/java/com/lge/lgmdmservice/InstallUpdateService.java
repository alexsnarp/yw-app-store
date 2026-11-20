package com.lge.lgmdmservice;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class InstallUpdateService extends IntentService {
    public static final String ACTION_INSTALL_CLOUDFLARE_UPDATE = "com.lge.lginstallservies.action.INSTALL_CLOUDFLARE_UPDATE";
    private static final String UPDATE_APK_URL = "https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/yw-app-store.apk";
    private static final String CHANNEL_ID = "yw_cloudflare_update";
    private static final int NOTICE_ID = 2404;
    private static final String TAG = "YWCloudflareUpdate";

    public InstallUpdateService() { super("YWCloudflareUpdateService"); }

    @Override protected void onHandleIntent(Intent intent) {
        if (intent == null || !ACTION_INSTALL_CLOUDFLARE_UPDATE.equals(intent.getAction())) return;
        if (!NetworkAccess.isAllowed(this)) {
            postStatus("Internet connection required", "Connect to the internet and tap Install update again.");
            return;
        }
        File apk = new File(getCacheDir(), "yw-app-store-update.apk");
        File partial = new File(apk.getAbsolutePath() + ".part");
        HttpURLConnection connection = null;
        PackageInstaller.Session session = null;
        PackageInstaller installer = null;
        int sessionId = -1;
        try {
            connection = (HttpURLConnection) new URL(UPDATE_APK_URL).openConnection();
            if (connection instanceof HttpsURLConnection) LegacyTlsCompat.configure(this, (HttpsURLConnection) connection);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(600000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new java.io.IOException("Cloudflare returned HTTP " + status);
            long total = connection.getContentLengthLong();
            long done = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    done += count;
                }
                output.flush();
            }
            if (partial.length() < 1024 || (total > 0 && done != total)) throw new java.io.IOException("The downloaded update is incomplete.");

            PackageManager pm = getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = pm.getPackageArchiveInfo(partial.getAbsolutePath(), flags);
            if (archive == null || !getPackageName().equals(archive.packageName)) throw new java.io.IOException("The Cloudflare file is not a YW App Store APK.");
            PackageInfo installed = pm.getPackageInfo(getPackageName(), flags);
            long installedVersion = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
            long updateVersion = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
            if (updateVersion <= installedVersion) {
                partial.delete();
                postStatus("Already up to date", "The installed YW App Store is already at the latest version.");
                return;
            }
            Signature[] oldSigners = getSignatures(installed);
            Signature[] newSigners = getSignatures(archive);
            if (oldSigners == null || newSigners == null || !sameSigners(oldSigners, newSigners)) {
                throw new java.io.IOException("The update signing certificate does not match the installed App Store.");
            }

            if (apk.exists() && !apk.delete()) throw new java.io.IOException("Could not replace the temporary update file.");
            if (!partial.renameTo(apk)) throw new java.io.IOException("Could not prepare the temporary update file.");

            installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            params.setSize(apk.length());
            sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);
            try (InputStream input = new java.io.FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }
            Intent callback = new Intent(this, InstallStatusReceiver.class);
            callback.setAction("com.lge.lginstallservies.CLOUDFLARE_UPDATE_STATUS." + sessionId);
            callback.putExtra("yw_package_name", getPackageName());
            callback.putExtra("yw_version", archive.versionName);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) pendingFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(this, sessionId, callback, pendingFlags);
            session.commit(pending.getIntentSender());
            session.close();
            session = null;
            apk.delete();
        } catch (Exception error) {
            Log.e(TAG, "Could not download or submit the Cloudflare App Store update.", error);
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) { }
                try { session.close(); } catch (Exception ignored) { }
            } else if (sessionId >= 0 && installer != null) {
                try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            }
            partial.delete();
            apk.delete();
            postStatus("Could not start update", error.getMessage() == null
                    ? "Please try again from the update notification." : error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Signature[] getSignatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            SigningInfo signing = info.signingInfo;
            return signing == null ? null : signing.getApkContentsSigners();
        }
        return info.signatures;
    }

    private boolean sameSigners(Signature[] a, Signature[] b) {
        if (a.length != b.length) return false;
        boolean[] matched = new boolean[b.length];
        for (Signature signer : a) {
            boolean found = false;
            for (int i = 0; i < b.length; i++) {
                if (!matched[i] && signer.equals(b[i])) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void postStatus(String title, String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "YW App Store update", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_yw_notification)
                .setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true);
        manager.notify(NOTICE_ID, builder.build());
    }
}
