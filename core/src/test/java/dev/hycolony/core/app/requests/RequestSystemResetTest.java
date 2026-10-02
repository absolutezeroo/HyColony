package dev.hycolony.core.app.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.farming.job.FarmerJob;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.logistics.warehouse.WarehouseRequestQueue;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MC /mc colony requestsystem-reset (StandardRequestManager.reset, then InitialUpdate): every request and assignment
 * forgotten at once, the player and retrying resolvers new, every hut a provider again, and the queues MC keeps in its
 * stores emptied (spec 2026-10-02 lot 3, § 4).
 */
class RequestSystemResetTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final Colony colony;
    private final Building hut;

    RequestSystemResetTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(new BlockPos(0, 64, 0)).orElseThrow();
    }

    private RequestToken request() {
        return colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 4, 4, true), 0);
    }

    @Test
    void everyRequestAndIndexIsForgottenWithoutACallbackAndTheColonySaved() {
        RequestToken old = request();
        request();
        colony.clearDirty();

        RequestSystemReset.reset(colony);

        assertTrue(colony.requests().all().isEmpty());
        assertTrue(colony.requests().byRequester(hut.requesterId()).isEmpty(), "the hut's requests");
        assertTrue(colony.requests().assignedTo(RetryingResolver.ID).isEmpty(), "the assignments");
        assertTrue(colony.requests().resolverOf(old).isEmpty(), "each request's resolver");
        assertTrue(colony.isDirty());
    }

    @Test
    void thePlayerAndRetryingResolversAreNewAndOnlyTheNewOnesServe() {
        Resolver retrying = colony.requests().resolver(RetryingResolver.ID).orElseThrow();
        Resolver player = colony.requests().resolver(PlayerResolver.ID).orElseThrow();

        RequestSystemReset.reset(colony);

        Resolver newRetrying = colony.requests().resolver(RetryingResolver.ID).orElseThrow();
        assertNotSame(retrying, newRetrying);
        assertNotSame(player, colony.requests().resolver(PlayerResolver.ID).orElseThrow());
        RequestToken again = request();
        assertSame(newRetrying, colony.requests().resolverOf(again).orElseThrow(), "not the forgotten one");
    }

    @Test
    void everyHutIsAProviderAgain() {
        Resolver own = hut.resolvers().getFirst();

        RequestSystemReset.reset(colony);

        assertEquals(own, colony.requests().resolver(own.resolverId()).orElseThrow());
    }

    @Test
    void aCraftersQueuedAndScheduledTasksGoItsCountersStay() {
        CitizenData farmer = new CitizenData(1);
        FarmerJob job = new FarmerJob(farmer);
        farmer.setJob(job);
        colony.citizens().restore(farmer);
        CraftingTasks tasks = job.craftingTasks();
        tasks.onTaskBeingScheduled(request());
        tasks.onTaskBeingResolved(request());
        tasks.setCraftCounter(3);

        RequestSystemReset.reset(colony);

        assertEquals(0, tasks.load());
        assertEquals(3, tasks.craftCounter(), "MC keeps its counters on the job");
    }

    @Test
    void aCouriersOngoingDeliveriesGo() {
        CitizenData courier = new CitizenData(1);
        DeliverymanJob job = new DeliverymanJob(courier);
        courier.setJob(job);
        colony.citizens().restore(courier);
        job.addConcurrentDelivery(request());

        RequestSystemReset.reset(colony);

        assertTrue(job.ongoingDeliveries().isEmpty());
    }

    @Test
    void theWarehouseQueueKeepsItsTokensAsMc() {
        Building warehouse = Building.create(WarehouseBuilding.TYPE, new BlockPos(10, 64, 0), 0);
        colony.buildings().add(warehouse);
        WarehouseRequestQueue queue =
                warehouse.module(WarehouseRequestQueue.class).orElseThrow();
        RequestToken old = request();
        queue.add(old);

        RequestSystemReset.reset(colony);

        assertEquals(List.of(old), queue.tokens(), "MC keeps it on the building; the courier purges dead tokens");
    }

    @Test
    void aCraftersTaskScheduledBeforeADeferredResetRunsIsForgottenWithIt() {
        CitizenData farmer = new CitizenData(1);
        FarmerJob job = new FarmerJob(farmer);
        farmer.setJob(job);
        colony.citizens().restore(farmer);
        boolean[] once = {false};
        colony.requests().setStateListener((r, from) -> {
            if (!once[0] && r.state() == RequestState.ASSIGNED) {
                once[0] = true;
                RequestSystemReset.reset(colony);
                job.craftingTasks().onTaskBeingScheduled(r.token()); // still before the queued reset
            }
        });

        request();

        assertEquals(0, job.craftingTasks().load(), "forgotten in the same step as the requests");
    }

    @Test
    void aResetAskedWhileTheManagerAssignsRunsOnceItIsFree() {
        boolean[] once = {false};
        // The state listener is called inside a queued step (the assignment), unlike the creation listener.
        colony.requests().setStateListener((r, from) -> {
            if (!once[0] && r.state() == RequestState.ASSIGNED) {
                once[0] = true;
                RequestSystemReset.reset(colony);
            }
        });

        request();

        assertTrue(colony.requests().all().isEmpty(), "queued behind the assignment, then everything forgotten");
        RequestToken again = request();
        assertSame(
                colony.requests().resolver(RetryingResolver.ID).orElseThrow(),
                colony.requests().resolverOf(again).orElseThrow(),
                "the new resolvers serve afterwards");
    }
}
