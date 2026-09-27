package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestToken;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MC EntityAIWorkDeliveryman.prepareDelivery: loads, one per step, the deliveries going to the same place as the
 * current one, up to the courier's parallel count, then goes delivering.
 */
final class DeliveryPreparation {
    private final CourierContext ctx;
    /** What the deliveries already covered take from the inventory, per item (kept to spare an allocation). */
    private final Map<ItemKey, Integer> covered = new HashMap<>();
    /** The rack the courier walks to for the task {@link #walkingFor}; null once there. */
    private BlockPos walkingTo;

    private RequestToken walkingFor;

    DeliveryPreparation(CourierContext ctx) {
        this.ctx = ctx;
    }

    /** The first delivery the inventory does not cover yet, and its 1-based place in the list (MC nextPickUp). */
    private record Next(Request task, int place) {}

    /**
     * Walks to the next delivery's rack and takes its stack; DELIVERY once all are loaded (or the parallel count is
     * reached), DUMPING on a full inventory. A rack without the stack fails the delivery, unless others are already
     * loaded: they go without it.
     */
    CourierState prepare() {
        Request task =
                ctx.task().filter(r -> r.requestable() instanceof Delivery).orElse(null);
        if (task == null) {
            return CourierState.START_WORKING;
        }
        if (stillWalking(task)) {
            return CourierState.PREPARE_DELIVERY; // no need to list the tasks again on the way
        }
        Next next = next(ctx.job().tasksWithSameDestination(ctx.colony(), task)).orElse(null);
        if (next == null || next.place() > ctx.job().maxParallelDeliveries(ctx.colony())) {
            return CourierState.DELIVERY;
        }
        Delivery delivery = (Delivery) next.task().requestable();
        if (!ctx.walkTo(delivery.start())) {
            walkingTo = delivery.start();
            walkingFor = task.token();
            return CourierState.PREPARE_DELIVERY;
        }
        if (ctx.inventory().isFull()) {
            return CourierState.DUMPING;
        }
        return load(next, delivery);
    }

    /** True while the walk started for {@code task} goes on; forgets it once over (or for another task). */
    private boolean stillWalking(Request task) {
        if (walkingTo != null && task.token().equals(walkingFor) && !ctx.walkTo(walkingTo)) {
            return true;
        }
        walkingTo = null;
        return false;
    }

    /** MC: takes the delivery's stack at its rack; without it, the loaded ones go, or the delivery fails alone. */
    private CourierState load(Next next, Delivery delivery) {
        ctx.job().addConcurrentDelivery(next.task().token());
        if (gather(delivery.start(), delivery.stack())) {
            return CourierState.PREPARE_DELIVERY;
        }
        if (next.place() > 1) {
            ctx.job().removeConcurrentDelivery(next.task().token());
            return CourierState.DELIVERY;
        }
        ctx.job().finishRequest(ctx.colony(), false);
        ctx.job().removeConcurrentDelivery(next.task().token());
        return CourierState.START_WORKING;
    }

    /** MC: walks the list with a running tally of what the inventory already covers; empty once all are covered. */
    private Optional<Next> next(List<Request> tasks) {
        covered.clear();
        int place = 0;
        for (Request r : tasks) {
            place++;
            ItemAmount stack = ((Delivery) r.requestable()).stack();
            int has = covered.getOrDefault(stack.item(), 0);
            if (ctx.inventory().count(stack.item()) < has + stack.count()) {
                return Optional.of(new Next(r, place));
            }
            covered.merge(stack.item(), stack.count(), Integer::sum);
        }
        return Optional.empty();
    }

    /**
     * MC gatherIfInTileEntity: when the hut (all its containers) or the rack at {@code start} holds the whole stack,
     * moves it into the inventory; true once all of it is there. What does not fit goes back.
     */
    private boolean gather(BlockPos start, ItemAmount stack) {
        List<BlockPos> source =
                ctx.colony().buildings().at(start).map(Building::containers).orElse(List.of(start));
        if (ctx.containers().count(source, stack.item()) < stack.count()) {
            return false;
        }
        int taken = ctx.containers().extract(source, stack.item(), stack.count());
        if (taken <= 0) {
            return false;
        }
        ItemAmount rest = ctx.inventory().insert(stack.withCount(taken), ctx.catalog()::maxStack);
        if (rest != null) {
            ctx.putBack(source, rest);
        }
        ctx.showHeld();
        return rest == null && taken >= stack.count();
    }
}
