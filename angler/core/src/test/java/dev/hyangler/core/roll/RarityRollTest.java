package dev.hyangler.core.roll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.api.Rarity;
import dev.hyangler.core.testing.ScriptedRandom;
import org.junit.jupiter.api.Test;

class RarityRollTest {

    @Test
    void withoutLuckTheWeightsAreTheBaitedTraps() {
        // Common 100, Uncommon 50, Rare 10, Epic 5, Legendary 1: total 166
        assertEquals(Rarity.COMMON, RarityRoll.roll(0, new ScriptedRandom(99)));
        assertEquals(Rarity.UNCOMMON, RarityRoll.roll(0, new ScriptedRandom(100)));
        assertEquals(Rarity.RARE, RarityRoll.roll(0, new ScriptedRandom(150)));
        assertEquals(Rarity.EPIC, RarityRoll.roll(0, new ScriptedRandom(160)));
        assertEquals(Rarity.LEGENDARY, RarityRoll.roll(0, new ScriptedRandom(165)));
    }

    @Test
    void eachLuckRaisesEachRarityByItsQuality() {
        // Luck 2, qualities 0 to 4: 100, 52, 14, 11, 9 of 186
        double[] lucky = RarityRoll.chances(2);
        double[] expected = {100 / 186.0, 52 / 186.0, 14 / 186.0, 11 / 186.0, 9 / 186.0};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], lucky[i], 1e-12);
        }
    }

    @Test
    void aHugeLuckCountsAsTheMaximumAndStillRolls() {
        double[] huge = RarityRoll.chances(Integer.MAX_VALUE);
        int l = Weights.MAX_LUCK;
        assertEquals((1 + 4.0 * l) / (166 + 10.0 * l), huge[Rarity.LEGENDARY.ordinal()], 1e-12);
        assertEquals(Rarity.COMMON, RarityRoll.roll(Integer.MAX_VALUE, new ScriptedRandom(0)));
    }

    @Test
    void luckMakesRareFishLikelier() {
        double[] none = RarityRoll.chances(0);
        double[] lucky = RarityRoll.chances(2);
        assertTrue(lucky[Rarity.LEGENDARY.ordinal()] > none[Rarity.LEGENDARY.ordinal()]);
        assertTrue(lucky[Rarity.COMMON.ordinal()] < none[Rarity.COMMON.ordinal()]);
    }
}
