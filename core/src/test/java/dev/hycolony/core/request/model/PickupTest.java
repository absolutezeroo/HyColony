package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PickupTest {
    @Test
    void equalsComparesOnlyPriorityLikeMc() {
        assertEquals(new Pickup(5, 1, 20), new Pickup(5, 99, 1), "MC Pickup.equals ignores day and quantity");
        assertEquals(new Pickup(5, 1, 20).hashCode(), new Pickup(5, 99, 1).hashCode());
    }

    @Test
    void withAgedPriorityIncrementsByOneCappedAtMaxAgingPriority() {
        assertEquals(6, new Pickup(5, 1, 20).withAgedPriority().priority());
        assertEquals(
                Delivery.MAX_AGING_PRIORITY,
                new Pickup(Delivery.MAX_AGING_PRIORITY, 1, 20)
                        .withAgedPriority()
                        .priority(),
                "already at the ceiling");
    }
}
