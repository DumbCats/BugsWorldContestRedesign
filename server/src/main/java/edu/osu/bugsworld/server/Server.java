package edu.osu.bugsworld.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.sim.GameConfig;
import edu.osu.bugsworld.engine.vm.BugProgram;
import edu.osu.bugsworld.server.Api.AddSpeciesRequest;
import edu.osu.bugsworld.server.Api.CompileRequest;
import edu.osu.bugsworld.server.Api.CompileResponse;
import edu.osu.bugsworld.server.Api.DiagnosticJson;
import edu.osu.bugsworld.server.Api.ErrorResponse;
import edu.osu.bugsworld.server.Api.GameSettings;
import edu.osu.bugsworld.server.Api.SpeedRequest;
import edu.osu.bugsworld.server.Api.SubmissionRequest;
import edu.osu.bugsworld.server.Api.SubmissionResponse;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * The BugsWorld web server: HTTP API, live WebSocket, and static web pages.
 *
 * <pre>
 * Pages:  /  (students: editor, practice, submit)   /watch  (projector)   /admin  (professor)
 * Public (students, projector)
 *   GET  /api/health
 *   GET  /api/info                    {requiresClassCode, studentUrls}
 *   GET  /api/samples                 example bugs [{name, source}]
 *   POST /api/practice                {source, opponent, seed?}  a short game, returned as frames
 *   POST /api/compile                 {source}            compile only, nothing stored
 *   POST /api/submissions             {student, source}   needs X-Class-Code if one is set
 *   GET  /api/game                    current game (same JSON as the WebSocket)
 *   WS   /ws/game                     live game frames
 * Instructor (Authorization: Bearer TOKEN)
 *   GET    /api/admin/check
 *   GET    /api/admin/info            {studentUrls, classCode}
 *   GET    /api/admin/submissions     GET/DELETE /api/admin/submissions/{id}
 *   POST   /api/admin/game            new game {rows, columns, bugsPerSpecies, seed, ...}
 *   POST   /api/admin/game/species    {submissionId, name?}
 *   DELETE /api/admin/game/species/{id}
 *   POST   /api/admin/game/start|pause|step|end|reset
 *   PUT    /api/admin/game/speed      {ticksPerSecond}
 *   GET    /api/admin/results         finished games, newest first [{finishedAt, game}]
 * </pre>
 */
public final class Server implements AutoCloseable {

    /** An error with an HTTP status and a message for the user. */
    static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final ServerConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Database db;
    private final LiveViewers viewers;
    private final GameRunner runner;
    private final Javalin app;

    private Server(ServerConfig config) {
        this.config = config;
        this.db = Database.open(config.dataDir());
        this.viewers = LiveViewers.start(mapper);
        this.runner = GameRunner.start(viewers::publish, this::saveResult);
        this.app = Javalin.create(this::configure);
    }

    /** Starts a server; port 0 picks a free port (see {@link #port()}). */
    public static Server start(ServerConfig config) {
        Server server = new Server(config);
        server.app.start(config.port());
        return server;
    }

    public int port() {
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
        runner.close();
        viewers.close();
        db.close();
    }

