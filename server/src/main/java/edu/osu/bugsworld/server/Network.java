package edu.osu.bugsworld.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds the addresses students can use to reach this computer. */
final class Network {

    private Network() {
    }

    /** {@code http://address:port} for each IPv4 address other computers may reach. */
    static List<String> studentUrls(int port) {
        List<String> urls = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLinkLocalAddress()) {
                        urls.add("http://" + a.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException e) {
            // return whatever we found
        }
        return urls;
    }
}
