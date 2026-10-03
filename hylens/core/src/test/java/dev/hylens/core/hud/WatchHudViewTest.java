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
import dev.hycolony.api.read.CitizenWellbeing;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What the HUD shows of a watched citizen (spec 2026-09-30, § 6.2), in sections of labelled fields. */
class WatchHudViewTest {
    private static final CitizenRef ANN = new CitizenRef(new ColonyRef("default", 1), 4);
    private static final long NOW = 2_000;
    /** A request id as the api gives it: a whole UUID. */
    private static final String R1 = "11111111-aaaa-bbbb-cccc-dddddddddddd";

    private static final ApiText NONE = ApiText.of("hylens.hud.none");
    private static final CitizenSnapshot NAMED =
            new CitizenSnapshot(ANN, "Ann", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    private static CitizenDebugSnapshot idle() {
        return idleWith(0, List.of());
    }

    private static CitizenDebugSnapshot idleWith(int leisureTicks, List<HistoryEntry> history) {
        return new CitizenDebugSnapshot(
                ANN,
                NOW,
                "IDLE",
                NOW - 100,
                "",
                0,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "",
                0,
                List.of(),
                leisureTicks,
                history);
    }

    private static CitizenDebugSnapshot working() {
        return workingWith(
                List.of(R1, "22222222-0000-0000-0000-000000000000", "33333333-0000-0000-0000-000000000000", R1),
                List.of());
    }

    private static CitizenDebugSnapshot workingWith(List<String> queue, List<HistoryEntry> history) {
        return new CitizenDebugSnapshot(
                ANN,
                NOW,
                "WORK",
                NOW - 400,
                "PICKUP",
                NOW - 60,
                Optional.of(ApiText.of("hycolony.activity.delivering", "Planks")),
                Optional.of(new Pos(10, 64, -3)),
                List.of(),
                Optional.of(new WalkEnded(ANN, new Pos(8, 64, 0), new Vec(8.5, 64, 0.5), "ARRIVED", 0.75, "IDLE")),
                "TELEPORT",
                NOW - 200,
                queue,
                1_200,
                history);
    }

    private static HistoryEntry entry(long tick, String to) {
        return new HistoryEntry(tick, "JOB_STEP", "", to, ApiText.of("hycolony.debug.history.jobStep", "-", to));
    }

    private static Violation alert(String code) {
        return new Violation(code, ApiText.of("hycolony.debug.violation." + code), Optional.of(ANN), Optional.empty());
    }

    private static HudLine section(String key, Object... params) {
        return HudLine.section(ApiText.of(key, params));
    }

    private static HudLine field(String labelKey, ApiText value) {
        return HudLine.field(ApiText.of(labelKey), value);
    }

    private static List<HudLine> lines(CitizenDebugSnapshot s) {
        return WatchHudView.lines(NAMED, s, Optional.empty(), List.of(), Optional.empty());
    }

    @Test
    void idleCitizenShowsItsSectionsWithPlaceholders() {
        assertEquals(
                List.of(
                        HudLine.title(ApiText.of("hylens.hud.title", "Ann", ApiText.of("hycolony.ui.job.none"))),
                        section("hylens.hud.section.state"),
                        field("hylens.hud.label.ai", ApiText.of("hylens.hud.since", "IDLE", "5")),
                        field("hylens.hud.label.step", NONE),
                        field("hylens.hud.label.activity", NONE),
                        section("hylens.hud.section.walk"),
                        field("hylens.hud.label.target", NONE),
                        field("hylens.hud.label.walk", NONE),
                        field("hylens.hud.label.stuck", NONE),
                        section("hylens.hud.section.needs"),
                        HudLine.field(ApiText.of("hylens.hud.label.queue", "0"), NONE),
                        field("hylens.hud.label.leisure", ApiText.of("hylens.hud.seconds", "0")),
                        field("hylens.hud.label.saturation", NONE),
                        section("hylens.hud.section.alerts", "0")),
                lines(idle()));
    }

    @Test
    void itsSaturationAndHappinessShowWithOneDecimal() {
        CitizenWellbeing fed = new CitizenWellbeing(ANN, 12.25, 60, 7.0, List.of());
        assertEquals(
                field("hylens.hud.label.saturation", ApiText.of("hylens.hud.wellbeing", "12.3", "60", "7.0")),
                WatchHudView.lines(NAMED, idle(), Optional.of(fed), List.of(), Optional.empty())
                        .get(12));
    }

    @Test
    void workingCitizenShowsWhatItDoesAndWhereItWalks() {
        List<HudLine> lines = lines(working());

        assertEquals(field("hylens.hud.label.ai", ApiText.of("hylens.hud.since", "WORK", "20")), lines.get(2));
        assertEquals(field("hylens.hud.label.step", ApiText.of("hylens.hud.since", "PICKUP", "3")), lines.get(3));
        assertEquals(
                field("hylens.hud.label.activity", ApiText.of("hycolony.activity.delivering", "Planks")), lines.get(4));
        assertEquals(field("hylens.hud.label.target", ApiText.of("hylens.hud.raw", "10 64 -3")), lines.get(6));
        assertEquals(
                field("hylens.hud.label.walk", ApiText.of("hylens.hud.walk", "8 64 0", "ARRIVED", "IDLE", "0.8")),
                lines.get(7));
        assertEquals(field("hylens.hud.label.stuck", ApiText.of("hylens.hud.stuck", "TELEPORT", "10")), lines.get(8));
        assertEquals(
                HudLine.field(
                        ApiText.of("hylens.hud.label.queue", "4"),
                        ApiText.of("hylens.hud.raw", "11111111, 22222222, 33333333, ...")),
                lines.get(10));
        assertEquals(field("hylens.hud.label.leisure", ApiText.of("hylens.hud.seconds", "60")), lines.get(11));
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
                List.of(),
                Optional.empty(),
                "",
                0,
                List.of(),
                0,
                s.history());

        assertEquals(
                field("hylens.hud.label.ai", ApiText.of("hylens.hud.unloaded")),
                lines(unloaded).get(2));
    }

