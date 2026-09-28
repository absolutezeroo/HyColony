package dev.hycolony.core.crafting.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a crafter's runs go (MC AbstractEntityAICrafting.executeCraftingAction and afterDump): each run's output joins
 * the task's deliveries; its other outputs wait in {@link CraftingTasks#secondaryOutputs} until the dump, then go to
 * the nearest warehouse.
 */
final class CraftedOutputs {
    /** MC AbstractDeliverymanRequestable.MAX_BUILDING_PRIORITY: how urgent a secondary output's delivery is. */
    static final int SECONDARY_DELIVERY_PRIORITY = Pickup.MAX_BUILDING_PRIORITY;

    private final CraftingWorkContext ctx;

    CraftedOutputs(CraftingWorkContext ctx) {
        this.ctx = ctx;
    }

    /**
     * MC executeCraftingAction's loop over the added stacks: one of the recipe's output item becomes a delivery of
     * {@code task}, any other is counted in the secondary outputs. MC merges the task's deliveries as they come (its
     * addDelivery); here they are merged into full stacks when the task is done (CraftingProductionResolver).
     */
    void route(Recipe recipe, List<ItemAmount> added, Request task) {
        for (ItemAmount stack : added) {
            if (stack.item().equals(recipe.primaryOutput().item())) {
                ctx.colony().requests().addDelivery(task.token(), stack);
            } else {
                ctx.tasks().secondaryOutputs().merge(stack.item(), stack.count(), Integer::sum);
            }
        }
    }

    /**
     * MC afterDump: the nearest warehouse asks for each secondary output, a {@link Delivery} from the hut per full
     * stack, at {@link #SECONDARY_DELIVERY_PRIORITY}; then they are forgotten, even with no warehouse (they are in the
     * hut anyway).
     */
    void sendSecondaryOutputs() {
        Map<ItemKey, Integer> outputs = ctx.tasks().secondaryOutputs();
        if (outputs.isEmpty()) {
            return;
        }
        nearestWarehouse()
                .ifPresent(warehouse -> outputs.forEach((item, count) -> {
                    int max = Math.max(1, ctx.items().maxStack(item));
                    for (int left = count; left > 0; left -= max) {
                        ItemAmount stack = new ItemAmount(item, Math.min(max, left));
                        ctx.colony()
                                .requests()
                                .createAndAssign(
                                        warehouse,
                                        new Delivery(
                                                ctx.hut().position(),
                                                warehouse.requesterId(),
                                                stack,
                                                SECONDARY_DELIVERY_PRIORITY),
                                        -1);
                    }
                }));
        outputs.clear();
        ctx.colony().markDirty();
    }

    /**
     * MC getBestBuilding(worker, BuildingWareHouse.class): the built, loaded warehouse closest to the crafter (to its
     * hut while it has no body); the first of equals.
     */
    private Optional<Building> nearestWarehouse() {
        BlockPos from = ctx.walker().at().orElse(ctx.hut().position());
        Building best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Building b : ctx.colony().buildings().all()) {
            if (WarehouseBuilding.TYPE_ID.equals(b.type().id())
                    && b.level() > 0
                    && ctx.colony().context().worldQuery().isLoaded(b.position())
                    && b.position().distSq(from) < bestDistance) {
                best = b;
                bestDistance = b.position().distSq(from);
            }
        }
        return Optional.ofNullable(best);
    }
}
