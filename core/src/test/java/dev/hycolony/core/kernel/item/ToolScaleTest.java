package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Hytale's gather powers turned into the core's hardness, tool level, speed and uses. */
class ToolScaleTest {
    @Test
    void hardnessGrowsAsBareHandsWeakenWithinItsBounds() {
        assertEquals(ToolScale.MIN_HARDNESS, ToolScale.hardness(1f), 1e-6, "one bare-hand hit");
        assertEquals(0.5f, ToolScale.hardness(0.1f), 1e-6);
        assertEquals(ToolScale.MAX_HARDNESS, ToolScale.hardness(0.001f), 1e-6, "capped");
        assertEquals(1f, ToolScale.hardness(0f), 1e-6, "bare hands cannot break it: one hit with the right tool");
    }

    @Test
    void levelIsTheSpecQualityAboveWood() {
        assertEquals(0, ToolScale.level(1));
        assertEquals(2, ToolScale.level(3));
        assertEquals(0, ToolScale.level(0));
    }

    @Test
    void speedIsThePowerOverBareHands() {
        assertEquals(4f, ToolScale.speed(0.4f, 0.1f), 1e-6);
        assertEquals(0.4f, ToolScale.speed(0.4f, 0f), 1e-6, "bare hands have no power: the tool's own");
        assertEquals(1f, ToolScale.speed(0f, 0.1f), 1e-6, "a tool without power works like bare hands");
    }

    @Test
    void usesAreTheBlocksMinedBeforeTheToolBreaks() {
        assertEquals(50, ToolScale.uses(100, 1, 0.5f), "two hits per block");
        assertEquals(100, ToolScale.uses(100, 1, 1f));
        assertEquals(1, ToolScale.uses(1, 5, 1f), "at least one");
        assertEquals(0, ToolScale.uses(0, 1, 1f), "unbreakable");
        assertEquals(0, ToolScale.uses(100, 0, 1f), "never worn");
    }
}
