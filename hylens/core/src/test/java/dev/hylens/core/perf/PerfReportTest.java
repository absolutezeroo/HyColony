package dev.hylens.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.debug.PartTiming;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What /hylens perf tells: the world's tick, Hytale's heaviest systems, and HyColony's parts. */
class PerfReportTest {
    private static final WorldTick TICK = new WorldTick(7_280_000, 78_700_000, 30);

    @Test
    void worldTickThenHeaviestSystemsThenHyColonysParts() {
        List<ApiText> lines = PerfReport.lines(
                TICK,
                List.of(
                        new SystemTime("com.hypixel.hytale.server.npc.SteeringSystem", 1_150_000, 127_600_000),
                        new SystemTime(
                                "com.hypixel.hytale.server.npc.role.RoleSystems$BehaviourTickSystem",
                                1_560_000,
                                27_400_000)),
                Optional.of(List.of(new PartTiming("hycolony:farmer", 1200, 120_000_000, 9_500_000))));

        assertEquals(
                List.of(
                        ApiText.of("hylens.perf.world", "7.28", "78.7", "33.3"),
                        ApiText.of("hylens.perf.systems"),
                        ApiText.of("hylens.perf.system", "RoleSystems$BehaviourTickSystem", "1.560", "27.4"),
                        ApiText.of("hylens.perf.system", "SteeringSystem", "1.150", "127.6"),
                        ApiText.of("hylens.perf.colony"),
                        ApiText.of("hylens.perf.part", "hycolony:farmer", "2.000", "1200", "9.5")),
                lines);
    }

    @Test
    void onlyTheHeaviestFewAreTold() {
        List<SystemTime> many = IntStream.range(0, 30)
                .mapToObj(i -> new SystemTime("S" + i, i * 1000.0, i))
                .toList();

        List<ApiText> lines = PerfReport.lines(TICK, many, Optional.of(List.of()));

        assertEquals(1 + 1 + PerfReport.TOP + 1, lines.size(), "world, systems header, the top, HyColony idle");
        assertEquals(ApiText.of("hylens.perf.system", "S29", "0.029", "0.0"), lines.get(2));
    }

    @Test
    void onlyHyColonysHeaviestFewPartsAreTold() {
        List<PartTiming> many = IntStream.range(0, 30)
                .mapToObj(i -> new PartTiming("p" + i, 1, 30 - i, 30 - i))
                .toList();

        List<ApiText> lines = PerfReport.lines(TICK, List.of(), Optional.of(many));

        assertEquals(1 + 1 + 1 + PerfReport.TOP, lines.size(), "world, systems header, colony header, the top");
    }

    @Test
    void hyColonyIdleOrAbsentIsSaid() {
        assertEquals(
                ApiText.of("hylens.perf.colonyIdle"),
                PerfReport.lines(TICK, List.of(), Optional.of(List.of())).getLast());
        assertEquals(
                ApiText.of("hylens.notRunning"),
                PerfReport.lines(TICK, List.of(), Optional.empty()).getLast());
    }
}
