package dev.hycolony.core.app.select;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Structurize WindowSelectRes.updateResources: the filter, the player's items first, then by name or closeness. */
class SelectResOrderTest {
    private static final Map<String, String> NAMES = Map.of(
            "wheat_seeds", "Wheat Seeds Bag", "carrot", "Carrot", "potato", "Potato", "melon_seeds", "Melon Seeds");
    private static final List<String> IDS = List.of("wheat_seeds", "carrot", "potato", "melon_seeds");

    private static List<String> sorted(Set<String> held, String filter) {
        return SelectResOrder.sorted(IDS, NAMES::get, held, filter);
    }

    @Test
    void withoutAFilterTheHeldItemsComeFirstThenByName() {
        assertEquals(List.of("potato", "carrot", "melon_seeds", "wheat_seeds"), sorted(Set.of("potato"), ""));
    }

    @Test
    void theFilterKeepsNamesOrIdsContainingItCaseInsensitively() {
        assertEquals(Set.of("melon_seeds", "wheat_seeds"), new HashSet<>(sorted(Set.of(), "SEEDS")));
        assertEquals(List.of("potato"), sorted(Set.of(), "pota"));
        assertEquals(List.of("melon_seeds"), sorted(Set.of(), "melon_"), "the id counts too");
    }

    @Test
    void withAFilterTheClosestNamesComeFirstAfterTheHeldOnes() {
        assertEquals(List.of("melon_seeds", "wheat_seeds"), sorted(Set.of(), "Seeds"), "6 edits against 10");
        assertEquals(List.of("wheat_seeds", "melon_seeds"), sorted(Set.of("wheat_seeds"), "Seeds"));
    }

    /** Alphabetical order would put "Melon Seeds" before "Seeds"; the distance (6 against 0) puts it after. */
    @Test
    void withAFilterTheDistanceWinsOverTheName() {
        Map<String, String> names =
                Map.of("melon_seeds", "Melon Seeds", "seeds", "Seeds", "wheat_seeds", "Wheat Seeds Bag");
        List<String> ids = List.of("melon_seeds", "seeds", "wheat_seeds");

        assertEquals(
                List.of("seeds", "melon_seeds", "wheat_seeds"),
                SelectResOrder.sorted(ids, names::get, Set.of(), "Seeds"));
    }
}
