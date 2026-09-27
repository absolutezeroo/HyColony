package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.logistics.pickup.HutKeep;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Pickup;
import java.util.List;

/**
 * MC EntityAIWorkDeliveryman.pickup: empties a hut one slot per step of what it does not keep ({@link HutKeep}),
 * until its last slot, the courier's carry limit or a full inventory.
 */
final class PickupRound {
    /** MC: the XP of one pickup. */
    static final double XP_PER_PICKUP = 0.05;
    /** MC PRIORITY_FORCING_DUMP: a pickup at least this urgent is stored at the warehouse right away. */
    static final int PRIORITY_FORCING_DUMP = 10;
    /** MC pickup: {@code setDelay(5)} between two slots. */
    static final int SLOT_DELAY = 5;

    private final CourierContext ctx;
    /**
     * The slot of the hut's containers (hut first) to look at next (MC currentSlot). Deviation from MC: it counts the
     * non-empty slots the container port lists, not every slot.
     */
    private int slot;
    /** What the hut keeps, counted over this round (MC alreadyKept); null between rounds. */
    private HutKeep keep;

    PickupRound(CourierContext ctx) {
        this.ctx = ctx;
    }

    /**
     * One step: START_WORKING once done (DUMPING for a pickup of priority {@link #PRIORITY_FORCING_DUMP}), DUMPING
     * when the courier can carry no more; a vanished hut fails the pickup.
     */
    CourierState pickup() {
        ctx.setDelay(CourierContext.WALK_DELAY);
        Request task = ctx.task().filter(r -> r.requestable() instanceof Pickup).orElse(null);
        if (task == null) {
            return CourierState.START_WORKING;
        }
        if (cannotHoldMoreItems()) {
            reset();
            return CourierState.DUMPING;
        }
        Building hut = ctx.colony().buildings().byRequester(task.requester()).orElse(null);
        if (hut == null) {
            ctx.job().finishRequest(ctx.colony(), false);
            return CourierState.START_WORKING;
        }
        if (!ctx.walkTo(hut.position())) {
            return CourierState.PICKUP;
        }
        if (pickupFromBuilding(hut)) {
            reset();
            ctx.job().finishRequest(ctx.colony(), true);
            ctx.award(XP_PER_PICKUP);
            return ((Pickup) task.requestable()).priority() >= PRIORITY_FORCING_DUMP
                    ? CourierState.DUMPING
                    : CourierState.START_WORKING;
        }
        if (ctx.inventory().freeSlots() <= 0) {
            reset();
            return CourierState.DUMPING;
        }
        ctx.setDelay(SLOT_DELAY);
        return CourierState.PICKUP;
    }

    private void reset() {
        slot = 0;
        keep = null;
    }

    /** MC cannotHoldMoreItems, capped by the courier hut's level. */
    private boolean cannotHoldMoreItems() {
        return ctx.hut()
                .map(hut -> DeliverymanHut.cannotHoldMoreItems(hut, ctx.inventory()))
                .orElse(false);
    }

    /** MC pickupFromBuilding: takes from the current slot; true once past the hut's last slot. */
    private boolean pickupFromBuilding(Building hut) {
        if (cannotHoldMoreItems() || ctx.inventory().freeSlots() <= 0) {
            return false;
        }
        if (keep == null) {
            keep = HutKeep.of(ctx.colony(), hut, false);
        }
        int index = slot;
        for (BlockPos container : hut.containers()) {
            List<ItemAmount> stacks = ctx.containers().stacks(container);
            if (index < stacks.size()) {
                take(container, stacks.get(index));
                return false;
            }
            index -= stacks.size();
        }
        return true;
    }

    /**
     * Moves what the hut does not keep of {@code stack} into the inventory (what does not fit goes back). The next slot
     * comes next, unless the whole stack left: the listing only has non-empty slots, so the next one moved up.
     */
    private void take(BlockPos container, ItemAmount stack) {
        int amount = keep.removable(stack);
        int taken = amount > 0 ? ctx.containers().extract(List.of(container), stack.item(), amount) : 0;
        ItemAmount rest = taken > 0 ? ctx.inventory().insert(stack.withCount(taken), ctx.catalog()::maxStack) : null;
        if (rest != null) {
            ctx.containers().insert(List.of(container), rest);
        }
        if (taken < stack.count() || rest != null) {
            slot++;
        }
        ctx.showHeld();
    }
}
