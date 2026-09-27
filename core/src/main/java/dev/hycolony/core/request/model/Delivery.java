package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Objects;

/**
 * A courier task moving one item stack from a warehouse rack ({@code start}) to a requester
 * ({@code target}) (MC {@code AbstractDeliverymanRequestable}, {@code Delivery}). Not a
 * {@link Deliverable}: no built-in stock resolver ever matches it.
 *
 * <p>MC {@code Delivery.equals} ignores {@link ItemAmount#count()} of {@code stack}: two deliveries of the
 * same item between the same start and target are equal regardless of quantity.
 */
public record Delivery(BlockPos start, RequesterId target, ItemAmount stack, int priority) implements Requestable {
    /** MC {@code AbstractDeliverymanRequestable.DEFAULT_DELIVERY_PRIORITY}: warehouse and crafter follow-ups. */
    public static final int DEFAULT_DELIVERY_PRIORITY = 13;

    /** MC {@code AbstractDeliverymanRequestable.MAX_AGING_PRIORITY}: ceiling of {@link #withAgedPriority()}. */
    public static final int MAX_AGING_PRIORITY = 14;

    public Delivery {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(stack, "stack");
    }

    /** MC {@code incrementPriorityDueToAging}: +1 per aging tick, never above {@link #MAX_AGING_PRIORITY}. */
    public Delivery withAgedPriority() {
        return new Delivery(start, target, stack, Math.min(MAX_AGING_PRIORITY, priority + 1));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Delivery d
                && priority == d.priority
                && start.equals(d.start)
                && target.equals(d.target)
                && stack.item().equals(d.stack.item());
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, target, stack.item(), priority);
    }

    @Override
    public String describe() {
        return "Deliver " + stack.count() + " x " + stack.item().id() + " to " + target.value();
    }
}
