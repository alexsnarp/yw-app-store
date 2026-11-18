package com.lge.lgmdmservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ActivationNotification {
    private static final String CHANNEL_ID = "admin_activation_reminder";
    private static final int NOTIFICATION_ID = 2301;

    private ActivationNotification() { }

    public static void showActivationNotification(Context context) {
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
        builder.setSmallIcon(R.drawable.ic_yw_notification)
                .setContentTitle("YW App Store")
                .setContentText("A YW App Store update is ready to install.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Tap Install update to install the prepared YW App Store update."))
                .setAutoCancel(true)
                .addAction(android.R.drawable.stat_sys_download_done, "Install update", action);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}