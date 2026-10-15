package edu.osu.bugsworld.server;

import io.javalin.Javalin;
import java.util.Map;

/** Entry point for the BugsWorld web lab backend. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        Javalin.create(config -> {
            config.routes.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        }).start(port);
    }
}
