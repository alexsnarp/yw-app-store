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
        NetworkInfo active = manager.getActiveNetworkInfo();
        if (active == null || !active.isConnected()) return false;
        Network activeNetwork = manager.getActiveNetwork();
        NetworkCapabilities capabilities = activeNetwork == null
                ? null : manager.getNetworkCapabilities(activeNetwork);
        boolean internetValidated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (!internetValidated) return false;
        boolean wifiConnected = active.getType() == ConnectivityManager.TYPE_WIFI;
        boolean mobileConnected = active.getType() == ConnectivityManager.TYPE_MOBILE;
        boolean mobileAllowed = context.getSharedPreferences("yw_store", Context.MODE_PRIVATE)
                .getBoolean("mobile_data", false);
        return shouldAllow(wifiConnected, mobileConnected, mobileAllowed);
    }

    static boolean shouldAllow(boolean wifiConnected, boolean mobileConnected, boolean mobileAllowed) {
        return wifiConnected || (mobileConnected && mobileAllowed);
    }
}
