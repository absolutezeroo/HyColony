package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * A crafting bench of the plan: the builder pays it with its Hytale upgrades, places it at its planned tier and the
 * hut registers it (MC registerBlockPosition); a bench the builder mines leaves the hut.
 */
class BuilderPlacesWorkstationTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final String FARMING = "Farmingbench";
    private static final BlockKey BENCH = new BlockKey("Bench_Farming");
    private static final ItemKey BENCH_I = new ItemKey("Bench_Farming");
    private static final ItemKey A = new ItemKey("Ingredient_A");
    private static final ItemKey B = new ItemKey("Ingredient_B");

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    /** Residence plans by level. */
    private final Map<Integer, Blueprint> plans = new HashMap<>();

    private final ColonyManager manager;
    private final Colony colony;
    private final Building builderHut;
    private final CitizenData citizen;
    private final BuilderAI ai;

    BuilderPlacesWorkstationTest() {
        t.bodies.instant = true;
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                return Optional.ofNullable(plans.get(level));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        t.catalog.kinds.put(BENCH, BlockKind.SOLID);
        t.catalog.itemForBlock.put(BENCH, BENCH_I);
        t.recipes.benchItems.put(FARMING, BENCH_I);
        t.recipes.upgradeCosts.put(FARMING + ":2", List.of(new ItemAmount(A, 5)));
        t.recipes.upgradeCosts.put(FARMING + ":3", List.of(new ItemAmount(B, 8)));

        manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        builderHut = hut(ConstructionBuildingTypes.BUILDER.id(), HUT, 5);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(builderHut.module(WorkerModule.class).orElseThrow().hire(colony, builderHut, citizen));
        ai = new BuilderAI(BuilderContext.of(colony, citizen, t.bodies.existing(colony.id(), 1, Vec3.center(HUT)))
                .orElseThrow());
    }

    private Building hut(String type, BlockPos pos, int level) {
        manager.huts().place(colony, type, pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private static BlueprintEntry bench(int x, int tier) {
        return new BlueprintEntry(
                new BlockPos(x, 0, 0), new BlockState(BENCH, 0), false, Optional.of(new Workstation(FARMING, tier)));
    }

    private static Blueprint plan(BlueprintEntry... entries) {
        return new Blueprint("bp", List.of(entries), new BlockPos(0, 0, 0), new BlockPos(2, 0, 0));
    }

    private WorkOrder order(WorkOrderType type) {
        colony.work().request(alice, RES, type, "", Optional.of(HUT));
        return colony.work().byBuilding(RES).orElseThrow();
    }

    private void give(ItemKey item, int n) {
        citizen.inventory().insert(new ItemAmount(item, n), t.catalog::maxStack);
    }

    private void tickUntil(BooleanSupplier done) {
        for (int i = 0; i < 5000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "not reached; state " + ai.stateName());
        assertTrue(ai.lastError().isEmpty());
    }

    private void tick(int n) {
        for (int i = 0; i < n; i++) {
            t.clock.tick++;
            ai.tick();
        }
    }

    private boolean finished() {
        return colony.work().byBuilding(RES).isEmpty();
    }

    /** The builder's own (sync) request for {@code item}, the one it waits for in NEEDS_ITEM. */
    private boolean waitsFor(ItemKey item) {
        return colony.requests().byRequester(builderHut.requesterId()).stream()
                .filter(r -> r.citizenId() == citizen.id())
                .map(Request::requestable)
                .anyMatch(r -> r instanceof StackRequest s && s.item().equals(item));
    }

    @Test
    void builderWaitsForEveryUpgradeItem() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        plans.put(1, plan(bench(1, 3)));
        give(BENCH_I, 1);
        give(A, 5);
        order(WorkOrderType.BUILD);

        tickUntil(() -> waitsFor(B));
        tick(1000);

        assertEquals("NEEDS_ITEM", ai.stateName());
        assertFalse(t.blocks.blocks.containsKey(RES.offset(1, 0, 0)));
        assertTrue(res.registeredBlocks().workstations().isEmpty());
        assertEquals(1, citizen.inventory().count(BENCH_I));
        assertEquals(5, citizen.inventory().count(A));
        assertFalse(waitsFor(BENCH_I) || waitsFor(A));
    }

    @Test
    void builderAsksForTheRestOfAnUpgradeItemItHoldsTooFewOf() {
        hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        plans.put(1, plan(bench(1, 3)));
        give(BENCH_I, 1);
        give(A, 5);
        give(B, 3);
        order(WorkOrderType.BUILD);

        tickUntil(() -> waitsFor(B));
        tick(1000);

        assertEquals("NEEDS_ITEM", ai.stateName());
        assertFalse(t.blocks.blocks.containsKey(RES.offset(1, 0, 0)));
        assertEquals(3, citizen.inventory().count(B));
    }

    @Test
    void placedBenchGetsItsTierAndJoinsTheHut() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        plans.put(1, plan(bench(1, 3)));
        give(BENCH_I, 1);
        give(A, 5);
        give(B, 8);
        order(WorkOrderType.BUILD);

        tickUntil(this::finished);

        BlockPos at = RES.offset(1, 0, 0);
        assertEquals(new BlockState(BENCH, 0), t.blocks.blocks.get(at));
        assertEquals(3, t.blocks.benchTiers.get(at));
        assertEquals(
                Map.of(at, new Workstation(FARMING, 3)), res.registeredBlocks().workstations());
        assertEquals(0, citizen.inventory().count(BENCH_I));
        assertEquals(0, citizen.inventory().count(A));
        assertEquals(0, citizen.inventory().count(B));
    }

    @Test
    void benchWhoseTierTheWorldRefusesIsStillRegisteredAtItsPlannedTier() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        plans.put(1, plan(bench(1, 2)));
        t.blocks.refuseBenchTier = true;
        give(BENCH_I, 1);
        give(A, 5);
        order(WorkOrderType.BUILD);

        tickUntil(this::finished);

        BlockPos at = RES.offset(1, 0, 0);
        assertEquals(
                Map.of(at, new Workstation(FARMING, 2)), res.registeredBlocks().workstations());
        assertTrue(t.blocks.benchTiers.isEmpty());
    }

    @Test
    void benchOfAFreeOrderCostsNothingAndJoinsTheHut() {
        t.players.creativeOperators.add(alice);
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        plans.put(1, plan(bench(1, 3)));
        assertTrue(order(WorkOrderType.BUILD).free());

        tickUntil(this::finished);

        BlockPos at = RES.offset(1, 0, 0);
        assertEquals(3, t.blocks.benchTiers.get(at));
        assertEquals(
                Map.of(at, new Workstation(FARMING, 3)), res.registeredBlocks().workstations());
        assertTrue(colony.requests().byRequester(builderHut.requesterId()).isEmpty());
    }

    @Test
    void minedBenchLeavesItsHut() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(1, plan(bench(1, 1)));
        BlockPos at = RES.offset(1, 0, 0);
        t.blocks.blocks.put(at, new BlockState(BENCH, 0));
        res.registeredBlocks().addWorkstation(at, new Workstation(FARMING, 1));
        order(WorkOrderType.REMOVE);

        tickUntil(this::finished);

        assertFalse(t.blocks.blocks.containsKey(at));
        assertTrue(res.registeredBlocks().workstations().isEmpty());
    }

    @Test
    void hutUpgradeRegistersTheBenchesOfTheNewPlan() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(1, plan(bench(1, 1)));
        plans.put(2, plan(bench(2, 2))); // the new level moves the bench and raises it to tier 2
        BlockPos old = RES.offset(1, 0, 0);
        BlockPos moved = RES.offset(2, 0, 0);
        t.blocks.blocks.put(old, new BlockState(BENCH, 0));
        res.registeredBlocks().addWorkstation(old, new Workstation(FARMING, 1));
        give(BENCH_I, 1);
        give(A, 5);
        WorkOrder o = order(WorkOrderType.UPGRADE);
        assertEquals(Stage.SOLID, o.stage());

        tickUntil(this::finished);

        assertFalse(t.blocks.blocks.containsKey(old));
        assertEquals(2, t.blocks.benchTiers.get(moved));
        assertEquals(
                Map.of(moved, new Workstation(FARMING, 2)),
                res.registeredBlocks().workstations());
    }
}
