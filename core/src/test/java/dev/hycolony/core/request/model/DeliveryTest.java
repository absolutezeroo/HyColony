package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import org.junit.jupiter.api.Test;

class DeliveryTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final BlockPos START = new BlockPos(0, 64, 0);
    private static final RequesterId TARGET = new RequesterId("building:5,64,5");

    private static Delivery delivery(int count, int priority) {
        return new Delivery(START, TARGET, new ItemAmount(PLANKS, count), priority);
    }

    @Test
    void equalsIgnoresDeliveredCountButNotStartTargetItemOrPriority() {
        assertEquals(delivery(4, 13), delivery(20, 13), "MC Delivery.equals ignores stack size");
        assertNotEquals(delivery(4, 13), delivery(4, 14), "priority differs");
        assertNotEquals(
                delivery(4, 13),
                new Delivery(START, TARGET, new ItemAmount(new ItemKey("Rock_Stone"), 4), 13),
                "item differs");
        assertNotEquals(
                delivery(4, 13),
                new Delivery(new BlockPos(1, 64, 0), TARGET, new ItemAmount(PLANKS, 4), 13),
                "start differs");
        assertNotEquals(
                delivery(4, 13),
                new Delivery(START, new RequesterId("building:9,64,9"), new ItemAmount(PLANKS, 4), 13),
                "target differs");
    }

    @Test
    void withAgedPriorityIncrementsByOneCappedAtMaxAgingPriority() {
        assertEquals(
                14,
                delivery(4, Delivery.DEFAULT_DELIVERY_PRIORITY)
                        .withAgedPriority()
                        .priority());
        assertEquals(
                Delivery.MAX_AGING_PRIORITY,
                delivery(4, Delivery.MAX_AGING_PRIORITY).withAgedPriority().priority(),
                "already at the ceiling");
    }
}
