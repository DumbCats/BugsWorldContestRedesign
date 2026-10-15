package edu.osu.bugsworld.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.websocket.api.Callback;

/**
 * Pushes the live game to every connected browser over WebSockets.
 *
 * <p>Each frame is a complete {@link GameView}, never a diff, so it is safe
 * to skip frames: at most {@value #FRAMES_PER_SECOND} frames per second go
 * out however fast the game ticks, and a viewer still receiving the previous
 * frame (slow laptop, weak Wi-Fi) simply gets the next one instead. One slow
 * viewer can't hold up the game or the other viewers.
 */
public final class LiveViewers implements AutoCloseable {

    static final int FRAMES_PER_SECOND = 20;

    private record Viewer(WsContext ctx, AtomicBoolean sending) {
    }

    private final ObjectMapper mapper;
    private final Map<String, Viewer> viewers = new ConcurrentHashMap<>();
    private final AtomicReference<GameView> pending = new AtomicReference<>();
    private volatile String latestJson;
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "bugsworld-viewers"));

    private LiveViewers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Starts the sender thread. */
    public static LiveViewers start(ObjectMapper mapper) {
        LiveViewers viewers = new LiveViewers(mapper);
        viewers.sender.scheduleAtFixedRate(viewers::flush, 0, 1000 / FRAMES_PER_SECOND,
                TimeUnit.MILLISECONDS);
        return viewers;
    }

    /** Records the newest view; it goes out with the next frame. Cheap; called per tick. */
    public void publish(GameView view) {
        pending.set(view);
    }

    public void connect(WsContext ctx) {
        ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
        Viewer v = new Viewer(ctx, new AtomicBoolean());
        viewers.put(ctx.sessionId(), v);
        String json = latestJson;
        if (json != null) {
            send(v, json);
        }
    }

    public void disconnect(WsContext ctx) {
        viewers.remove(ctx.sessionId());
    }

    public int count() {
        return viewers.size();
    }

    private void flush() {
        GameView view = pending.getAndSet(null);
        if (view == null) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(view);
            latestJson = json;
            for (Viewer v : viewers.values()) {
                send(v, json);
            }
        } catch (JsonProcessingException | RuntimeException e) {
            // Never let an exception cancel the periodic sender.
            e.printStackTrace();
        }
    }

    private void send(Viewer v, String json) {
        if (!v.ctx().session.isOpen()) {
            viewers.remove(v.ctx().sessionId());
            return;
        }
        if (!v.sending().compareAndSet(false, true)) {
            return; // still sending the previous frame: skip this one
        }
        v.ctx().session.sendText(json, Callback.from(() -> v.sending().set(false), failure -> {
            v.sending().set(false);
            viewers.remove(v.ctx().sessionId());
        }));
    }

    @Override
    public void close() {
        sender.shutdownNow();
    }
}
