package dev.hycolony.core.colony.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MC StatisticsManager: counts per stat and day. */
class ColonyStatisticsTest {
    private final ColonyStatistics stats = new ColonyStatistics();

    @Test
    void countsAddUpPerDayAndInTotal() {
        stats.increment(ColonyStatistics.DEATH, 3);
        stats.increment(ColonyStatistics.DEATH, 3);
        stats.incrementBy(ColonyStatistics.DEATH, 4, 10);

        assertEquals(6, stats.total(ColonyStatistics.DEATH));
        assertEquals(Map.of(3, 2, 10, 4), stats.perDay(ColonyStatistics.DEATH));
        assertEquals(0, stats.total("unknown"));
    }

    @Test
    void aPeriodCountsItsFirstAndLastDays() {
        stats.increment(ColonyStatistics.DEATH, 2);
        stats.increment(ColonyStatistics.DEATH, 5);
        stats.increment(ColonyStatistics.DEATH, 6);

        assertEquals(2, stats.inPeriod(ColonyStatistics.DEATH, 5, 6), "MC getStatsInPeriod: startDay..endDay");
        assertEquals(3, stats.inPeriod(ColonyStatistics.DEATH, 2, 6));
    }

    @Test
    void typesKeepTheirFirstCountOrder() {
        stats.increment("b", 1);
        stats.increment("a", 1);
        assertEquals(List.of("b", "a"), List.copyOf(stats.types()));
    }
}
