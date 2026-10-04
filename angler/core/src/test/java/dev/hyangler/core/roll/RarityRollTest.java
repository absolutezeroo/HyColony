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
    void luckMakesRareFishLikelier() {
        double[] none = RarityRoll.chances(0);
        double[] lucky = RarityRoll.chances(2);
        assertTrue(lucky[Rarity.LEGENDARY.ordinal()] > none[Rarity.LEGENDARY.ordinal()]);
        assertTrue(lucky[Rarity.COMMON.ordinal()] < none[Rarity.COMMON.ordinal()]);
    }
}
