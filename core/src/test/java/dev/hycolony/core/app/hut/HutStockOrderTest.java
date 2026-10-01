package dev.hycolony.core.app.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** MC WindowHutAllInventory.updateResources: the filter, its Levenshtein order, then the five sorts. */
class HutStockOrderTest {
    private static final HutStock OAK = stock("hytale:oak_log", 5);
    private static final HutStock STONE = stock("hytale:stone", 40);
    private static final HutStock SAND = stock("hytale:sand", 12);
    private static final Map<HutStock, String> NAMES = Map.of(OAK, "Oak Log", STONE, "Stone", SAND, "Sand");
    private static final Function<HutStock, String> NAME = NAMES::get;

    private static HutStock stock(String id, int count) {
        return new HutStock(new ItemKey(id), count, List.of());
    }

    @Test
    void theFilterKeepsNamesOrIdsContainingItIgnoringCase() {
        assertEquals(
                List.of(STONE), HutStockOrder.sorted(List.of(OAK, STONE, SAND), NAME, "STON", HutStockOrder.Sort.NONE));
        assertEquals(
                List.of(OAK), HutStockOrder.sorted(List.of(OAK, STONE, SAND), NAME, "oak_", HutStockOrder.Sort.NONE));
    }

    @Test
    void withoutSortTheClosestNamesToTheFilterComeFirst() {
        assertEquals(
                List.of(SAND, STONE, OAK),
                HutStockOrder.sorted(List.of(OAK, STONE, SAND), NAME, "", HutStockOrder.Sort.NONE),
                "an empty filter orders by name length, MC's distance to \"\"");
        assertEquals(
                List.of(SAND, STONE),
                HutStockOrder.sorted(List.of(OAK, STONE, SAND), NAME, "s", HutStockOrder.Sort.NONE));
    }

    @Test
    void theFiveSortsAreMcs() {
        List<HutStock> all = List.of(OAK, STONE, SAND);
        assertEquals(List.of(OAK, SAND, STONE), HutStockOrder.sorted(all, NAME, "", HutStockOrder.Sort.NAME_ASC));
        assertEquals(List.of(STONE, SAND, OAK), HutStockOrder.sorted(all, NAME, "", HutStockOrder.Sort.NAME_DESC));
        assertEquals(List.of(OAK, SAND, STONE), HutStockOrder.sorted(all, NAME, "", HutStockOrder.Sort.COUNT_ASC));
        assertEquals(List.of(STONE, SAND, OAK), HutStockOrder.sorted(all, NAME, "", HutStockOrder.Sort.COUNT_DESC));
    }

    @Test
    void tiesOfTheChosenSortKeepTheClosestNameFirstAsMc() {
        HutStock oak = stock("hytale:oak_log", 7);
        HutStock sand = stock("hytale:sand", 7);
        Map<HutStock, String> names = Map.of(oak, "Oak Log", sand, "Sand");
        assertEquals(
                List.of(sand, oak),
                HutStockOrder.sorted(List.of(oak, sand), names::get, "", HutStockOrder.Sort.COUNT_ASC),
                "same count: MC's first, stable sort by distance decides");
    }

    @Test
    void theSortButtonCyclesThroughMcsLabels() {
        HutStockOrder.Sort s = HutStockOrder.Sort.NONE;
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            labels.append(s.label()).append(' ');
            s = s.next();
        }
        assertEquals("v^ A^ Av 1^ 1v ", labels.toString());
        assertEquals(HutStockOrder.Sort.NONE, s);
    }

    @Test
    void levenshteinIsTheEditDistance() {
        assertEquals(3, HutStockOrder.levenshtein("kitten", "sitting"));
        assertEquals(5, HutStockOrder.levenshtein("Stone", ""));
    }
}
