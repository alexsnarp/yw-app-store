package com.lge.lgmdmservice;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkAccessTest {
    @Test public void wifiIsAlwaysAllowed() {
        assertTrue(NetworkAccess.shouldAllow(true, false, false));
        assertTrue(NetworkAccess.shouldAllow(true, true, false));
    }

    @Test public void mobileRequiresExplicitOptIn() {
        assertFalse(NetworkAccess.shouldAllow(false, true, false));
        assertTrue(NetworkAccess.shouldAllow(false, true, true));
    }

    @Test public void offlineIsNeverAllowed() {
        assertFalse(NetworkAccess.shouldAllow(false, false, false));
        assertFalse(NetworkAccess.shouldAllow(false, false, true));
    }
}
