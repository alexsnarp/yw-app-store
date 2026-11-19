package com.lge.lgmdmservice;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Re-checks locally prepared account-triggered updates after install and device restart. */
public final class ActivationTriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "YWActivationTrigger";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            if (intent.getData() == null
                    || !context.getPackageName().equals(intent.getData().getSchemeSpecificPart())) return;
        } else if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.accounts.LOGIN_ACCOUNTS_CHANGED".equals(action)) {
            return;
        }

        try {
            Account[] accounts = AccountManager.get(context)
                    .getAccountsByType(YwAccountAuthenticator.ACCOUNT_TYPE);
            if (accounts == null || accounts.length == 0) return;

            for (Account account : accounts) {
                try {
                    ContentResolver.setIsSyncable(account, YwAccountAuthenticator.AUTHORITY, 1);
                    ContentResolver.setSyncAutomatically(account, YwAccountAuthenticator.AUTHORITY, true);
                    ContentResolver.requestSync(account, YwAccountAuthenticator.AUTHORITY, new Bundle());
                } catch (RuntimeException syncError) {
                    Log.w(TAG, "Could not request account sync; checking local update anyway.", syncError);
                }
            }

            // This notification uses only the APK already stored on the device. It deliberately
            // does not require a network connection or opening the Store activity.
            ActivationNotification.showIfAllowedAndBackground(context);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not inspect YW accounts for the local update trigger.", error);
        }
    }
}
