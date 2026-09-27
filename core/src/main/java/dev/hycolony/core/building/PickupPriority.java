package dev.hycolony.core.building;

import dev.hycolony.core.request.model.Pickup;

/**
 * How eagerly couriers empty a building (MC {@code AbstractBuildingContainer.unscaledPickUpPriority}): 0 means never,
 * up to {@link Pickup#MAX_BUILDING_PRIORITY}.
 */
public final class PickupPriority {
    /** MC {@code unscaledPickUpPriority} default. */
    public static final int DEFAULT = 5;

    private int value = DEFAULT;

    PickupPriority() {}

    public int value() {
        return value;
    }

    /** Sets the priority, clamped to 0..{@link Pickup#MAX_BUILDING_PRIORITY}. */
    public void set(int priority) {
        value = Math.clamp(priority, 0, Pickup.MAX_BUILDING_PRIORITY);
    }

    /** MC {@code alterPickUpPriority}: adds {@code delta} (the window's +1/-1), clamped like {@link #set}. */
    public void alter(int delta) {
        set(value + delta);
    }
}
