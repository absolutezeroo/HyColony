package dev.hylens.core.hud;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.CitizenWellbeing;
import dev.hycolony.api.read.JobNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the HUD shows of a watched citizen (spec 2026-09-30, § 6.2): its name and job, then sections of labelled
 * fields. Its state: AI state and job step with how long they last, activity. Its walk: target and the blocks there,
 * last walk, stuck action. Its needs: job queue, leisure, saturation and happiness. Then its last {@link #HISTORY}
 * transitions and its alerts. Durations are in seconds of the core's clock (20 ticks per second).
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
     * Lines the HUD holds at most: the title, three sections of three fields, a header and {@link #HISTORY}, a header
     * and {@link #ALERTS}. The panel's #Lines (Hud/HyLens/WatchHud.ui) is sized for this many: 22 px of title, 20 px
     * per header and 18 px per field or alert.
     */
    static final int MAX_LINES = 1 + 3 * (1 + 3) + 1 + HISTORY + 1 + ALERTS;

    private static final int TICKS_PER_SECOND = 20;
    private static final ApiText NONE = ApiText.of("hylens.hud.none");

    private WatchHudView() {}

    /**
     * The lines for {@code citizen}, read at {@code s.tick()}, with how it fares ({@code wellbeing}, "-" when unknown),
     * its confirmed {@code alerts} and what the world holds at its walk target ({@code cell}, empty while unloaded).
     */
    public static List<HudLine> lines(
            CitizenSnapshot citizen,
            CitizenDebugSnapshot s,
            Optional<CitizenWellbeing> wellbeing,
            List<Violation> alerts,
            Optional<TargetCell> cell) {
        List<HudLine> out = new ArrayList<>(MAX_LINES);
        out.add(HudLine.title(ApiText.of("hylens.hud.title", citizen.name(), JobNames.of(citizen.job()))));
        state(out, s);
        walk(out, s, cell);
        needs(out, s, wellbeing);
        history(out, s);
        alerts(out, alerts);
        return out;
    }

    /** Its AI state and job step with how long they last, and its activity; no AI state while its body is unloaded. */
    private static void state(List<HudLine> out, CitizenDebugSnapshot s) {
        out.add(section("hylens.hud.section.state"));
        out.add(field(
                "hylens.hud.label.ai",
                s.aiState().isEmpty()
                        ? ApiText.of("hylens.hud.unloaded")
                        : since(s.aiState(), s.tick() - s.aiStateSince())));
        out.add(field(
                "hylens.hud.label.step",
                s.jobStep().isEmpty() ? NONE : since(s.jobStep(), s.tick() - s.jobStepSince())));
        out.add(field("hylens.hud.label.activity", s.activity().orElse(NONE)));
    }

    /** Its walk target, how its last walk ended and the stuck handler's last action. */
    private static void walk(List<HudLine> out, CitizenDebugSnapshot s, Optional<TargetCell> cell) {
        out.add(section("hylens.hud.section.walk"));
        out.add(field(
                "hylens.hud.label.target",
                s.walkTarget().map(t -> target(t, cell)).orElse(NONE)));
        out.add(field(
                "hylens.hud.label.walk",
                s.lastWalkEnd().map(WatchHudView::walkEnd).orElse(NONE)));
        out.add(field(
                "hylens.hud.label.stuck",
                s.lastStuck().isEmpty()
                        ? NONE
                        : ApiText.of("hylens.hud.stuck", s.lastStuck(), seconds(s.tick() - s.lastStuckTick()))));
    }

    /** Its job queue, its leisure (never below zero) and its saturation and happiness. */
    private static void needs(List<HudLine> out, CitizenDebugSnapshot s, Optional<CitizenWellbeing> wellbeing) {
        out.add(section("hylens.hud.section.needs"));
        out.add(HudLine.field(
                ApiText.of("hylens.hud.label.queue", String.valueOf(s.queue().size())), queue(s.queue())));
        out.add(field(
                "hylens.hud.label.leisure", ApiText.of("hylens.hud.seconds", seconds(Math.max(0, s.leisureTicks())))));
        out.add(field(
                "hylens.hud.label.saturation",
                wellbeing
                        .map(w -> ApiText.of(
                                "hylens.hud.wellbeing",
                                tenth(w.saturation()),
                                String.valueOf((int) w.maxSaturation()),
                                tenth(w.happiness())))
                        .orElse(NONE)));
    }

    /** A header and the last {@link #HISTORY} transitions, newest first, each with how long ago; none if empty. */
    private static void history(List<HudLine> out, CitizenDebugSnapshot s) {
        List<HistoryEntry> h = s.history();
        if (h.isEmpty()) {
            return;
        }
        out.add(section("hylens.hud.section.history"));
        for (int i = h.size() - 1; i >= Math.max(0, h.size() - HISTORY); i--) {
            HistoryEntry e = h.get(i);
            out.add(HudLine.field(ApiText.of("hylens.hud.ago", seconds(s.tick() - e.tick())), e.detail()));
        }
    }

    /** A header counting them, zero included, and the first {@link #ALERTS} alerts. */
    private static void alerts(List<HudLine> out, List<Violation> alerts) {
        out.add(HudLine.section(ApiText.of("hylens.hud.section.alerts", String.valueOf(alerts.size()))));
        alerts.stream().limit(ALERTS).forEach(v -> out.add(HudLine.alert(ApiText.of("hylens.hud.alert", v.detail()))));
    }

    /** The section header {@code key}, without parameters. */
    private static HudLine section(String key) {
        return HudLine.section(ApiText.of(key));
    }

    /** The field labelled {@code labelKey}, without parameters, showing {@code value}. */
    private static HudLine field(String labelKey, ApiText value) {
        return HudLine.field(ApiText.of(labelKey), value);
    }

    /** {@code state} and how many seconds it has lasted. */
    private static ApiText since(String state, long ticks) {
        return ApiText.of("hylens.hud.since", state, seconds(ticks));
    }

    /** The walk target, with the blocks there and below when its chunk is loaded. */
    private static ApiText target(Pos target, Optional<TargetCell> cell) {
        return cell.map(c -> ApiText.of("hylens.hud.cell", pos(target), cellContent(c.block()), cellContent(c.below())))
                .orElse(ApiText.of("hylens.hud.raw", pos(target)));
    }

    /** The target of its last walk, how it ended, the navigation's state then and how far from the target it stops. */
    private static ApiText walkEnd(WalkEnded w) {
        return ApiText.of(
                "hylens.hud.walk", pos(w.target()), w.how(), w.nav(), String.format(Locale.ROOT, "%.1f", w.distance()));
    }

    /** The first {@link #QUEUE} requests, then "..." when there are more; "-" when empty. */
    private static ApiText queue(List<String> queue) {
        if (queue.isEmpty()) {
            return NONE;
        }
        String shown = queue.stream()
                .limit(QUEUE)
                .map(id -> id.substring(0, Math.min(SHORT_ID, id.length())))
                .collect(Collectors.joining(", "));
        return ApiText.of("hylens.hud.raw", queue.size() > QUEUE ? shown + ", ..." : shown);
    }

    /** A block or fluid id as it is; nothing, said in the player's language. */
    private static Object cellContent(String id) {
        return id.isEmpty() ? ApiText.of("hylens.hud.empty") : id;
    }

    private static String pos(Pos p) {
        return p.x() + " " + p.y() + " " + p.z();
    }

    /** {@code value} with one decimal, whatever the locale; the menu shows saturation the same way. */
    public static String tenth(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String seconds(long ticks) {
        return String.valueOf(ticks / TICKS_PER_SECOND);
    }
}
