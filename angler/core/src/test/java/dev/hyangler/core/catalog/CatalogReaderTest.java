package dev.hyangler.core.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.RodStats;
import dev.hyangler.core.catalog.RawFile.Kind;
import dev.hyangler.core.condition.ConditionRegistry;
import dev.hyangler.core.testing.Contexts;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogReaderTest {
    private final ConditionRegistry registry = new ConditionRegistry();

    private Catalog read(RawFile... files) {
        return CatalogReader.read(List.of(files), registry, Set.of("Fish_Trout_Rainbow_Item"), 32);
    }

    @Test
    void aFishFileGivesItsWeightConditionsAndModifiers() {
        Catalog c = read(new RawFile(Kind.FISH, "Fish_Trout_Rainbow_Item", """
                {"Weight":25,"Conditions":{"All":[{"Type":"Time","From":5,"To":22}]},
                 "Modifiers":[{"If":{"Type":"Weather","Rain":true},"Multiplier":1.5}]}"""));
        Entry trout = c.fish().getFirst();
        assertEquals(25, trout.weight());
        assertEquals(0, trout.quality());
        assertTrue(trout.rarities());
        assertTrue(trout.condition().test(Contexts.hour(12)));
        assertFalse(trout.condition().test(Contexts.hour(23)));
        assertEquals(1.5, trout.modifiers().getFirst().multiplier());
    }

    @Test
    void raritiesDefaultToWhetherTheItemHasRarityStates() {
        Catalog c = read(new RawFile(Kind.FISH, "Fish_Pike_Item", "{\"Weight\":12}"));
        assertFalse(c.fish().getFirst().rarities());
    }

    @Test
    void raritiesCannotBeTurnedOnForAnItemWithoutRarityStates() {
        Catalog c = read(
                new RawFile(Kind.FISH, "Fish_Pike_Item", "{\"Weight\":12,\"Rarities\":true}"),
                new RawFile(Kind.FISH, "Fish_Trout_Rainbow_Item", "{\"Weight\":12,\"Rarities\":false}"));
        assertFalse(c.fish().get(0).rarities());
        assertFalse(c.fish().get(1).rarities());
    }

    @Test
    void anInfiniteMultiplierIsRejected() {
        Catalog c = read(new RawFile(
                Kind.FISH,
                "Fish_Pike_Item",
                "{\"Weight\":12,\"Modifiers\":[{\"If\":{\"Type\":\"OpenWater\"},\"Multiplier\":\"Infinity\"}]}"));
        assertTrue(c.fish().isEmpty());
        assertEquals(1, c.rejections().size());
    }

    @Test
    void junkAndTreasureAreSortedByTheirCategory() {
        Catalog c = read(
                new RawFile(Kind.CATCH, "Rubble_Stone", "{\"Category\":\"Junk\",\"Weight\":10,\"Count\":[1,3]}"),
                new RawFile(Kind.CATCH, "Deco_Treasure", "{\"Category\":\"Treasure\",\"Weight\":2}"));
        assertEquals(CatchCategory.JUNK, c.junk().getFirst().category());
        assertEquals(3, c.junk().getFirst().maxCount());
        assertEquals(1, c.treasure().getFirst().minCount());
    }

    @Test
    void aRodFileGivesItsStatsAndTheDefaultLine() {
        Catalog c = read(new RawFile(Kind.ROD, "HyAngler_Rod_Iron", "{\"Tier\":2,\"Lure\":1,\"Luck\":1}"));
        assertEquals(new RodStats(2, 1, 1, 32), c.rods().get("HyAngler_Rod_Iron"));
    }

    @Test
    void anUnknownKeyIsIgnored() {
        Catalog c = read(new RawFile(Kind.FISH, "Fish_Pike_Item", "{\"Weight\":12,\"SizeCm\":[40,90]}"));
        assertEquals(1, c.fish().size());
        assertTrue(c.rejections().isEmpty());
    }

    @Test
    void anInvalidFileIsRejectedWithItsReasonAndTheOthersStay() {
        Catalog c = read(
                new RawFile(Kind.FISH, "Fish_Pike_Item", "{\"Weight\":0}"),
                new RawFile(Kind.FISH, "Fish_Salmon_Item", "{\"Weight\":20,\"Conditions\":{\"Type\":\"Season\"}}"),
                new RawFile(Kind.CATCH, "Rubble_Stone", "{\"Category\":\"Trash\",\"Weight\":10}"),
                new RawFile(Kind.FISH, "Fish_Minnow_Item", "not json"),
                new RawFile(Kind.FISH, "Fish_Bluegill_Item", "{\"Weight\":40}"));
        assertEquals(
                List.of("Fish_Bluegill_Item"), c.fish().stream().map(Entry::id).toList());
        assertEquals(4, c.rejections().size());
        assertTrue(c.rejections().get(1).reason().contains("Season"));
    }

    @Test
    void aCountWhoseMinIsOverItsMaxIsRejected() {
        Catalog c =
                read(new RawFile(Kind.CATCH, "Rubble_Stone", "{\"Category\":\"Junk\",\"Weight\":1,\"Count\":[3,1]}"));
        assertTrue(c.junk().isEmpty());
        assertEquals(1, c.rejections().size());
    }
}