    @Test
    void historyShorterThanFiveShowsItAllNewestFirst() {
        List<HistoryEntry> history = List.of(entry(NOW - 40, "S0"), entry(NOW - 20, "S1"));

        List<HudLine> lines = lines(idleWith(0, history));

        assertEquals(section("hylens.hud.section.history"), lines.get(13));
        assertEquals(
                HudLine.field(ApiText.of("hylens.hud.ago", "1"), history.get(1).detail()), lines.get(14));
        assertEquals(
                HudLine.field(ApiText.of("hylens.hud.ago", "2"), history.get(0).detail()), lines.get(15));
        assertEquals(17, lines.size());
    }

    @Test
    void leisureOutsideABreakNeverShowsBelowZero() {
        assertEquals(
                field("hylens.hud.label.leisure", ApiText.of("hylens.hud.seconds", "0")),
                lines(idleWith(-40, List.of())).get(11));
    }

    @Test
    void historyShowsTheLastFiveTransitionsNewestFirst() {
        List<HistoryEntry> history = IntStream.range(0, 7)
                .mapToObj(i -> entry(NOW - 20L * (7 - i), "S" + i))
                .toList();

        List<HudLine> lines = lines(idleWith(0, history));

        assertEquals(section("hylens.hud.section.history"), lines.get(13));
        assertEquals(
                HudLine.field(ApiText.of("hylens.hud.ago", "1"), history.get(6).detail()), lines.get(14));
        assertEquals(
                HudLine.field(ApiText.of("hylens.hud.ago", "5"), history.get(2).detail()), lines.get(18));
        assertEquals(section("hylens.hud.section.alerts", "0"), lines.get(19));
        assertEquals(20, lines.size());
    }

    @Test
    void alertsAreCountedAndTheFirstThreeShown() {
        List<Violation> alerts = List.of(alert("a"), alert("b"), alert("c"), alert("d"));

        List<HudLine> lines = WatchHudView.lines(NAMED, idle(), Optional.empty(), alerts, Optional.empty());

        assertEquals(section("hylens.hud.section.alerts", "4"), lines.get(13));
        assertEquals(HudLine.alert(ApiText.of("hylens.hud.alert", alerts.get(0).detail())), lines.get(14));
        assertEquals(HudLine.alert(ApiText.of("hylens.hud.alert", alerts.get(2).detail())), lines.get(16));
        assertEquals(17, lines.size());
    }

    @Test
    void neverMoreLinesThanTheHudHolds() {
        List<HistoryEntry> history =
                IntStream.range(0, 20).mapToObj(i -> entry(NOW, "S" + i)).toList();
        List<Violation> alerts = List.of(alert("a"), alert("b"), alert("c"), alert("d"));

        assertEquals(
                WatchHudView.MAX_LINES,
                WatchHudView.lines(NAMED, workingWith(List.of(R1), history), Optional.empty(), alerts, Optional.empty())
                        .size());
    }

    @Test
    void jobIsShownBesideTheName() {
        CitizenSnapshot builder = new CitizenSnapshot(
                ANN, "Ann", Optional.of("hycolony:builder"), Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(
                HudLine.title(ApiText.of("hylens.hud.title", "Ann", ApiText.of("hycolony.ui.job.builder"))),
                WatchHudView.lines(builder, idle(), Optional.empty(), List.of(), Optional.empty())
                        .get(0));
    }

    @Test
    void targetShowsTheBlockThereAndBelowWhenLoaded() {
        Optional<TargetCell> cell = Optional.of(new TargetCell("Rock_Stone", ""));

        assertEquals(
                field(
                        "hylens.hud.label.target",
                        ApiText.of("hylens.hud.cell", "10 64 -3", "Rock_Stone", ApiText.of("hylens.hud.empty"))),
                WatchHudView.lines(NAMED, working(), Optional.empty(), List.of(), cell)
                        .get(6));
    }

    @Test
    void noTargetIgnoresTheCell() {
        Optional<TargetCell> cell = Optional.of(new TargetCell("Rock_Stone", "Empty"));

        assertEquals(
                field("hylens.hud.label.target", NONE),
                WatchHudView.lines(NAMED, idle(), Optional.empty(), List.of(), cell)
                        .get(6));
    }

    @Test
    void queueOfExactlyTheShownCountIsNotElided() {
        CitizenDebugSnapshot three = workingWith(working().queue().subList(0, 3), List.of());

        assertEquals(
                HudLine.field(
                        ApiText.of("hylens.hud.label.queue", "3"),
                        ApiText.of("hylens.hud.raw", "11111111, 22222222, 33333333")),
                lines(three).get(10));
    }
}
