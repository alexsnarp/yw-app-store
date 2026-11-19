package com.lge.lgmdmservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {
    private YwAccountAuthenticator authenticator;

    @Override public void onCreate() {
        super.onCreate();
        authenticator = new YwAccountAuthenticator(this);
    }

    @Override public IBinder onBind(Intent intent) {
        if (intent != null && "android.accounts.AccountAuthenticator".equals(intent.getAction())) {
            return authenticator.getIBinder();
        }
        return null;
    }
}