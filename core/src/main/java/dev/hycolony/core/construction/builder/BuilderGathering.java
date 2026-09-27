package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Brings the builder the materials of its build (MC GATHERING_REQUIRED_MATERIALS): the current bucket and the item
 * needed now from the hut, and requests for what the hut lacks.
 */
final class BuilderGathering {
    /** MC getResourceBatchMultiplier: 1 until research exists. */
    static final int RESOURCE_BATCH_MULTIPLIER = 1;

    private final BuilderContext ctx;
    /** The item the last placement lacked. */
    private @Nullable ItemKey neededItem;

    private int lastRecomputeIndex = -1;

    BuilderGathering(BuilderContext ctx) {
        this.ctx = ctx;
    }

    void reset() {
        neededItem = null;
        lastRecomputeIndex = -1;
    }

    /**
     * The item for the placement at {@code index} is not in the inventory. If it is in neither the current nor the
     * next bucket (the world changed since the needs were computed), the needs are recomputed first, at most once per
     * position.
     */
    BuilderState missing(ItemKey item, int index) {
        BuildingResourcesModule resources = ctx.resources();
        boolean inBuckets =
                resources.currentBucket().map(b -> b.containsKey(item)).orElse(false)
                        || resources.nextBucket().map(b -> b.containsKey(item)).orElse(false);
        if (!inBuckets && resources.needs().remaining().containsKey(item) && lastRecomputeIndex != index) {
            lastRecomputeIndex = index;
            resources.start(
                    ctx.site().loadedOrder(), NeededResources.compute(ctx.site().plan(), ctx.blocks(), ctx.catalog()));
        }
        neededItem = item;
        return BuilderState.GATHERING_REQUIRED_MATERIALS;
    }

    /**
     * Fetches the current bucket (and the item needed now) from the hut, asks the building for what the current and
     * next buckets still miss (async), and makes the request for the item needed now sync.
     */
    @Nullable
    BuilderState gather() {
        if (!ctx.site().loaded()) {
            return BuilderState.START_WORKING;
        }
        if (!ctx.walkToHut()) {
            return null;
        }
        BuilderStock stock = ctx.stock();
        ctx.requests().receiveCompletedBuildingRequests();
        ctx.resources().currentBucket().ifPresent(stock::takeBucket);
        ItemKey needed = neededItem;
        neededItem = null;
        if (needed != null && stock.inventory().count(needed) == 0 && !fetch(needed)) {
            stock.dumpNow(); // the hut has it, the inventory has no room: dump rather than ask again
            return BuilderState.INVENTORY_FULL;
        }
        Stage stage = ctx.site().loadedOrder().stage();
        if (stage != Stage.CLEAR && stage != Stage.REMOVE) { // never request while clearing or removing
            request(needed);
        }
        return ctx.requests().hasSyncRequests() ? BuilderState.NEEDS_ITEM : BuilderState.BUILDING_STEP;
    }

    /** Takes the item from the hut; false when the hut has some but none fitted in the inventory. */
    private boolean fetch(ItemKey needed) {
        BuilderStock stock = ctx.stock();
        stock.take(needed, requestAmount(needed));
        return stock.inventory().count(needed) != 0 || stock.hutCount(needed) <= 0;
    }

    /** Requests what the current and next buckets miss (async), and the item needed now (sync). */
    private void request(@Nullable ItemKey needed) {
        BuilderStock stock = ctx.stock();
        Set<ItemKey> requested = ctx.requests().requestedItems();
        ctx.resources()
                .missingForCurrentAndNext(stock.inventory(), stock::hutCount)
                .forEach((item, n) -> {
                    if (requested.add(item)) {
                        ctx.requests().requestForBucket(item, n * RESOURCE_BATCH_MULTIPLIER);
                    }
                });
        if (needed != null && stock.inventory().count(needed) == 0) {
            ctx.requests().requestNow(needed, requestAmount(needed));
        }
    }

    /** MC waitForRequests / lookForRequests: fetch every completed request at the hut, wait for the open ones. */
    @Nullable
    BuilderState waitForRequests() {
        BuilderRequests requests = ctx.requests();
        List<Request> mine = requests.mine();
        if (mine.isEmpty()) {
            return BuilderState.START_WORKING;
        }
        if (!ctx.walkToHut()) {
            return null;
        }
        requests.receiveCompletedBuildingRequests();
        requests.claimOpenFromHut();
        for (Request r : mine) {
            if (r.state() == RequestState.COMPLETED) {
                requests.pickUp(r);
            }
        }
        return requests.hasSyncRequests() ? null : BuilderState.START_WORKING;
    }

    /** MC getTotalAmount: what is still needed of the item, capped to a stack, at least 1. */
    private int requestAmount(ItemKey item) {
        int left = ctx.resources().needs().remaining().getOrDefault(item, 1);
        return Math.max(1, Math.min(left, ctx.catalog().maxStack(item)));
    }
}
