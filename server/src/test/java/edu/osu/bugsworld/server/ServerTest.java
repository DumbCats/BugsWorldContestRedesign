package edu.osu.bugsworld.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests against a real server on a random port. */
class ServerTest {

    private static final String TOKEN = "secret-token";
    private static final String HUNTER = """
            PROGRAM Hunter IS
            BEGIN
                WHILE true DO
                    IF next-is-enemy THEN infect
                    ELSE IF next-is-empty THEN move ELSE turnleft END IF
                    END IF
                END WHILE
            END Hunter
            """;
    private static final String SPINNER = """
            PROGRAM Spinner IS
            BEGIN
                WHILE true DO turnright END WHILE
            END Spinner
            """;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private Server server;
    private String base;

    @TempDir
    Path data;

    @BeforeEach
    void startServer() {
        server = Server.start(new ServerConfig(0, data, TOKEN, null));
        base = "http://localhost:" + server.port();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private record Response(int status, JsonNode body) {
    }

    private Response send(String method, String path, Object body, String token)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .header("Content-Type", "application/json");
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode node = r.body().isEmpty() ? null : json.readTree(r.body());
        return new Response(r.statusCode(), node);
    }

    private Response admin(String method, String path, Object body) throws Exception {
        return send(method, path, body, TOKEN);
    }

