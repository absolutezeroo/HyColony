package dev.hycolony.core.app.citizen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The citizen window's health bar (MC CitizenWindowUtils.createHealthBar): ten heart slots, blue, green, golden then
 * red hearts filled from the left, a half heart drawn over the heart below it.
 */
public final class HealthBar {
    /** MC MAX_HEART_ICONS. */
    private static final int SLOTS = 10;

    /** MC HeartsEnum: its value in health points, its half heart and the heart one step below it. */
    public enum Heart {
        EMPTY(0),
        HALF_RED(1),
        RED(2),
        HALF_GOLDEN(3),
        GOLDEN(4),
        HALF_GREEN(5),
        GREEN(6),
        HALF_BLUE(7),
        BLUE(8);

        private final int value;

        Heart(int value) {
            this.value = value;
        }

        /** The heart one step below: RED's is EMPTY, GOLDEN's RED…; empty for EMPTY and half hearts. */
        private Optional<Heart> previous() {
            return value % 2 == 0 && value > 0 ? Optional.of(values()[ordinal() - 2]) : Optional.empty();
        }

        private Heart half() {
            return values()[ordinal() - 1];
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
            Heart prev = heart.previous().orElseThrow();
            while (pos < SLOTS && left > prev.value * SLOTS + 1) {
                slots.get(pos++).add(heart);
                left -= heart.value - prev.value;
            }
            if (left % 2 == 1 && pos < SLOTS && left > prev.value * SLOTS) {
                if (prev != Heart.EMPTY) { // MC draws EMPTY again; the background already is
                    slots.get(pos).add(prev);
                }
                slots.get(pos++).add(heart.half());
                left -= heart.half().value - prev.value;
            }
            return pos >= SLOTS;
        }
    }

    /** MC's label beside the hearts: half the health, rounded down. */
    public static int label(int health) {
        return health / 2;
    }
}
