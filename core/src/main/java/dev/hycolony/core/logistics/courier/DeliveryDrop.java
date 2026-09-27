package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * MC EntityAIWorkDeliveryman.deliver: walks to the target and puts in every carried stack of the deliveries going
 * there, swapping out a stack the target does not need when it is full ({@link ForcedInsert}).
 */
final class DeliveryDrop {
    /** MC: the XP of one delivery trip. */
    static final double XP_PER_DELIVERY = 1.5;

    private final CourierContext ctx;

    DeliveryDrop(CourierContext ctx) {
        this.ctx = ctx;
    }

    /**
     * START_WORKING once delivered; DUMPING when a stack found no room (the courier still carries it) or when the task
     * is no longer a delivery. A vanished target resolves the deliveries (MC); nothing to deliver fails them.
     */
    CourierState deliver() {
        Request task =
                ctx.task().filter(r -> r.requestable() instanceof Delivery).orElse(null);
        if (task == null) {
            return CourierState.DUMPING;
        }
        Building target = RequesterLocation.of(ctx.colony(), ((Delivery) task.requestable()).target())
                .flatMap(ctx.colony().buildings()::at)
                .orElse(null);
        if (target == null) {
            ctx.job().finishRequest(ctx.colony(), true);
            return CourierState.START_WORKING;
        }
        if (!ctx.walkTo(target.position())) {
            ctx.setDelay(CourierContext.WALK_DELAY);
            return CourierState.DELIVERY;
        }
        Set<ItemKey> items = new HashSet<>();
        ctx.job()
                .tasksWithSameDestination(ctx.colony(), task)
                .forEach(r -> items.add(((Delivery) r.requestable()).stack().item()));
        Optional<Boolean> success = putIn(target, items);
        ctx.showHeld();
        if (success.isEmpty()) {
            ctx.job().finishRequest(ctx.colony(), false);
            return CourierState.START_WORKING;
        }
        ctx.award(XP_PER_DELIVERY);
        ctx.job().finishRequest(ctx.colony(), true);
        return success.get() ? CourierState.START_WORKING : CourierState.DUMPING;
    }

    /**
     * Moves each inventory slot holding one of {@code items} into {@code target}; what comes back (the rest, or a
     * swapped-out stack) returns to that slot. Empty when no slot held any; false when a stack found no room at all.
     */
    private Optional<Boolean> putIn(Building target, Set<ItemKey> items) {
        Inventory inventory = ctx.inventory();
        Set<ItemKey> kept = inRequest(ctx.colony(), target);
        boolean extracted = false;
        boolean success = true;
        for (int i = 0; i < inventory.size(); i++) {
            ItemAmount stack = inventory.slot(i).orElse(null);
            if (stack == null || !items.contains(stack.item())) {
                continue;
            }
            inventory.set(i, Optional.empty());
            extracted = true;
            ItemAmount back = ForcedInsert.insert(ctx.containers(), target.containers(), stack, kept::contains);
            if (back != null) {
                success &= !back.equals(stack);
                inventory.set(i, Optional.of(back));
            }
        }
        return extracted ? Optional.of(success) : Optional.empty();
    }

    /**
     * MC AbstractBuilding.isItemStackInRequest: the items already delivered to the target's open requests, which a
     * swap must not take out. Deviation from MC: every open request of the building counts, not only those of its
     * assigned citizens, since our builder asks for its materials in the hut's name.
     */
    private static Set<ItemKey> inRequest(Colony colony, Building target) {
        Set<ItemKey> out = new HashSet<>();
        for (Request r : colony.requests().byRequester(target.requesterId())) {
            if (r.state().ordinal() < RequestState.COMPLETED.ordinal()) {
                r.deliveries().forEach(d -> out.add(d.item()));
            }
        }
        return out;
    }
}
