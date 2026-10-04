package dev.hyangler.core.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hyangler.api.CatchCategory;
import dev.hyangler.api.Tackle;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.core.catalog.Entry;
import dev.hyangler.core.catalog.Modifier;
import dev.hyangler.core.testing.Contexts;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightsTest {
    private static Entry entry(int weight, int quality, Condition c, List<Modifier> mods) {
        return new Entry("X", CatchCategory.FISH, weight, quality, 1, 1, false, c, mods);
    }

    @Test
    void luckRaisesAPositiveQualityAndLowersANegativeOne() {
        var lucky = Contexts.tackle(Tackle.of(0, 3, 32));
        assertEquals(16, Weights.effective(entry(10, 2, ctx -> true, List.of()), lucky));
        assertEquals(1, Weights.effective(entry(10, -3, ctx -> true, List.of()), lucky));
    }

    @Test
    void aWeightNeverGoesBelowZero() {
        var lucky = Contexts.tackle(Tackle.of(0, 3, 32));
        assertEquals(0, Weights.effective(entry(2, -2, ctx -> true, List.of()), lucky));
    }

    @Test
    void aFalseConditionGivesZero() {
        assertEquals(0, Weights.effective(entry(30, 0, ctx -> false, List.of()), Contexts.base()));
    }

    @Test
    void onlyTheModifiersThatHoldMultiply() {
        List<Modifier> mods = List.of(new Modifier(ctx -> true, 1.5), new Modifier(ctx -> false, 10));
        assertEquals(37, Weights.effective(entry(25, 0, ctx -> true, mods), Contexts.base()));
    }

    @Test
    void theCategoriesAreVanillas() {
        var noLuck = Contexts.base();
        assertEquals(85, Weights.category(CatchCategory.FISH, noLuck));
        assertEquals(10, Weights.category(CatchCategory.JUNK, noLuck));
        assertEquals(5, Weights.category(CatchCategory.TREASURE, noLuck));
        var lucky = Contexts.tackle(Tackle.of(0, 3, 32));
        assertEquals(82, Weights.category(CatchCategory.FISH, lucky));
        assertEquals(4, Weights.category(CatchCategory.JUNK, lucky));
        assertEquals(11, Weights.category(CatchCategory.TREASURE, lucky));
    }

    @Test
    void noTreasureOutsideOpenWater() {
        assertEquals(0, Weights.category(CatchCategory.TREASURE, Contexts.water(3, false, true)));
    }
}
