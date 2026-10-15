package org.wheelchair.blink;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

/** Camera traffic uses Wi-Fi; Azure uses a separately validated internet route. */
public final class NetworkRoutes implements AutoCloseable {
    private final ConnectivityManager manager;
    private volatile Network cellular;
    private boolean requested;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { cellular = network; }
        @Override public void onLost(Network network) { if (network.equals(cellular)) cellular = null; }
    };
    public NetworkRoutes(Context context) {
        manager = context.getSystemService(ConnectivityManager.class);
        try {
            manager.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback);
            requested = true;
        } catch (RuntimeException ignored) { /* Offline queuing remains available. */ }
    }
    public static URL cameraUrl(String value) throws Exception {
        URI uri = new URI(value.trim());
        String host = uri.getHost();
        if (!"http".equals(uri.getScheme()) || host == null || !host.matches("[0-9.]+")
                || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Use an HTTP camera URL with a local IP address.");
        InetAddress address = InetAddress.getByName(host);
        if (!address.isSiteLocalAddress() && !address.isLoopbackAddress() && !address.isLinkLocalAddress())
            throw new IllegalArgumentException("The camera must use a local network address.");
        return uri.toURL();
    }
    public HttpURLConnection camera(URL url) throws Exception {
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return (HttpURLConnection) network.openConnection(url);
        }
        // Ethernet and Android emulator networks can also reach a local camera.
        return (HttpURLConnection) url.openConnection();
    }
    private boolean usable(Network network) {
        if (network == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    public Network internet() {
        Network active = manager.getActiveNetwork();
        if (usable(active)) return active;
        if (usable(cellular)) return cellular;
        for (Network network : manager.getAllNetworks()) if (usable(network)) return network;
        return null;
    }
    @Override public void close() { if (requested) { manager.unregisterNetworkCallback(callback); requested = false; } }
}