    private void configure(JavalinConfig c) {
        c.startup.showJavalinBanner = false; // Main prints what the professor needs
        c.staticFiles.add("/public", Location.CLASSPATH);

        c.routes.exception(ApiException.class, (e, ctx) ->
                ctx.status(e.status).json(new ErrorResponse(e.getMessage())));
        c.routes.exception(IllegalArgumentException.class, (e, ctx) ->
                ctx.status(400).json(new ErrorResponse(e.getMessage())));
        c.routes.exception(IllegalStateException.class, (e, ctx) ->
                ctx.status(409).json(new ErrorResponse(e.getMessage())));

        // Pages (index.html is also served at / by the static file handler)
        c.routes.get("/watch", ctx -> page(ctx, "watch.html"));
        c.routes.get("/admin", ctx -> page(ctx, "admin.html"));

        // Public
        c.routes.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        c.routes.get("/api/info", ctx -> ctx.json(Map.of(
                "requiresClassCode", config.classCode() != null,
                "studentUrls", Network.studentUrls(port()))));
        c.routes.get("/api/samples", ctx -> ctx.json(Samples.all()));
        c.routes.post("/api/practice", this::practice);
        c.routes.post("/api/compile", this::compile);
        c.routes.post("/api/submissions", this::submit);
        c.routes.get("/api/game", ctx -> ctx.json(runner.view()));
        c.routes.ws("/ws/game", ws -> {
            ws.onConnect(viewers::connect);
            ws.onClose(viewers::disconnect);
            ws.onError(viewers::disconnect);
        });

        // Instructor
        c.routes.before("/api/admin/*", this::requireInstructor);
        c.routes.get("/api/admin/check", ctx -> ctx.status(204));
        c.routes.get("/api/admin/info", ctx -> {
            Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("studentUrls", Network.studentUrls(port()));
            info.put("classCode", config.classCode());
            ctx.json(info);
        });
        c.routes.get("/api/admin/submissions", ctx -> ctx.json(db.submissions().stream()
                .map(s -> Map.of("id", s.id(), "student", s.student(),
                        "programName", s.programName(), "createdAt", s.createdAt().toString()))
                .toList()));
        c.routes.get("/api/admin/submissions/{id}", ctx -> {
            Database.Submission s = submission(ctx.pathParam("id"));
            ctx.json(Map.of("id", s.id(), "student", s.student(), "programName",
                    s.programName(), "createdAt", s.createdAt().toString(), "source", s.source()));
        });
        c.routes.delete("/api/admin/submissions/{id}", ctx -> {
            if (!db.deleteSubmission(id(ctx.pathParam("id")))) {
                throw new ApiException(404, "No submission " + ctx.pathParam("id"));
            }
            ctx.status(204);
        });
        c.routes.post("/api/admin/game", this::newGame);
        c.routes.post("/api/admin/game/species", this::addSpecies);
        c.routes.delete("/api/admin/game/species/{id}", ctx ->
                ctx.json(runner.removeSpecies((int) id(ctx.pathParam("id")))));
        c.routes.post("/api/admin/game/start", ctx -> ctx.json(runner.start()));
        c.routes.post("/api/admin/game/pause", ctx -> ctx.json(runner.pause()));
        c.routes.post("/api/admin/game/step", ctx -> ctx.json(runner.step()));
        c.routes.post("/api/admin/game/end", ctx -> ctx.json(runner.end()));
        c.routes.post("/api/admin/game/reset", ctx -> ctx.json(runner.reset()));
        c.routes.put("/api/admin/game/speed", ctx -> {
            SpeedRequest req = body(ctx, SpeedRequest.class);
            if (req.ticksPerSecond() == null) {
                throw new ApiException(400, "ticksPerSecond is required");
            }
            ctx.json(runner.setSpeed(req.ticksPerSecond()));
        });
        c.routes.get("/api/admin/results", ctx -> {
            // Stored as JSON already; wrap without re-parsing.
            List<String> items = db.results().stream()
                    .map(r -> "{\"finishedAt\":\"" + r.finishedAt() + "\",\"game\":" + r.json() + "}")
                    .toList();
            ctx.contentType("application/json").result("[" + String.join(",", items) + "]");
        });
    }

    // ------------------------------------------------------------- handlers

    private void compile(Context ctx) {
        CompileRequest req = body(ctx, CompileRequest.class);
        ctx.json(CompileResponse.of(Compiler.compile(source(req.source()))));
    }

    private void practice(Context ctx) {
        Api.PracticeRequest req = body(ctx, Api.PracticeRequest.class);
        CompileResult mine = Compiler.compile(source(req.source()));
        if (!mine.succeeded()) {
            ctx.status(422).json(CompileResponse.of(mine));
            return;
        }
        String opponentName = req.opponent() == null ? "hunter" : req.opponent();
        BugProgram opponent = null;
        if (!opponentName.equals("none")) {
            Samples.Sample sample = Samples.named(opponentName).orElseThrow(
                    () -> new ApiException(400, "No example bug named " + opponentName));
            opponent = BugProgram.of(Compiler.compile(sample.source()).code());
        }
        long seed = req.seed() != null ? req.seed() : GameRunner.randomSeed();
        ctx.json(Practice.run(BugProgram.of(mine.code()), opponentName, opponent, seed));
    }

