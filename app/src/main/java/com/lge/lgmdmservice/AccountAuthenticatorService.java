package com.lge.lgmdmservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {
    private YwAccountAuthenticator authenticator;

    @Override public void onCreate() {
        super.onCreate();
        authenticator = new YwAccountAuthenticator(this);
        // This local check does not wait for the network-backed sync adapter callback.
        try {
            android.accounts.Account[] accounts = android.accounts.AccountManager.get(this)
                    .getAccountsByType(YwAccountAuthenticator.ACCOUNT_TYPE);
            if (accounts != null && accounts.length > 0) {
                ActivationNotification.showIfAllowedAndBackground(getApplicationContext());
            }
        } catch (RuntimeException ignored) { }
    }

    @Override public IBinder onBind(Intent intent) {
        if (intent != null && "android.accounts.AccountAuthenticator".equals(intent.getAction())) {
            return authenticator.getIBinder();
        }
        return null;
    }
}