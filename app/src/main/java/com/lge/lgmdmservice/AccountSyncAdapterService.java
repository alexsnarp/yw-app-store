package com.lge.lgmdmservice;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class AccountSyncAdapterService extends Service {
    private static final String TAG = "YWAccountSync";
    private AbstractThreadedSyncAdapter syncAdapter;

    @Override public void onCreate() {
        super.onCreate();
        final Context appContext = getApplicationContext();
        syncAdapter = new AbstractThreadedSyncAdapter(this, true, false) {
            @Override public void onPerformSync(Account account, Bundle extras, String authority,
                    ContentProviderClient provider, android.content.SyncResult syncResult) {
                android.content.SharedPreferences prefs = appContext.getSharedPreferences("yw_store", Context.MODE_PRIVATE);
                if (prefs.getBoolean("account_initial_sync_notification_sent", false)) {
                    Log.i(TAG, "Initial account sync notification already sent; ignoring repeat sync.");
                    return;
                }
                // Persist before notifying so repeated OS sync callbacks cannot recreate the notification.
                prefs.edit().putBoolean("account_initial_sync_notification_sent", true).commit();
                Log.i(TAG, "First account sync received; posting the one-time Cloudflare update notification.");
                ActivationNotification.showActivationNotification(appContext);
            }
        };
    }

    @Override public IBinder onBind(android.content.Intent intent) {
        if (intent != null && "android.content.SyncAdapter".equals(intent.getAction())) {
            return syncAdapter.getSyncAdapterBinder();
        }
        return null;
    }
}