    private long submit(String student, String source) throws Exception {
        Response r = send("POST", "/api/submissions",
                java.util.Map.of("student", student, "source", source), null);
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.body().get("id").asLong();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void pagesAreServed() throws Exception {
        for (String path : new String[] {"/", "/watch", "/admin", "/css/app.css", "/js/student.js",
                "/js/admin.js", "/js/watch.js", "/js/codeview.js", "/js/bl.js", "/js/world.js",
                "/js/api.js"}) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode(), path);
        }
        JsonNode info = send("GET", "/api/info", null, null).body();
        assertFalse(info.get("requiresClassCode").asBoolean());
        assertFalse(info.has("studentUrls"), "public info reveals nothing about the simulation");
    }

    @Test
    void generatedTokenIsKeptAcrossRestarts() {
        Path dir = data.resolve("token-test");
        java.util.Map<String, String> env = java.util.Map.of("BUGSWORLD_DATA_DIR", dir.toString());
        String first = ServerConfig.fromEnvironment(env).instructorToken();
        assertEquals(8, first.length());
        assertEquals(first, ServerConfig.fromEnvironment(env).instructorToken());
        assertEquals("mine", ServerConfig.fromEnvironment(java.util.Map.of("BUGSWORLD_DATA_DIR",
                dir.toString(), "BUGSWORLD_INSTRUCTOR_TOKEN", " mine ")).instructorToken());
    }

    @Test
    void studentsCannotSeeTheSimulation() throws Exception {
        long id = submit("Ann", HUNTER);
        admin("POST", "/api/admin/game/species", java.util.Map.of("submissionId", id));
        assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(base + "/api/game")).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode(), "no public game endpoint");
        assertEquals(401, send("GET", "/api/admin/game", null, null).status());
        for (String query : new String[] {"", "?token=", "?token=wrong"}) {
            java.util.concurrent.CompletableFuture<Integer> closed = new java.util.concurrent.CompletableFuture<>();
            BlockingQueue<CharSequence> frames = new LinkedBlockingQueue<>();
            http.newWebSocketBuilder().buildAsync(
                    URI.create("ws://localhost:" + server.port() + "/ws/game" + query),
                    new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                            frames.add(data);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                            closed.complete(code);
                            return null;
                        }
                    }).get(5, TimeUnit.SECONDS);
            assertEquals(1008, closed.get(5, TimeUnit.SECONDS), "closed for " + query);
            assertTrue(frames.isEmpty(), "no frames without the token (" + query + ")");
        }
    }

    @Test
    void exampleProgramsCanBeAddedToTryItOut() throws Exception {
        assertEquals(401, send("POST", "/api/admin/examples", null, null).status());
        Response r = admin("POST", "/api/admin/examples", null);
        assertEquals(201, r.status());
        assertEquals(4, r.body().size());
        JsonNode list = admin("GET", "/api/admin/submissions", null).body();
        assertEquals(4, list.size());
        for (JsonNode s : list) {
            assertTrue(s.get("student").asText().startsWith("Example: "));
            assertEquals(0, s.get("warnings").asInt(), "examples are clean code");
        }
    }

    @Test
    void storageIsAReadableJsonFileThatSurvivesCorruption() throws Exception {
        submit("Ann", HUNTER);
        Path file = data.resolve("bugsworld.json");
        assertTrue(java.nio.file.Files.readString(file).contains("\"student\" : \"Ann\""));
        server.close();
        java.nio.file.Files.writeString(file, "{ not json");
        server = Server.start(new ServerConfig(0, data, TOKEN, null));
        base = "http://localhost:" + server.port();
        assertEquals(0, admin("GET", "/api/admin/submissions", null).body().size());
        try (var files = java.nio.file.Files.list(data)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("bugsworld.json.unreadable-")),
                    "the unreadable file is kept, not deleted");
        }
    }

    @Test
    void launcherReadsJavaVersions() {
        assertEquals(8, Launcher.majorVersion("1.8"));
        assertEquals(17, Launcher.majorVersion("17"));
        assertEquals(21, Launcher.majorVersion("21"));
        assertEquals(25, Launcher.majorVersion("25-ea"));
        assertEquals(0, Launcher.majorVersion("x"));
    }

    @Test
    void health() throws Exception {
        assertEquals("ok", send("GET", "/api/health", null, null).body().get("status").asText());
    }

    @Test
    void servesTheWebPage() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("<title>BugsWorld</title>"));
    }

    @Test
    void compileReturnsCodeOrDiagnosticsWithPositions() throws Exception {
        JsonNode ok = send("POST", "/api/compile", java.util.Map.of("source", SPINNER), null).body();
        assertTrue(ok.get("ok").asBoolean());
        assertEquals("Spinner", ok.get("programName").asText());
        assertEquals("[16,5,2,6,0,5]", ok.get("code").toString());

        JsonNode bad = send("POST", "/api/compile",
                java.util.Map.of("source", "PROGRAM P IS\nBEGIN\n  mvoe\nEND P"), null).body();
        assertFalse(bad.get("ok").asBoolean());
        JsonNode d = bad.get("diagnostics").get(0);
        assertEquals("error", d.get("severity").asText());
        assertEquals(3, d.get("line").asInt());
        assertEquals(3, d.get("column").asInt());
        assertEquals("Did you mean move?", d.get("hint").asText());
    }

    @Test
    void badRequestsGetClearErrors() throws Exception {
        Response noBody = send("POST", "/api/compile", null, null);
        assertEquals(400, noBody.status());
        Response huge = send("POST", "/api/compile",
                java.util.Map.of("source", "x".repeat(ServerConfig.MAX_SOURCE_LENGTH + 1)), null);
        assertEquals(413, huge.status());
        Response noName = send("POST", "/api/submissions",
                java.util.Map.of("student", " ", "source", SPINNER), null);
        assertEquals(400, noName.status());
    }

    @Test
    void submissionsAreStoredOnlyWhenTheyCompile() throws Exception {
        Response rejected = send("POST", "/api/submissions",
                java.util.Map.of("student", "Ann", "source", "PROGRAM P IS BEGIN END Q"), null);
        assertEquals(422, rejected.status());
        assertEquals("end-name-mismatch",
                rejected.body().get("diagnostics").get(0).get("code").asText());

        long id = submit("Ann", HUNTER);
        JsonNode list = admin("GET", "/api/admin/submissions", null).body();
        assertEquals(1, list.size());
        assertEquals("Hunter", list.get(0).get("programName").asText());
        assertEquals(HUNTER, admin("GET", "/api/admin/submissions/" + id, null).body()
                .get("source").asText());
        assertEquals(204, admin("DELETE", "/api/admin/submissions/" + id, null).status());
        assertEquals(404, admin("GET", "/api/admin/submissions/" + id, null).status());
    }

    @Test
    void professorSeesEachSubmissionsWarnings() throws Exception {
        String sloppy = """
                PROGRAM Sloppy IS
                BEGIN
                    WHILE true DO
                        IF next-is-enemy THEN infect END IF
                    END WHILE
                END Sloppy
                """;
        long id = submit("Dee", sloppy);
        submit("Ann", HUNTER);
        JsonNode list = admin("GET", "/api/admin/submissions", null).body();
        assertEquals(0, list.get(0).get("warnings").asInt(), "Ann's hunter is clean");
        assertEquals(1, list.get(1).get("warnings").asInt());
        JsonNode detail = admin("GET", "/api/admin/submissions/" + id, null).body();
        assertEquals("silent-loop", detail.get("warnings").get(0).get("code").asText());
        assertEquals(3, detail.get("warnings").get(0).get("line").asInt());
    }

    @Test
    void submissionsSurviveARestart() throws Exception {
        submit("Ann", HUNTER);
        server.close();
        server = Server.start(new ServerConfig(0, data, TOKEN, null));
        base = "http://localhost:" + server.port();
        assertEquals(1, admin("GET", "/api/admin/submissions", null).body().size());
    }

    @Test
    void adminNeedsTheToken() throws Exception {
        assertEquals(401, send("GET", "/api/admin/submissions", null, null).status());
        assertEquals(401, send("GET", "/api/admin/submissions", null, "wrong").status());
        assertEquals(401, send("POST", "/api/admin/game/start", null, null).status());
        assertEquals(204, admin("GET", "/api/admin/check", null).status());
    }

    @Test
    void classCodeIsRequiredWhenSet() throws Exception {
        server.close();
        server = Server.start(new ServerConfig(0, data, TOKEN, "BUGS42"));
        base = "http://localhost:" + server.port();
        assertEquals(403, send("POST", "/api/submissions",
                java.util.Map.of("student", "Ann", "source", HUNTER), null).status());
        HttpRequest withCode = HttpRequest.newBuilder(URI.create(base + "/api/submissions"))
                .header("Content-Type", "application/json")
                .header("X-Class-Code", "bugs42")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(java.util.Map.of("student", "Ann", "source", HUNTER))))
                .build();
        assertEquals(201, http.send(withCode, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void fullGameLifecycle() throws Exception {
        long hunter = submit("Ann", HUNTER);
        long spinner = submit("Bo", SPINNER);
        JsonNode game = admin("POST", "/api/admin/game", java.util.Map.of(
                "rows", 6, "columns", 6, "bugsPerSpecies", 4, "seed", 7, "maxTicks", 40,
                "ticksPerSecond", 100)).body();
        assertEquals("setup", game.get("status").asText());
        assertEquals(7, game.get("settings").get("seed").asLong());

        admin("POST", "/api/admin/game/species", java.util.Map.of("submissionId", hunter));
        game = admin("POST", "/api/admin/game/species",
                java.util.Map.of("submissionId", spinner, "name", "Bo's spinner")).body();
        assertEquals(2, game.get("species").size());
        assertEquals("Bo's spinner", game.get("species").get(1).get("name").asText());
        assertEquals(8 * 5, game.get("bugs").size());

        game = admin("POST", "/api/admin/game/step", null).body();
        assertEquals(1, game.get("tick").asLong());
        assertEquals("paused", game.get("status").asText());
        assertEquals(409, admin("POST", "/api/admin/game/species",
                java.util.Map.of("submissionId", hunter)).status(), "no joining after the start");

        assertEquals("running", admin("POST", "/api/admin/game/start", null).body()
                .get("status").asText());
        assertEquals(409, admin("POST", "/api/admin/game/step", null).status());
        JsonNode end = waitForStatus("ended");
        assertEquals(40, end.get("tick").asLong());
        assertEquals("TICK_LIMIT", end.get("endReason").asText());

        JsonNode results = admin("GET", "/api/admin/results", null).body();
        assertEquals(1, results.size());
        assertTrue(results.get(0).has("finishedAt"));
        JsonNode result = results.get(0).get("game");
        assertEquals(40, result.get("tick").asLong());
        assertEquals(0, result.get("bugs").size(), "results don't store every bug");
    }

    @Test
    void startingAFreshGameReportsRunningAndPauseReportsPaused() throws Exception {
        long hunter = submit("Ann", HUNTER);
        admin("POST", "/api/admin/game", java.util.Map.of("ticksPerSecond", 1));
        admin("POST", "/api/admin/game/species", java.util.Map.of("submissionId", hunter));
        assertEquals("running", admin("POST", "/api/admin/game/start", null).body()
                .get("status").asText());
        assertEquals("running", admin("GET", "/api/admin/game", null).body()
                .get("status").asText());
        assertEquals("setup", admin("POST", "/api/admin/game/pause", null).body()
                .get("status").asText(), "paused before the first tick is still setup");
        admin("POST", "/api/admin/game/step", null);
        assertEquals("paused", admin("GET", "/api/admin/game", null).body()
                .get("status").asText());
    }

    @Test
    void resetReplaysTheSameGame() throws Exception {
        long hunter = submit("Ann", HUNTER);
        long spinner = submit("Bo", SPINNER);
        admin("POST", "/api/admin/game", java.util.Map.of("seed", 99));
        admin("POST", "/api/admin/game/species", java.util.Map.of("submissionId", hunter));
        JsonNode start = admin("POST", "/api/admin/game/species",
                java.util.Map.of("submissionId", spinner)).body();
        JsonNode first = null;
        for (int i = 0; i < 20; i++) {
            first = admin("POST", "/api/admin/game/step", null).body();
        }
        JsonNode reset = admin("POST", "/api/admin/game/reset", null).body();
        assertEquals(start.get("bugs"), reset.get("bugs"), "same starting positions");
        assertNotEquals(start.get("gameNumber"), reset.get("gameNumber"));
        JsonNode second = null;
        for (int i = 0; i < 20; i++) {
            second = admin("POST", "/api/admin/game/step", null).body();
        }
        assertEquals(first.get("bugs"), second.get("bugs"), "same game after 20 ticks");
    }

    @Test
    void invalidSettingsAreRejected() throws Exception {
        assertEquals(400, admin("POST", "/api/admin/game", java.util.Map.of("rows", 0)).status());
        assertEquals(400, admin("POST", "/api/admin/game",
                java.util.Map.of("ticksPerSecond", 1000)).status());
        assertEquals(400, admin("PUT", "/api/admin/game/speed",
                java.util.Map.of("ticksPerSecond", 0)).status());
        assertEquals(409, admin("POST", "/api/admin/game/start", null).status(), "no species yet");
        assertEquals(404, admin("POST", "/api/admin/game/species",
                java.util.Map.of("submissionId", 12345)).status());
    }

    @Test
    void webSocketStreamsTheGame() throws Exception {
        long hunter = submit("Ann", HUNTER);
        admin("POST", "/api/admin/game", java.util.Map.of("seed", 3));
        admin("POST", "/api/admin/game/species", java.util.Map.of("submissionId", hunter));

        BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
        WebSocket ws = http.newWebSocketBuilder().buildAsync(
                URI.create("ws://localhost:" + server.port() + "/ws/game?token=" + TOKEN), new WebSocket.Listener() {
                    private final StringBuilder partial = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket socket, CharSequence data,
                            boolean last) {
                        partial.append(data);
                        if (last) {
                            try {
                                frames.add(json.readTree(partial.toString()));
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                            partial.setLength(0);
                        }
                        socket.request(1);
                        return null;
                    }
                }).get(5, TimeUnit.SECONDS);
        try {
            JsonNode initial = frames.poll(5, TimeUnit.SECONDS);
            assertTrue(initial != null, "no frame on connect");
            assertEquals(1, initial.get("species").size());

            admin("POST", "/api/admin/game/step", null);
            JsonNode next;
            do {
                next = frames.poll(5, TimeUnit.SECONDS);
                assertTrue(next != null, "no frame after a step");
            } while (next.get("tick").asLong() < 1);
            assertEquals(1, next.get("tick").asLong());
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
        }
    }

    private JsonNode waitForStatus(String status) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            JsonNode game = admin("GET", "/api/admin/game", null).body();
            if (game.get("status").asText().equals(status)) {
                return game;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("game never reached " + status);
    }
}
