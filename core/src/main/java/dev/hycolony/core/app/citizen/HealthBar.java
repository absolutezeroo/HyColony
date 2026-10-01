package dev.hycolony.core.app.citizen;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The citizen window's health bar (MC CitizenWindowUtils.createHealthBar): ten heart slots, blue, green, golden then
 * red hearts filled from the left, a half heart drawn over the heart below it.
 */
public final class HealthBar {
    /** MC MAX_HEART_ICONS. */
    private static final int SLOTS = 10;

    /** MC HeartsEnum: its value in health points, its half heart and the heart one step below it. */
    public enum Heart {
        EMPTY(0, null, null),
        HALF_RED(1, null, EMPTY),
        RED(2, HALF_RED, EMPTY),
        HALF_GOLDEN(3, null, RED),
        GOLDEN(4, HALF_GOLDEN, RED),
        HALF_GREEN(5, null, GOLDEN),
        GREEN(6, HALF_GREEN, GOLDEN),
        HALF_BLUE(7, null, GREEN),
        BLUE(8, HALF_BLUE, GREEN);

        private final int value;
        private final @Nullable Heart half;
        private final @Nullable Heart previous;

        Heart(int value, @Nullable Heart half, @Nullable Heart previous) {
            this.value = value;
            this.half = half;
            this.previous = previous;
        }
    }

    private HealthBar() {}

    /**
     * MC createHealthBar on {@code health} (MC points, 20 for ten red hearts): each slot's layers from the empty
     * background up. MC's quirks are kept, such as 21 starting with a half golden heart over a red one.
     */
    public static List<List<Heart>> of(int health) {
        Filler filler = new Filler(health);
        for (Heart heart : List.of(Heart.BLUE, Heart.GREEN, Heart.GOLDEN, Heart.RED)) {
            if (filler.fill(heart)) {
                break;
            }
        }
        return filler.slots.stream().map(List::copyOf).toList();
    }

    /** MC createHealthBar's loop state: the slots, the health still to draw and the next slot. */
    private static final class Filler {
        private final List<List<Heart>> slots = new ArrayList<>(SLOTS);
        private int left;
        private int pos;

        Filler(int health) {
            for (int i = 0; i < SLOTS; i++) {
                slots.add(new ArrayList<>(List.of(Heart.EMPTY)));
            }
            left = health;
        }

        /** Draws {@code heart}s while the health is above its threshold, then its half heart; true once full. */
        boolean fill(Heart heart) {
            Heart prev = heart.previous;
            Heart half = heart.half;
            if (prev == null || half == null) { // MC skips half hearts and EMPTY
                return false;
            }
            while (pos < SLOTS && left > prev.value * SLOTS + 1) {
                slots.get(pos++).add(heart);
                left -= heart.value - prev.value;
            }
            if (left % 2 == 1 && pos < SLOTS && left > prev.value * SLOTS) {
                if (prev != Heart.EMPTY) { // MC draws EMPTY again; the background already is
                    slots.get(pos).add(prev);
                }
                slots.get(pos++).add(half);
                left -= half.value - prev.value;
            }
            return pos >= SLOTS;
        }
    }

    /** MC's label beside the hearts: half the health, rounded down. */
    public static int label(int health) {
        return health / 2;
    }
}
