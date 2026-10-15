package edu.osu.bugsworld.server;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Starts the BugsWorld server and prints how to reach it. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.fromEnvironment(System.getenv());
        Server server = Server.start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

        System.out.println();
        System.out.println("BugsWorld is running.");
        System.out.println("  On this computer:  http://localhost:" + server.port());
        for (String address : lanAddresses()) {
            System.out.println("  For students:      http://" + address + ":" + server.port());
        }
        System.out.println("  Instructor token:  " + config.instructorToken()
                + (System.getenv("BUGSWORLD_INSTRUCTOR_TOKEN") == null
                        ? "   (random; set BUGSWORLD_INSTRUCTOR_TOKEN to keep one)" : ""));
        System.out.println("  Class code:        " + (config.classCode() == null
                ? "none (set BUGSWORLD_CLASS_CODE to require one)" : config.classCode()));
        System.out.println("  Data:              " + config.dataDir().toAbsolutePath());
        System.out.println();
    }

    /** IPv4 addresses other computers on the network may be able to reach. */
    private static List<String> lanAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLinkLocalAddress()) {
                        addresses.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // fall through with whatever we found
        }
        return addresses;
    }
}
