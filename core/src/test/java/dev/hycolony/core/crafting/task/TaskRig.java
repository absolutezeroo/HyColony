package dev.hycolony.core.crafting.task;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.request.FakeResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A colony whose request system hands crafting tasks to one {@link CraftingTasks}, the way the hut's crafting
 * resolvers do: a request for seeds is split into {@link Crafting} tasks, each scheduled for the crafter when
 * assigned, queued once its ingredients are there, and dropped from its lists when completed or cancelled.
 */
final class TaskRig {
    static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    static final ItemKey ESSENCE = new ItemKey("Ingredient_Life_Essence");
    /** A task of more runs than this first asks for its essence, so it stays scheduled. */
    static final int RUNS_IN_STOCK = 5;

    final TestContexts t = new TestContexts();
    final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "O")));
    final Building asker =
            Building.create(new BuildingType("test:asker", "hut.asker", 1, List.of()), new BlockPos(20, 64, 0), 0);
    final CraftingTasks tasks = new CraftingTasks();
    final Splitter splitter = new Splitter();
    final Production production = new Production();

    TaskRig() {
        colony.buildings().add(asker);
        colony.requests().registerBuiltIn(splitter);
        colony.requests().registerBuiltIn(production);
    }

    RequestManager requests() {
        return colony.requests();
    }

    /** The asker's request for seeds, which {@link #splitter} answers with one crafting task per batch. */
    RequestToken ask(int... batches) {
        splitter.batches = batches;
        int total = 0;
        for (int runs : batches) {
            total += runs;
        }
        return requests().createAndAssign(asker, new StackRequest(SEEDS, total, total, true), -1);
    }

    /** The crafting request resolver's part: seeds become crafting tasks, while {@link #open}. */
    static final class Splitter extends FakeResolver {
        boolean open = true;
        int[] batches = {};

        Splitter() {
            super("test:splitter", 125);
        }

        @Override
        public boolean handles(Requestable requestable) {
            return requestable instanceof StackRequest s && s.item().equals(SEEDS);
        }

        @Override
        public boolean canResolve(RequestManager m, Request r) {
            return open;
        }

        @Override
        public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
            List<Requestable> children = new ArrayList<>();
            for (int runs : batches) {
                children.add(new Crafting(SEEDS, runs, runs, "hytale:" + SEEDS.id(), true));
            }
            return Optional.of(children);
        }

        @Override
        public void resolve(RequestManager m, Request r) {
            m.updateState(r.token(), RequestState.RESOLVED);
        }
    }

    /** The public production resolver's part (MC PublicWorkerCraftingProductionResolver), for {@link #tasks}. */
    final class Production extends FakeResolver {
        /** Each task this resolver completed. */
        final List<RequestToken> completed = new ArrayList<>();
        /** Each task cancelled, with the state it had then (FAILED when the crafter failed it). */
        final Map<RequestToken, RequestState> cancelled = new LinkedHashMap<>();

        Production() {
            super("test:production", 125);
        }

        @Override
        public boolean handles(Requestable requestable) {
            return requestable instanceof Crafting;
        }

        @Override
        public boolean canResolve(RequestManager m, Request r) {
            return true;
        }

        @Override
        public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
            int runs = ((Crafting) r.requestable()).count();
            return Optional.of(
                    runs <= RUNS_IN_STOCK ? List.of() : List.of(new StackRequest(ESSENCE, 2 * runs, 2 * runs, true)));
        }

        @Override
        public void onAssigned(RequestManager m, Request r) {
            tasks.onTaskBeingScheduled(r.token());
        }

        @Override
        public void resolve(RequestManager m, Request r) {
            tasks.onTaskBeingResolved(r.token());
        }

        @Override
        public List<Requestable> followups(RequestManager m, Request r) {
            tasks.onTaskDeletion(r.token());
            completed.add(r.token());
            return List.of();
        }

        @Override
        public void onCancelling(RequestManager m, Request r) {
            cancelled.put(r.token(), r.state());
        }

        @Override
        public void onCancelled(RequestManager m, Request r) {
            tasks.onTaskDeletion(r.token());
        }
    }
}
