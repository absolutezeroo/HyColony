package dev.hycolony.core.app.citizen;

import static dev.hycolony.core.app.citizen.HealthBar.Heart.EMPTY;
import static dev.hycolony.core.app.citizen.HealthBar.Heart.GOLDEN;
import static dev.hycolony.core.app.citizen.HealthBar.Heart.HALF_GOLDEN;
import static dev.hycolony.core.app.citizen.HealthBar.Heart.HALF_RED;
import static dev.hycolony.core.app.citizen.HealthBar.Heart.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.citizen.HealthBar.Heart;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC CitizenWindowUtils.createHealthBar: ten slots, each its layers from the empty background up. */
class HealthBarTest {
    private static List<List<Heart>> slots(List<Heart> first, int n, List<Heart> rest) {
        List<List<Heart>> out = new java.util.ArrayList<>();
        out.add(first);
        out.addAll(Collections.nCopies(n, rest));
        return out;
    }

    @Test
    void noHealthIsTenEmptyHearts() {
        assertEquals(Collections.nCopies(10, List.of(EMPTY)), HealthBar.of(0));
    }

    @Test
    void fullHealthIsTenRedHearts() {
        assertEquals(Collections.nCopies(10, List.of(EMPTY, RED)), HealthBar.of(20));
    }

    @Test
    void anOddHealthEndsOnAHalfHeart() {
        List<List<Heart>> bar = HealthBar.of(5);
        assertEquals(List.of(EMPTY, RED), bar.get(1));
        assertEquals(List.of(EMPTY, HALF_RED), bar.get(2), "MC: the half red heart over the empty one");
        assertEquals(List.of(EMPTY), bar.get(3));
    }

    @Test
    void oneMoreThanTwentyStartsWithAHalfGoldenHeartAsMc() {
        assertEquals(slots(List.of(EMPTY, RED, HALF_GOLDEN), 9, List.of(EMPTY, RED)), HealthBar.of(21));
    }

    @Test
    void fortyIsTenGoldenHearts() {
        assertEquals(Collections.nCopies(10, List.of(EMPTY, GOLDEN)), HealthBar.of(40));
    }

    @Test
    void theLabelIsHalfTheHealth() {
        assertEquals(10, HealthBar.label(20));
        assertEquals(2, HealthBar.label(5));
    }
}