    private void submit(Context ctx) {
        if (config.classCode() != null) {
            String given = ctx.header("X-Class-Code");
            if (given == null || !config.classCode().equalsIgnoreCase(given.trim())) {
                throw new ApiException(403, "Wrong or missing class code");
            }
        }
        SubmissionRequest req = body(ctx, SubmissionRequest.class);
        String student = req.student() == null ? "" : req.student().strip();
        if (student.isEmpty() || student.length() > ServerConfig.MAX_NAME_LENGTH) {
            throw new ApiException(400, "Enter your name (at most "
                    + ServerConfig.MAX_NAME_LENGTH + " characters)");
        }
        String source = source(req.source());
        CompileResult r = Compiler.compile(source);
        if (!r.succeeded()) {
            ctx.status(422).json(CompileResponse.of(r));
            return;
        }
        Database.Submission s = db.addSubmission(student, r.program().name(), source);
        List<DiagnosticJson> warnings = r.warnings().stream().map(DiagnosticJson::of).toList();
        ctx.status(201).json(new SubmissionResponse(s.id(), student, s.programName(), warnings));
    }

    private void newGame(Context ctx) {
        GameSettings s = body(ctx, GameSettings.class);
        GameConfig cfg = GameConfig.defaults(s.seed() != null ? s.seed() : GameRunner.randomSeed());
        if (s.rows() != null || s.columns() != null) {
            cfg = cfg.withSize(s.rows() != null ? s.rows() : cfg.rows(),
                    s.columns() != null ? s.columns() : cfg.columns());
        }
        if (s.bugsPerSpecies() != null) {
            cfg = cfg.withBugsPerSpecies(s.bugsPerSpecies());
        }
        if (s.maxTicks() != null) {
            cfg = cfg.withMaxTicks(s.maxTicks());
        }
        if (s.endWhenOneSpeciesLeft() != null) {
            cfg = cfg.withEndWhenOneSpeciesLeft(s.endWhenOneSpeciesLeft());
        }
        if (s.infectedBugsActSameTick() != null) {
            cfg = cfg.withInfectedBugsActSameTick(s.infectedBugsActSameTick());
        }
        if (s.jumpBudget() != null) {
            cfg = cfg.withJumpBudget(s.jumpBudget());
        }
        int speed = s.ticksPerSecond() != null ? s.ticksPerSecond() : GameRunner.DEFAULT_SPEED;
        ctx.json(runner.newGame(cfg, speed));
    }

    private void addSpecies(Context ctx) {
        AddSpeciesRequest req = body(ctx, AddSpeciesRequest.class);
        if (req.submissionId() == null) {
            throw new ApiException(400, "submissionId is required");
        }
        Database.Submission s = submission(String.valueOf(req.submissionId()));
        CompileResult r = Compiler.compile(s.source());
        if (!r.succeeded()) {
            throw new ApiException(409, "Submission " + s.id() + " no longer compiles");
        }
        String name = req.name() != null && !req.name().isBlank() ? req.name().strip()
                : s.student();
        ctx.json(runner.addSpecies(name, s.id(), BugProgram.of(r.code())));
    }

    // -------------------------------------------------------------- helpers

    /** Serves a page from the bundled web files. */
    private static void page(Context ctx, String file) {
        java.io.InputStream in = Server.class.getResourceAsStream("/public/" + file);
        if (in == null) {
            throw new ApiException(404, "Missing page " + file);
        }
        ctx.contentType("text/html; charset=utf-8").result(in);
    }

    private void requireInstructor(Context ctx) {
        String header = ctx.header("Authorization");
        String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim()
                : "";
        boolean ok = MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                config.instructorToken().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ApiException(401, "Instructor token required");
        }
    }

    private void saveResult(GameView result) {
        try {
            db.addResult(mapper.writeValueAsString(result));
        } catch (JsonProcessingException | RuntimeException e) {
            e.printStackTrace(); // never let saving a result break the game thread
        }
    }

    private Database.Submission submission(String id) {
        return db.submission(id(id)).orElseThrow(() -> new ApiException(404, "No submission " + id));
    }

    private static long id(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Not a valid id: " + text);
        }
    }

    private static String source(String source) {
        if (source == null) {
            throw new ApiException(400, "source is required");
        }
        if (source.length() > ServerConfig.MAX_SOURCE_LENGTH) {
            throw new ApiException(413, "Programs can be at most "
                    + ServerConfig.MAX_SOURCE_LENGTH + " characters");
        }
        return source;
    }

    private static <T> T body(Context ctx, Class<T> type) {
        if (ctx.body().isBlank()) {
            throw new ApiException(400, "Request body is missing");
        }
        T value;
        try {
            value = ctx.bodyAsClass(type);
        } catch (Exception e) {
            // Javalin (Kotlin) rethrows Jackson's checked exceptions undeclared.
            throw new ApiException(400, "Request body is not valid JSON for this endpoint");
        }
        if (value == null) {
            throw new ApiException(400, "Request body is missing");
        }
        return value;
    }
}
