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
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class InstallUpdateService extends IntentService {
    public static final String ACTION_INSTALL_LOCAL_UPDATE = "com.lge.lginstallservies.action.INSTALL_LOCAL_UPDATE";
    private static final String CHANNEL_ID = "yw_local_update";
    private static final int NOTICE_ID = 2404;
    private static final String TAG = "YWLocalUpdate";

    public InstallUpdateService() { super("YWLocalUpdateService"); }

    @Override protected void onHandleIntent(Intent intent) {
        if (intent == null || !ACTION_INSTALL_LOCAL_UPDATE.equals(intent.getAction())) return;
        if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
            postStatus("Storage access unavailable", "Open YW App Store and approve storage access, then retry.");
            return;
        }

        File apk = new File(Environment.getExternalStorageDirectory(), "YWAI.apk");
        if (!apk.isFile() || !apk.canRead() || apk.length() <= 0) {
            postStatus("Update APK not found", "Place a readable update at /storage/emulated/0/YWAI.apk.");
            return;
        }

        PackageInstaller.Session session = null;
        PackageInstaller installer = null;
        int sessionId = -1;
        try {
            PackageManager pm = getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (archive == null || archive.packageName == null) {
                postStatus("Invalid update APK", "YWAI.apk could not be parsed as a signed APK.");
                return;
            }
            if (!getPackageName().equals(archive.packageName)) {
                postStatus("Wrong APK", "YWAI.apk is for " + archive.packageName + ", not " + getPackageName() + ".");
                return;
            }

            PackageInfo installed = pm.getPackageInfo(getPackageName(), flags);
            long installedVersion = Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode() : installed.versionCode;
            long updateVersion = Build.VERSION.SDK_INT >= 28
                    ? archive.getLongVersionCode() : archive.versionCode;
            if (updateVersion <= installedVersion) {
                postStatus("No newer update", "YWAI.apk version " + updateVersion
                        + " is not newer than installed version " + installedVersion + ".");
                return;
            }

            Signature[] installedSigners = getSignatures(installed);
            Signature[] updateSigners = getSignatures(archive);
            if (installedSigners == null || installedSigners.length == 0
                    || updateSigners == null || updateSigners.length == 0
                    || !sameSigners(installedSigners, updateSigners)) {
                postStatus("Signing key mismatch", "YWAI.apk is not signed with the installed app's certificate.");
                return;
            }

            installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            params.setSize(apk.length());
            sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            try (FileInputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }

            Intent callback = new Intent(this, InstallStatusReceiver.class);
            callback.setAction("com.lge.lginstallservies.LOCAL_UPDATE_STATUS." + sessionId);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) pendingFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(this, sessionId, callback, pendingFlags);
            session.commit(pending.getIntentSender());
            session.close();
            session = null;
        } catch (Exception error) {
            Log.e(TAG, "Could not submit local self-update.", error);
            if (session != null) {
                try { session.abandon(); } catch (Exception ignored) { }
                try { session.close(); } catch (Exception ignored) { }
            } else if (sessionId >= 0 && installer != null) {
                try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            }
            postStatus("Could not start update",
                    error.getMessage() == null ? "PackageInstaller could not process YWAI.apk." : error.getMessage());
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
                    CHANNEL_ID, "YW installer self-update", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setAutoCancel(true);
        manager.notify(NOTICE_ID, builder.build());
    }
}