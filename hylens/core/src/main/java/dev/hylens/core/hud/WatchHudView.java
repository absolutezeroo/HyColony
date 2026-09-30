package dev.hylens.core.hud;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the HUD shows of a watched citizen, one text per line (spec 2026-09-30, § 6.2): its job, its AI state and job
 * step with how long they last, its activity, walk target and the blocks there, last walk and stuck action, job queue,
 * leisure, its last {@link #HISTORY} transitions and its alerts. Durations are in seconds of the core's clock (20
 * ticks per second).
 */
public final class WatchHudView {
    /** Transitions shown, newest first. */
    static final int HISTORY = 5;
    /** Alerts shown; the header counts them all. */
    static final int ALERTS = 3;
    /** Queued requests named; the rest are counted. */
    static final int QUEUE = 3;
    /** Characters of a request id shown: HyColony's alerts name requests the same way (CitizenInvariants). */
    static final int SHORT_ID = 8;
    /**
     * Lines the HUD holds at most: 10 of state, a header and {@link #HISTORY}, a header and {@link #ALERTS}. The panel
     * (Hud/HyLens/WatchHud.ui) is exactly this many lines of 20 px high.
     */
    public static final int MAX_LINES = 10 + 1 + HISTORY + 1 + ALERTS;

    private static final int TICKS_PER_SECOND = 20;
    private static final String NONE = "-";

    private WatchHudView() {}

    /**
     * The lines for {@code citizen}, read at {@code s.tick()}, with its confirmed {@code alerts} and what the world
     * holds at its walk target ({@code cell}, empty while unloaded).
     */
    public static List<ApiText> lines(
            CitizenSnapshot citizen, CitizenDebugSnapshot s, List<Violation> alerts, Optional<TargetCell> cell) {
        List<ApiText> out = new ArrayList<>(MAX_LINES);
        out.add(ApiText.of("hylens.hud.title", citizen.name()));
        out.add(ApiText.of("hylens.hud.job", citizen.job().orElse(NONE)));
        out.add(
                s.aiState().isEmpty()
                        ? ApiText.of("hylens.hud.unloaded")
                        : ApiText.of("hylens.hud.ai", s.aiState(), seconds(s.tick() - s.aiStateSince())));
        out.add(
                s.jobStep().isEmpty()
                        ? ApiText.of("hylens.hud.step", NONE, "0")
                        : ApiText.of("hylens.hud.step", s.jobStep(), seconds(s.tick() - s.jobStepSince())));
        out.add(s.activity()
                .map(a -> ApiText.of("hylens.hud.activity", a))
                .orElse(ApiText.of("hylens.hud.activity", NONE)));
        out.add(s.walkTarget().map(t -> target(t, cell)).orElse(ApiText.of("hylens.hud.target", NONE)));
        out.add(s.lastWalkEnd().map(WatchHudView::walk).orElse(ApiText.of("hylens.hud.walkNone")));
        out.add(
                s.lastStuck().isEmpty()
                        ? ApiText.of("hylens.hud.stuckNone")
                        : ApiText.of("hylens.hud.stuck", s.lastStuck(), seconds(s.tick() - s.lastStuckTick())));
        out.add(ApiText.of("hylens.hud.queue", String.valueOf(s.queue().size()), queue(s.queue())));
        out.add(ApiText.of("hylens.hud.leisure", seconds(s.leisureTicks())));
        history(out, s);
        alerts(out, alerts);
        return out;
    }

    /** A header and the last {@link #HISTORY} transitions, newest first, each with how long ago; none if empty. */
    private static void history(List<ApiText> out, CitizenDebugSnapshot s) {
        List<HistoryEntry> h = s.history();
        if (h.isEmpty()) {
            return;
        }
        out.add(ApiText.of("hylens.hud.history"));
        for (int i = h.size() - 1; i >= Math.max(0, h.size() - HISTORY); i--) {
            HistoryEntry e = h.get(i);
            out.add(ApiText.of("hylens.hud.historyEntry", seconds(s.tick() - e.tick()), e.detail()));
        }
    }

    /** A header counting them and the first {@link #ALERTS} alerts, or a line saying there are none. */
    private static void alerts(List<ApiText> out, List<Violation> alerts) {
        if (alerts.isEmpty()) {
            out.add(ApiText.of("hylens.hud.noAlerts"));
            return;
        }
        out.add(ApiText.of("hylens.hud.alerts", String.valueOf(alerts.size())));
        alerts.stream().limit(ALERTS).forEach(v -> out.add(ApiText.of("hylens.hud.alert", v.detail())));
    }

    /** The walk target, with the blocks there and below when its chunk is loaded. */
    private static ApiText target(Pos target, Optional<TargetCell> cell) {
        return cell.map(c -> ApiText.of(
                        "hylens.hud.targetCell", pos(target), cellContent(c.block()), cellContent(c.below())))
                .orElse(ApiText.of("hylens.hud.target", pos(target)));
    }

    private static ApiText walk(WalkEnded w) {
        return ApiText.of(
                "hylens.hud.walk", pos(w.target()), w.how(), w.nav(), String.format(Locale.ROOT, "%.1f", w.distance()));
    }

    /** The first {@link #QUEUE} requests, then "..." when there are more; "-" when empty. */
    private static String queue(List<String> queue) {
        if (queue.isEmpty()) {
            return NONE;
        }
        String shown = queue.stream()
                .limit(QUEUE)
                .map(id -> id.substring(0, Math.min(SHORT_ID, id.length())))
                .collect(Collectors.joining(", "));
        return queue.size() > QUEUE ? shown + ", ..." : shown;
    }

    /** A block or fluid id as it is; nothing, said in the player's language. */
    private static Object cellContent(String id) {
        return id.isEmpty() ? ApiText.of("hylens.hud.empty") : id;
    }

    private static String pos(Pos p) {
        return p.x() + " " + p.y() + " " + p.z();
    }

    private static String seconds(long ticks) {
        return String.valueOf(ticks / TICKS_PER_SECOND);
    }
}
