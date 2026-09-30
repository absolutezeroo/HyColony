package dev.hycolony.core.request.model;

/**
 * A request to empty a building's rack overflow into the warehouse (MC {@code AbstractDeliverymanRequestable},
 * {@code Pickup}). {@code day} is the colony day it becomes due; {@code quantity} is an estimate of the items to
 * collect. Not a {@link Deliverable}: no built-in stock resolver ever matches it.
 *
 * <p>MC {@code Pickup.equals} only compares {@link #priority()}, inherited unchanged from
 * {@code AbstractDeliverymanRequestable}: two pickups of the same priority are equal regardless of day or
 * quantity.
 */
public record Pickup(int priority, int day, int quantity) implements Requestable {
    /**
     * MC {@code AbstractDeliverymanRequestable.MAX_BUILDING_PRIORITY}: highest priority a building's setting allows.
     */
    public static final int MAX_BUILDING_PRIORITY = 10;

    /** MC {@code incrementPriorityDueToAging}: +1 per aging tick, never above {@link Delivery#MAX_AGING_PRIORITY}. */
    public Pickup withAgedPriority() {
        return new Pickup(Math.min(Delivery.MAX_AGING_PRIORITY, priority + 1), day, quantity);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Pickup p && priority == p.priority;
    }

    @Override
    public int hashCode() {
        return priority;
    }

    @Override
    public String describe() {
        return "Pick up ~" + quantity + " items (due day " + day + ")";
    }
}
