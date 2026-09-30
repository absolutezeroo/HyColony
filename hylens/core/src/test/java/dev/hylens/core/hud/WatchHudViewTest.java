package dev.hylens.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.HistoryEntry;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.debug.WalkEnded;
import dev.hycolony.api.read.CitizenSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What the HUD shows of a watched citizen (spec 2026-09-30, § 6.2). */
class WatchHudViewTest {
    private static final CitizenRef ANN = new CitizenRef(new ColonyRef("default", 1), 4);
    private static final long NOW = 2_000;
    /** A request id as the api gives it: a whole UUID. */
    private static final String R1 = "11111111-aaaa-bbbb-cccc-dddddddddddd";

    private static final CitizenSnapshot NAMED =
            new CitizenSnapshot(ANN, "Ann", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static CitizenDebugSnapshot idle() {
        return new CitizenDebugSnapshot(
                ANN,
                NOW,
                "IDLE",
                NOW - 100,
                "",
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                0,
                List.of(),
                0,
                List.of());
    }

    private static CitizenDebugSnapshot working() {
        return new CitizenDebugSnapshot(
                ANN,
                NOW,
                "WORK",
                NOW - 400,
                "PICKUP",
                NOW - 60,
                Optional.of(ApiText.of("hycolony.activity.delivering", "Planks")),
                Optional.of(new Pos(10, 64, -3)),
                Optional.of(new WalkEnded(ANN, new Pos(8, 64, 0), new Vec(8.5, 64, 0.5), "ARRIVED", 0.75, "IDLE")),
                "TELEPORT",
                NOW - 200,
                List.of(R1, "22222222-0000-0000-0000-000000000000", "33333333-0000-0000-0000-000000000000", R1),
                1_200,
                List.of());
    }

    private static HistoryEntry entry(long tick, String to) {
        return new HistoryEntry(tick, "JOB_STEP", "", to, ApiText.of("hycolony.debug.history.jobStep", "-", to));
    }

    private static Violation alert(String code) {
        return new Violation(code, ApiText.of("hycolony.debug.violation." + code), Optional.of(ANN), Optional.empty());
    }

    @Test
    void idleCitizenShowsItsStateAndPlaceholders() {
        assertEquals(
                List.of(
                        ApiText.of("hylens.hud.title", "Ann"),
                        ApiText.of("hylens.hud.job", "-"),
                        ApiText.of("hylens.hud.ai", "IDLE", "5"),
                        ApiText.of("hylens.hud.step", "-", "0"),
                        ApiText.of("hylens.hud.activity", "-"),
                        ApiText.of("hylens.hud.target", "-"),
                        ApiText.of("hylens.hud.walkNone"),
                        ApiText.of("hylens.hud.stuckNone"),
                        ApiText.of("hylens.hud.queue", "0", "-"),
                        ApiText.of("hylens.hud.leisure", "0"),
                        ApiText.of("hylens.hud.noAlerts")),
                WatchHudView.lines(NAMED, idle(), List.of(), Optional.empty()));
    }

    @Test
    void workingCitizenShowsWhatItDoesAndWhereItWalks() {
        List<ApiText> lines = WatchHudView.lines(NAMED, working(), List.of(), Optional.empty());

        assertEquals(ApiText.of("hylens.hud.ai", "WORK", "20"), lines.get(2));
        assertEquals(ApiText.of("hylens.hud.step", "PICKUP", "3"), lines.get(3));
        assertEquals(
                ApiText.of("hylens.hud.activity", ApiText.of("hycolony.activity.delivering", "Planks")), lines.get(4));
        assertEquals(ApiText.of("hylens.hud.target", "10 64 -3"), lines.get(5));
        assertEquals(ApiText.of("hylens.hud.walk", "8 64 0", "ARRIVED", "IDLE", "0.8"), lines.get(6));
        assertEquals(ApiText.of("hylens.hud.stuck", "TELEPORT", "10"), lines.get(7));
        assertEquals(ApiText.of("hylens.hud.queue", "4", "11111111, 22222222, 33333333, ..."), lines.get(8));
        assertEquals(ApiText.of("hylens.hud.leisure", "60"), lines.get(9));
    }

    @Test
    void unloadedBodyShowsNoAiState() {
        CitizenDebugSnapshot s = idle();
        CitizenDebugSnapshot unloaded = new CitizenDebugSnapshot(
                ANN,
                NOW,
                "",
                0,
                "",
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                0,
                List.of(),
                0,
                s.history());

        assertEquals(
                ApiText.of("hylens.hud.unloaded"),
                WatchHudView.lines(NAMED, unloaded, List.of(), Optional.empty()).get(2));
    }

    @Test
    void historyShowsTheLastFiveTransitionsNewestFirst() {
        List<HistoryEntry> history = IntStream.range(0, 7)
                .mapToObj(i -> entry(NOW - 20L * (7 - i), "S" + i))
                .toList();
        CitizenDebugSnapshot s = idle();
        CitizenDebugSnapshot withHistory = new CitizenDebugSnapshot(
                ANN,
                NOW,
                s.aiState(),
                s.aiStateSince(),
                "",
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "",
                0,
                List.of(),
                0,
                history);

        List<ApiText> lines = WatchHudView.lines(NAMED, withHistory, List.of(), Optional.empty());

        assertEquals(ApiText.of("hylens.hud.history"), lines.get(10));
        assertEquals(ApiText.of("hylens.hud.historyEntry", "1", history.get(6).detail()), lines.get(11));
        assertEquals(ApiText.of("hylens.hud.historyEntry", "5", history.get(2).detail()), lines.get(15));
        assertEquals(ApiText.of("hylens.hud.noAlerts"), lines.get(16));
        assertEquals(17, lines.size());
    }

    @Test
    void alertsAreCountedAndTheFirstThreeShown() {
        List<Violation> alerts = List.of(alert("a"), alert("b"), alert("c"), alert("d"));

        List<ApiText> lines = WatchHudView.lines(NAMED, idle(), alerts, Optional.empty());

        assertEquals(ApiText.of("hylens.hud.alerts", "4"), lines.get(10));
        assertEquals(ApiText.of("hylens.hud.alert", alerts.get(0).detail()), lines.get(11));
        assertEquals(ApiText.of("hylens.hud.alert", alerts.get(2).detail()), lines.get(13));
        assertEquals(14, lines.size());
    }

    @Test
    void neverMoreLinesThanTheHudHolds() {
        List<HistoryEntry> history =
                IntStream.range(0, 20).mapToObj(i -> entry(NOW, "S" + i)).toList();
        CitizenDebugSnapshot s = working();
        CitizenDebugSnapshot full = new CitizenDebugSnapshot(
                ANN,
                NOW,
                s.aiState(),
                s.aiStateSince(),
                s.jobStep(),
                s.jobStepSince(),
                s.activity(),
                s.walkTarget(),
                s.lastWalkEnd(),
                s.lastStuck(),
                s.lastStuckTick(),
                s.queue(),
                s.leisureTicks(),
                history);
        List<Violation> alerts = List.of(alert("a"), alert("b"), alert("c"), alert("d"));

        assertEquals(
                WatchHudView.MAX_LINES,
                WatchHudView.lines(NAMED, full, alerts, Optional.empty()).size());
    }

    @Test
    void jobIsShownUnderTheName() {
        CitizenSnapshot miner = new CitizenSnapshot(
                ANN, "Ann", Optional.of("hycolony:quarrier"), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(
                ApiText.of("hylens.hud.job", "hycolony:quarrier"),
                WatchHudView.lines(miner, idle(), List.of(), Optional.empty()).get(1));
    }

    @Test
    void targetShowsTheBlockThereAndBelowWhenLoaded() {
        Optional<TargetCell> cell = Optional.of(new TargetCell("Rock_Stone", ""));

        assertEquals(
                ApiText.of("hylens.hud.targetCell", "10 64 -3", "Rock_Stone", ApiText.of("hylens.hud.empty")),
                WatchHudView.lines(NAMED, working(), List.of(), cell).get(5));
    }

    @Test
    void noTargetIgnoresTheCell() {
        Optional<TargetCell> cell = Optional.of(new TargetCell("Rock_Stone", "Empty"));

        assertEquals(
                ApiText.of("hylens.hud.target", "-"),
                WatchHudView.lines(NAMED, idle(), List.of(), cell).get(5));
    }

    @Test
    void queueOfExactlyTheShownCountIsNotElided() {
        CitizenDebugSnapshot s = working();
        CitizenDebugSnapshot three = new CitizenDebugSnapshot(
                ANN,
                NOW,
                s.aiState(),
                s.aiStateSince(),
                s.jobStep(),
                s.jobStepSince(),
                s.activity(),
                s.walkTarget(),
                s.lastWalkEnd(),
                s.lastStuck(),
                s.lastStuckTick(),
                s.queue().subList(0, 3),
                s.leisureTicks(),
                s.history());

        assertEquals(
                ApiText.of("hylens.hud.queue", "3", "11111111, 22222222, 33333333"),
                WatchHudView.lines(NAMED, three, List.of(), Optional.empty()).get(8));
    }
}
