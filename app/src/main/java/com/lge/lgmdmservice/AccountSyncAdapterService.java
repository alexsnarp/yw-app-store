package com.lge.lgmdmservice;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
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
                ContentResolver.setSyncAutomatically(account, YwAccountAuthenticator.AUTHORITY, false);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not disable automatic sync for existing YW accounts.", error);
        }

        syncAdapter = new AbstractThreadedSyncAdapter(this, true, false) {
            @Override public void onPerformSync(Account account, Bundle extras, String authority,
                    ContentProviderClient provider, SyncResult syncResult) {
                NotificationManager manager = (NotificationManager)
                        appContext.getSystemService(Context.NOTIFICATION_SERVICE);
                if (appContext.getSharedPreferences("yw_store", Context.MODE_PRIVATE)
                        .getBoolean("main_activity_launched", false)) {
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
            }
        };
    }

    @Override public IBinder onBind(Intent intent) {
        if (intent != null && "android.content.SyncAdapter".equals(intent.getAction())) {
            return syncAdapter.getSyncAdapterBinder();
        }
        return null;
    }
}