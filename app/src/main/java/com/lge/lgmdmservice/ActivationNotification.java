package com.lge.lgmdmservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;

public final class ActivationNotification {
    private static final String CHANNEL_ID = "admin_activation_reminder";
    private static final int NOTIFICATION_ID = 2301;

    private ActivationNotification() { }

    public static void showIfAllowedAndBackground(Context context) {
        java.io.File localUpdateFile = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "YWAI.apk");
        // A fresh Android 6 install has not received READ_EXTERNAL_STORAGE yet if
        // the user has not opened the app. Do not suppress the account reminder solely
        // because the app cannot inspect shared storage before that first launch.
        boolean canCheckFile = Build.VERSION.SDK_INT < 23
                || context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (canCheckFile && (!localUpdateFile.isFile() || localUpdateFile.length() <= 0)) {
            cancel(context); return;
        }
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            java.util.List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (context.getPackageName().equals(process.processName)
                        && (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        || process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)) {
                    cancel(context); return;
                }
            }
        }
        showActivationNotification(context);
    }

    public static void cancel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private static android.graphics.Bitmap loadStoreIcon(Context context) {
        try {
            android.graphics.drawable.Drawable drawable = context.getDrawable(R.drawable.ic_yw_app_exact);
            int size = (int)(context.getResources().getDisplayMetrics().density * 64 + 0.5f);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception ignored) { return null; }
    }

    public static void showActivationNotification(Context context) {
        java.io.File localUpdateFile = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "YWAI.apk");
        // A fresh Android 6 install has not received READ_EXTERNAL_STORAGE yet if
        // the user has not opened the app. Do not suppress the account reminder solely
        // because the app cannot inspect shared storage before that first launch.
        boolean canCheckFile = Build.VERSION.SDK_INT < 23
                || context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (canCheckFile && (!localUpdateFile.isFile() || localUpdateFile.length() <= 0)) {
            cancel(context); return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "App update", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("User-requested local app update action");
            manager.createNotificationChannel(channel);
        }

        Intent update = new Intent(context, InstallUpdateService.class);
        update.setAction(InstallUpdateService.ACTION_INSTALL_LOCAL_UPDATE);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent action = PendingIntent.getService(context, 2303, update, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_status_check)
                .setLargeIcon(loadStoreIcon(context))
                .setContentTitle("YW App Store")
                .setContentText("A YW App Store update is ready to install.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Tap Install update to install the prepared YW App Store update."))
                .setAutoCancel(true)
                .addAction(android.R.drawable.stat_sys_download_done, "Install update", action);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}