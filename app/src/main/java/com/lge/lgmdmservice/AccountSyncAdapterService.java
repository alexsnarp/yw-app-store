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
                if (extras == null || !extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false)) {
                    Log.i(TAG, "Ignoring automatic/non-manual sync; waiting for Sync now.");
                    return;
                }
                Log.i(TAG, "Manual account sync requested; posting the Cloudflare update notification.");
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
