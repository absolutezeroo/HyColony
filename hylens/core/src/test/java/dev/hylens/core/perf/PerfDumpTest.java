package dev.hylens.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.debug.PartTiming;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The file /hylens perf --dump writes: every system and every part, as Hytale's /server dump lists them. */
class PerfDumpTest {
    private static final long S = 1_000_000_000L;

    private static List<Period> periods(double avgMs, long maxMs) {
        return List.of(
                new Period(S, avgMs * 1e6, maxMs * 1_000_000),
                new Period(60 * S, avgMs * 1e6, maxMs * 1_000_000),
                new Period(300 * S, avgMs * 1e6, maxMs * 1_000_000));
    }

    private static final PerfSnapshot SNAPSHOT = new PerfSnapshot(
            "default",
            "2026-10-01T03:56:16Z",
            30,
            List.of(new Period(10 * S, 7.28e6, 78_700_000), new Period(60 * S, 7.28e6, 78_700_000)),
            List.of(
                    new SystemMetrics("com.hypixel.SteeringSystem", "EntityStore", 209, periods(1.15, 127)),
                    new SystemMetrics(
                            "com.hypixel.RoleSystems$BehaviourTickSystem", "EntityStore", 0, periods(1.56, 27))),
            Optional.of(List.of(new PartTiming("autosave", 2, 21_000_000, 15_000_000))));

    @Test
    void headerAndWorldTickPerPeriod() {
        List<String> lines = PerfDump.lines(SNAPSHOT);

        assertEquals("HyLens perf dump, world default, 2026-10-01T03:56:16Z", lines.getFirst());
        assertTrue(lines.contains("World tick (30 ticks/s, budget 33.33 ms):"), lines.toString());
        assertTrue(lines.contains("  10 s: avg 7.280 ms, max 78.70 ms"), lines.toString());
        assertTrue(lines.contains("  1 min: avg 7.280 ms, max 78.70 ms"), lines.toString());
    }

    @Test
    void everySystemHeaviestFirstWithItsPeriods() {
        List<String> lines = PerfDump.lines(SNAPSHOT);
        int behaviour = indexOf(lines, "RoleSystems$BehaviourTickSystem");
        int steering = indexOf(lines, "SteeringSystem");

        assertTrue(behaviour >= 0 && behaviour < steering, lines.toString());
        assertTrue(lines.get(behaviour).contains("1.560"), lines.get(behaviour));
        assertTrue(lines.get(steering).contains("127.00"), lines.get(steering));
        assertTrue(lines.get(steering).contains("209"), lines.get(steering));
        assertTrue(lines.get(steering).contains("EntityStore"), lines.get(steering));
    }

    @Test
    void heaviestIsOverAMinuteNotOverOtherPeriods() {
        SystemMetrics minuteHeavy = new SystemMetrics(
                "MinuteHeavy",
                "EntityStore",
                0,
                List.of(new Period(S, 0, 0), new Period(60 * S, 2e6, 0), new Period(300 * S, 1e6, 0)));
        SystemMetrics fiveMinutesHeavy = new SystemMetrics(
                "FiveMinutesHeavy",
                "EntityStore",
                0,
                List.of(new Period(S, 9e6, 0), new Period(60 * S, 1e6, 0), new Period(300 * S, 9e6, 0)));
        List<String> lines = PerfDump.lines(
                new PerfSnapshot("w", "t", 30, List.of(), List.of(fiveMinutesHeavy, minuteHeavy), Optional.empty()));

        assertTrue(indexOf(lines, "MinuteHeavy") < indexOf(lines, "FiveMinutesHeavy"), lines.toString());
    }

    @Test
    void hyColonysPartsWithTheirCallsTotalAverageAndWorst() {
        String autosave = PerfDump.lines(SNAPSHOT).get(indexOf(PerfDump.lines(SNAPSHOT), "autosave"));

        assertEquals("  0.350 ms/s  2 calls  total 21.00 ms  avg 10.50 ms  max 15.00 ms  autosave", autosave);
    }

    @Test
    void noHyColonyIsSaid() {
        PerfSnapshot none = new PerfSnapshot("w", "t", 30, List.of(), List.of(), Optional.empty());

        assertTrue(PerfDump.lines(none).contains("HyColony does not run in this world."));
    }

    private static int indexOf(List<String> lines, String part) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).endsWith(part)) {
                return i;
            }
        }
        return -1;
    }
}
