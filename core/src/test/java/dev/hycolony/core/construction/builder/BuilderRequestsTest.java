package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.testing.TestContexts;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A save may hold builder requests for an item no block costs any more (the item that places a block changed,
 * docs/research/audit-monde-hytale.md A-15): loading the order cancels them, so the builder never waits on them.
 */
class BuilderRequestsTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final ItemKey WALL_TORCH = new ItemKey("Wood_Torch_Wall");
    private static final ItemKey TORCH = new ItemKey("Furniture_Crude_Torch");
    private static final ItemKey PLANKS = new ItemKey("Wood_Oak_Planks");

    private final TestContexts t = new TestContexts();
    private final Colony colony;
    private final Building hut;
    private final BuilderRequests requests;

    BuilderRequestsTest() {
        UUID alice = UUID.randomUUID();
        ColonyManager manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, UUID.randomUUID());
        hut = colony.buildings().at(HUT).orElseThrow();
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        requests = new BuilderRequests(colony, citizen, hut);
    }

    @Test
    void requestsForItemsTheOrderNoLongerNeedsAreCancelled() {
        requests.requestNow(WALL_TORCH, 4);
        requests.requestForBucket(PLANKS, 16);

        requests.cancelUnneeded(Set.of(TORCH, PLANKS));

        assertEquals(Set.of(PLANKS), requests.requestedItems());
    }

    /** A completed request holds items waiting in the hut: they are fetched, not cancelled. */
    @Test
    void aCompletedRequestForAnUnneededItemStays() {
        requests.requestForBucket(WALL_TORCH, 4);
        Request r = colony.requests().byRequester(hut.requesterId()).get(0);
        colony.requests().updateState(r.token(), RequestState.COMPLETED);

        requests.cancelUnneeded(Set.of(PLANKS));

        assertEquals(Set.of(WALL_TORCH), requests.requestedItems());
    }
}
