package dev.hycolony.core.colony.stats;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A colony's statistics: per stat id, a count per colony day (MC StatisticsManager). Deviation from MC: only the
 * stats HyColony records are counted (MC StatisticsConstants lists them all); the ids are MC's.
 */
public final class ColonyStatistics {
    /** MC StatisticsConstants.DEATH: a citizen died. */
    public static final String DEATH = "death";

    private final Map<String, Map<Integer, Integer>> stats = new LinkedHashMap<>();

    /** MC increment: one more {@code id} on {@code day}. */
    public void increment(String id, int day) {
        incrementBy(id, 1, day);
    }

    /** MC incrementBy: {@code qty} more {@code id} on {@code day}. */
    public void incrementBy(String id, int qty, int day) {
        stats.computeIfAbsent(id, _ -> new TreeMap<>()).merge(day, qty, Integer::sum);
    }

    /** MC getStatTotal: every count of {@code id}; 0 for an unknown id. */
    public int total(String id) {
        int total = 0;
        for (int count : perDay(id).values()) {
            total += count;
        }
        return total;
    }

    /** MC getStatsInPeriod: the counts of {@code id} from {@code startDay} to {@code endDay}, both included. */
    public int inPeriod(String id, int startDay, int endDay) {
        int count = 0;
        for (Map.Entry<Integer, Integer> e : perDay(id).entrySet()) {
            if (e.getKey() >= startDay && e.getKey() <= endDay) {
                count += e.getValue();
            }
        }
        return count;
    }

    /** MC getStatTypes: the ids counted, in the order they were first counted. */
    public Set<String> types() {
        return Collections.unmodifiableSet(stats.keySet());
    }

    /** The counts of {@code id} by day, oldest first; empty for an unknown id. */
    public Map<Integer, Integer> perDay(String id) {
        return Collections.unmodifiableMap(stats.getOrDefault(id, Map.of()));
    }

    /** The save (MC writeToNBT): {@code {"death": {"3": 2}}}. */
    public JsonObject write() {
        JsonObject o = new JsonObject();
        stats.forEach((id, days) -> {
            JsonObject counts = new JsonObject();
            days.forEach((day, count) -> counts.addProperty(String.valueOf(day), count));
            o.add(id, counts);
        });
        return o;
    }

    /**
     * Adds the counts of a save (MC readFromNBT), tolerantly (CLAUDE.md § 5): a stat that is no object, a count that is
     * no number or a day that is no integer is skipped.
     */
    public void load(JsonObject saved) {
        for (Map.Entry<String, JsonElement> stat : saved.entrySet()) {
            if (stat.getValue() instanceof JsonObject days) {
                for (Map.Entry<String, JsonElement> day : days.entrySet()) {
                    loadDay(stat.getKey(), day.getKey(), day.getValue());
                }
            }
        }
    }

    private void loadDay(String id, String day, JsonElement count) {
        if (!(count instanceof JsonPrimitive p) || !p.isNumber()) {
            return;
        }
        try {
            incrementBy(id, p.getAsInt(), Integer.parseInt(day));
        } catch (NumberFormatException _) {
            // Not a day: skipped.
        }
    }
}
