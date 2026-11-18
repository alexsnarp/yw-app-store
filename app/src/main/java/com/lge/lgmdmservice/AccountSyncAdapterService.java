package com.lge.lgmdmservice;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.ActivityManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class AccountSyncAdapterService extends Service {
    private static final String TAG = "YWAccountSync";
    private AbstractThreadedSyncAdapter syncAdapter;

    @Override public void onCreate() {
        super.onCreate();
        final Context appContext = getApplicationContext();
        try {
            Account[] accounts = AccountManager.get(this).getAccountsByType(YwAccountAuthenticator.ACCOUNT_TYPE);
            for (Account account : accounts) {
                ContentResolver.setIsSyncable(account, YwAccountAuthenticator.AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(account, YwAccountAuthenticator.AUTHORITY, true);
                // Enabling automatic sync does not itself guarantee an immediate callback
                // for an account that already existed before this service was created.
                // Request one initial sync per account, but do not re-request on every service
                // bind (which could otherwise keep the adapter scheduling itself).
                String key = "initial_sync_requested_" + account.name;
                android.content.SharedPreferences state = appContext.getSharedPreferences("yw_store", Context.MODE_PRIVATE);
                if (!state.getBoolean(key, false)) {
                    state.edit().putBoolean(key, true).apply();
                    ContentResolver.requestSync(account, YwAccountAuthenticator.AUTHORITY, new Bundle());
                    Log.i(TAG, "Requested initial account sync for " + account.name + ".");
                }
                Log.i(TAG, "Automatic account sync is enabled for " + account.name + ".");
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not initialize automatic sync for existing YW accounts.", error);
        }

        syncAdapter = new AbstractThreadedSyncAdapter(this, true, false) {
            @Override public void onPerformSync(Account account, Bundle extras, String authority,
                    ContentProviderClient provider, SyncResult syncResult) {
                NotificationManager manager = (NotificationManager)
                        appContext.getSystemService(Context.NOTIFICATION_SERVICE);
                Log.i(TAG, "Account sync callback received for " + account.name + ".");
                if (isMainActivityForeground(appContext)) {
                    if (manager != null) manager.cancel(2301);
                    Log.i(TAG, "Main screen is active; activation notification suppressed.");
                    return;
                }
                if (!NetworkAccess.isAllowed(appContext)) {
                    if (manager != null) manager.cancel(2301);
                    Log.i(TAG, "No permitted Wi-Fi/mobile network; activation notification suppressed.");
                    return;
                }
                Log.i(TAG, "Account sync trigger received on an allowed network; posting notification.");
                ActivationNotification.showActivationNotification(appContext);
                Log.i(TAG, "Activation notification posted.");
            }
        };
    }

    private boolean isMainActivityForeground(Context context) {
        ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return context.getSharedPreferences("yw_store", Context.MODE_PRIVATE)
                    .getBoolean("main_activity_launched", false);
        }
        java.util.List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        if (processes == null) {
            return context.getSharedPreferences("yw_store", Context.MODE_PRIVATE)
                    .getBoolean("main_activity_launched", false);
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (context.getPackageName().equals(process.processName)) {
                return process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        || process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
            }
        }
        return false;
    }

    @Override public IBinder onBind(Intent intent) {
        if (intent != null && "android.content.SyncAdapter".equals(intent.getAction())) {
            return syncAdapter.getSyncAdapterBinder();
        }
        return null;
    }
}