package dev.hyangler.core.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.CatchChance;
import dev.hyangler.api.Rarity;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.core.catalog.Catalog;
import dev.hyangler.core.catalog.Entry;
import dev.hyangler.core.testing.Contexts;
import dev.hyangler.core.testing.ScriptedRandom;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatchRollerTest {
    private static final Entry CHEST =
            new Entry("Deco_Treasure", CatchCategory.TREASURE, 1, 0, 1, 1, false, ctx -> true, List.of());

    private final Catalog catalog = new Catalog(
            List.of(
                    fish("Fish_Bluegill_Item", 30, true, ctx -> true),
                    fish("Fish_Catfish_Item", 10, false, ctx -> ctx.hour() >= 19 || ctx.hour() < 6)),
            List.of(junk("Rubble_Stone", 10, 1, 3)),
            List.of(CHEST),
            Map.of(),
            List.of());

    private static CatchRoller roller(Catalog c) {
        return new CatchRoller(c, e -> {
            throw new AssertionError(e);
        });
    }

    @Test
    void hugeWeightsStillBite() {
        Catalog huge = new Catalog(
                List.of(
                        fish("Fish_A", 2_000_000_000, false, ctx -> true),
                        fish("Fish_B", 2_000_000_000, false, ctx -> true)),
                List.of(),
                List.of(),
                Map.of(),
                List.of());
        assertEquals(
                "Fish_A",
                roller(huge)
                        .roll(Contexts.base(), new ScriptedRandom(0, 5))
                        .orElseThrow()
                        .itemId());
        List<CatchChance> chances = roller(huge).chances(Contexts.base());
        assertEquals(0.5, chances.get(0).probability(), 1e-12);
        assertEquals(0.5, chances.get(1).probability(), 1e-12);
    }

    private static Entry fish(String id, int weight, boolean rarities, Condition c) {
        return new Entry(id, CatchCategory.FISH, weight, 0, 1, 1, rarities, c, List.of());
    }

    private static Entry junk(String id, int weight, int min, int max) {
        return new Entry(id, CatchCategory.JUNK, weight, 0, min, max, false, ctx -> true, List.of());
    }

    @Test
    void chancesSumToOneAndFollowTheCategories() {
        List<CatchChance> noon = roller(catalog).chances(Contexts.base());
        assertEquals(1.0, noon.stream().mapToDouble(CatchChance::probability).sum(), 1e-9);
        // fish 85, junk 10, treasure 5 of 100; at noon only the bluegill is a fish
        assertEquals("Fish_Bluegill_Item", noon.getFirst().itemId());
        assertEquals(0.85, noon.getFirst().probability(), 1e-9);
    }

    @Test
    void catfishOnlyBitesAtNight() {
        List<CatchChance> night = roller(catalog).chances(Contexts.hour(23));
        double catfish = night.stream()
                .filter(c -> c.itemId().equals("Fish_Catfish_Item"))
                .mapToDouble(CatchChance::probability)
                .sum();
        assertEquals(0.85 * 10 / 40, catfish, 1e-9);
    }

    @Test
    void anEmptyCategoryGivesItsShareToTheOthers() {
        Catalog noJunk = new Catalog(catalog.fish(), List.of(), List.of(), Map.of(), List.of());
        List<CatchChance> c = roller(noJunk).chances(Contexts.base());
        assertEquals(1.0, c.getFirst().probability(), 1e-9);
    }

    @Test
    void nothingBitesWhereNothingLives() {
        Catalog nightOnly = new Catalog(List.of(catalog.fish().get(1)), List.of(), List.of(), Map.of(), List.of());
        assertTrue(roller(nightOnly).chances(Contexts.base()).isEmpty());
        assertTrue(roller(nightOnly).roll(Contexts.base(), new ScriptedRandom()).isEmpty());
    }

    @Test
    void aRolledFishCarriesItsRarity() {
        // category: 0 of 100 -> fish; species: 0 of 30 -> bluegill; rarity: 150 of 166 -> rare
        var c = roller(catalog)
                .roll(Contexts.base(), new ScriptedRandom(0, 0, 150))
                .orElseThrow();
        assertEquals("Fish_Bluegill_Item", c.itemId());
        assertEquals(Rarity.RARE, c.rarity().orElseThrow());
    }

    @Test
    void junkComesInItsCount() {
        // category: 90 of 100 -> junk; entry: 0 of 10; count: 2 in [1, 4)
        var c = roller(catalog)
                .roll(Contexts.base(), new ScriptedRandom(90, 0, 2))
                .orElseThrow();
        assertEquals("Rubble_Stone", c.itemId());
        assertEquals(2, c.count());
        assertTrue(c.rarity().isEmpty());
    }
}
