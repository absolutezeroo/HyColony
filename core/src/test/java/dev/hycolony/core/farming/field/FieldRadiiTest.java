package dev.hycolony.core.farming.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.field.FieldRadii.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC FarmFieldPlotResizeMessage (the size budget) and WindowField (the radius button cycle). */
class FieldRadiiTest {
    @Test
    void defaultsAreFiveOnEverySide() {
        assertEquals(new FieldRadii(5, 5, 5, 5), FieldRadii.defaults());
    }

    @Test
    void growthBeyondTheBudgetIsRefused() {
        assertTrue(FieldRadii.defaults().resized(Direction.NORTH, 6).isEmpty());
        assertEquals(
                new FieldRadii(0, 0, 20, 0),
                new FieldRadii(0, 0, 0, 0).resized(Direction.NORTH, 20).orElseThrow());
    }

    @Test
    void shrinkingIsAlwaysAllowed() {
        assertEquals(
                new FieldRadii(5, 5, 1, 5),
                FieldRadii.defaults().resized(Direction.NORTH, 1).orElseThrow());
    }

    @Test
    void negativeRadiusIsRefused() {
        assertTrue(FieldRadii.defaults().resized(Direction.EAST, -1).isEmpty());
    }

    @Test
    void buttonCyclesFromOneToCurrentPlusLeftover() {
        FieldRadii r = FieldRadii.defaults();
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            r = r.cycled(Direction.SOUTH);
            seen.add(r.get(Direction.SOUTH));
        }
        // 5 with nothing left over -> 1, then 1 + 4 left over allows up to 5, then back to 1 (MC WindowField)
        assertEquals(List.of(1, 2, 3, 4, 5, 1), seen);
    }

    @Test
    void buttonLeavesAZeroSideAloneWhenNothingIsLeft() {
        FieldRadii full = new FieldRadii(0, 0, 20, 0);
        assertEquals(full, full.cycled(Direction.SOUTH));
    }
}
