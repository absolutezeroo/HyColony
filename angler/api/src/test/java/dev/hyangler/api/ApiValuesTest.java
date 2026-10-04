package dev.hyangler.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiValuesTest {

    @Test
    void tackleOfKeepsThePartsOfLaterSubProjectsNeutral() {
        Tackle t = Tackle.of(2, 1, 40);
        assertEquals(2, t.lure());
        assertEquals(1, t.luck());
        assertEquals(40, t.maxLine());
        assertEquals(1.0, t.reelSpeed());
        assertEquals(0.0, t.lineStrength());
        assertEquals(0, t.hookSize());
        assertEquals(0, t.depthBias());
        assertTrue(t.bait().isEmpty());
    }

    @Test
    void noTackleHasNoLureNoLuckAndTheDefaultLine() {
        assertEquals(Tackle.of(0, 0, Tackle.DEFAULT_MAX_LINE), Tackle.NONE);
    }

    @Test
    void tackleRefusesANegativeLureOrAnEmptyLine() {
        assertThrows(IllegalArgumentException.class, () -> Tackle.of(-1, 0, 32));
        assertThrows(IllegalArgumentException.class, () -> Tackle.of(0, 0, 0));
    }

    @Test
    void contextRefusesAnHourOutsideTheDay() {
        assertThrows(IllegalArgumentException.class, () -> context(24.0));
        assertThrows(IllegalArgumentException.class, () -> context(-0.5));
        assertEquals(23.9, context(23.9).hour());
    }

    @Test
    void catchRefusesAnEmptyCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Catch("Fish_Bluegill_Item", 0, CatchCategory.FISH, Optional.empty(), "Fish_Bluegill_Item"));
    }

    @Test
    void rodStatsGiveTheirTackle() {
        assertEquals(Tackle.of(1, 2, 32), new RodStats(3, 1, 2, 32).tackle());
    }

    @Test
    void rarityNamesAreHytaleQualities() {
        assertEquals("Uncommon", Rarity.UNCOMMON.hytaleName());
        assertEquals("Legendary", Rarity.LEGENDARY.hytaleName());
    }

    private static FishingContext context(double hour) {
        return new FishingContext(
                "Env_Zone1_Plains",
                "Zone1",
                WaterKind.FRESH,
                3,
                true,
                true,
                hour,
                "Zone1_Sunny",
                false,
                0,
                Tackle.NONE);
    }
}
