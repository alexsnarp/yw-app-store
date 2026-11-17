package com.lge.lgmdmservice;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/** Shared network rule for catalog access and account-triggered notifications. */
public final class NetworkAccess {
    private NetworkAccess() { }

    public static boolean isAllowed(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        NetworkInfo wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        boolean wifiConnected = wifi != null && wifi.isConnected();
        NetworkInfo active = manager.getActiveNetworkInfo();
        boolean mobileConnected = active != null && active.isConnected()
                && active.getType() == ConnectivityManager.TYPE_MOBILE;
        boolean mobileAllowed = context.getSharedPreferences("yw_store", Context.MODE_PRIVATE)
                .getBoolean("mobile_data", false);
        return shouldAllow(wifiConnected, mobileConnected, mobileAllowed);
    }

    static boolean shouldAllow(boolean wifiConnected, boolean mobileConnected, boolean mobileAllowed) {
        return wifiConnected || (mobileConnected && mobileAllowed);
    }
}
