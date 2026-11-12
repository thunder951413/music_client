package com.eddyizm.tempus.subsonic.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

@RunWith(JUnit4.class)
public class CacheUtilTest {

    @Test
    public void vpnOverCellularWithInternetIsTreatedAsConnected() {
        NetworkCapabilities activeVpn = mock(NetworkCapabilities.class);
        when(activeVpn.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(activeVpn.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        NetworkCapabilities underlyingCellular = mock(NetworkCapabilities.class);
        when(underlyingCellular.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);
        when(underlyingCellular.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        assertTrue(CacheUtil.isCapabilitiesConnected(activeVpn, Collections.singletonList(underlyingCellular)));
    }

    @Test
    public void vpnAloneWithInternetWhenNoOtherNetworksVisibleIsTreatedAsConnected() {
        NetworkCapabilities activeVpn = mock(NetworkCapabilities.class);
        when(activeVpn.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(activeVpn.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        assertTrue(CacheUtil.isCapabilitiesConnected(activeVpn, Collections.emptyList()));
    }

    @Test
    public void vpnWithDisconnectedUnderlyingNetworkIsTreatedAsDisconnected() {
        NetworkCapabilities activeVpn = mock(NetworkCapabilities.class);
        when(activeVpn.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(activeVpn.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        NetworkCapabilities deadWifi = mock(NetworkCapabilities.class);
        when(deadWifi.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        when(deadWifi.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(false);

        assertFalse(CacheUtil.isCapabilitiesConnected(activeVpn, Collections.singletonList(deadWifi)));
    }

    @Test
    public void networkWithoutInternetCapabilityIsDisconnected() {
        NetworkCapabilities noInternet = mock(NetworkCapabilities.class);
        when(noInternet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true);
        when(noInternet.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(false);

        assertFalse(CacheUtil.isCapabilitiesConnected(noInternet, Collections.emptyList()));
    }
}
