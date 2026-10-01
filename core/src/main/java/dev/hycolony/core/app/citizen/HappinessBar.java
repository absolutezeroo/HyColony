package dev.hycolony.core.app.citizen;

import java.util.ArrayList;
import java.util.List;

/**
 * The citizen window's happiness bar (MC CitizenWindowUtils.createHappinessBar): ten smiley slots, red smileys filled
 * from the left. MC doubles the happiness after casting it to an int, so a half smiley never shows; that is kept.
 */
public final class HappinessBar {
    /** MC MAX_HEART_ICONS. */
    private static final int SLOTS = 10;
    /** MC SmileyEnum.RED.happinessValue: a full smiley stands for 2. */
    private static final int FULL_VALUE = 2;

    /** MC SmileyEnum. */
    public enum Smiley {
        EMPTY,
        HALF,
        FULL
    }

    private HappinessBar() {}

    /** MC createHappinessBar on {@code happiness} (0 to 10): one smiley per slot. */
    public static List<Smiley> of(double happiness) {
        int left = ((int) happiness) * FULL_VALUE;
        List<Smiley> slots = new ArrayList<>(SLOTS);
        while (slots.size() < SLOTS && left > 1) {
            slots.add(Smiley.FULL);
            left -= FULL_VALUE;
        }
        if (left % 2 == 1 && slots.size() < SLOTS) {
            slots.add(Smiley.HALF);
        }
        while (slots.size() < SLOTS) {
            slots.add(Smiley.EMPTY);
        }
        return List.copyOf(slots);
    }
}
