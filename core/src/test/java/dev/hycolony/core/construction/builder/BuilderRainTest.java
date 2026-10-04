package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.calculateNextState rain rule and WorkerBuildingModule.canWorkDuringTheRain, on the builder. */
class BuilderRainTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final ItemKey STONE_ITEM = new ItemKey("stone_item");
    private static final int PLAN_SIZE = 3;

    private final TestContexts t = new TestContexts();
    private Colony colony;
    private CitizenData citizen;
    private BodyId body;
    private CitizenAI ai;

    /**
     * A builder at a hut of {@code level} with a claimed order for a row of {@link #PLAN_SIZE} stones, holding
     * {@code stones} of them (fewer: it waits for the rest and never finishes).
     */
    private void start(int level, boolean workersAlwaysWorkInRain, int stones) {
        t.bodies.instant = true;
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.itemForBlock.put(STONE, STONE_ITEM);
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int lvl, int rotation) {
                List<BlueprintEntry> row = new ArrayList<>();
                for (int x = 1; x <= PLAN_SIZE; x++) {
                    row.add(new BlueprintEntry(new BlockPos(x, 0, 0), new BlockState(STONE, 0), false));
                }
                return Optional.of(new Blueprint("bp", row, new BlockPos(0, 0, 0), new BlockPos(PLAN_SIZE, 0, 0)));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        t.config = config(workersAlwaysWorkInRain);
        UUID alice = UUID.randomUUID();
        ColonyManager manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        Building hut = place(manager, ConstructionBuildingTypes.BUILDER.id(), HUT, level);
        place(manager, ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        if (stones > 0) {
            citizen.inventory().insert(new ItemAmount(STONE_ITEM, stones), t.catalog::maxStack);
        }
        var r = colony.work().request(alice, RES, WorkOrderType.BUILD, "", Optional.of(HUT));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new CitizenAI(colony, citizen, body);
    }

    private static ColonyConfig config(boolean workersAlwaysWorkInRain) {
        ColonyConfig d = ColonyConfig.defaults();
        return new ColonyConfig(
                new ColonyConfig.Gameplay(
                        d.gameplay().initialCitizenAmount(),
                        d.gameplay().maxCitizenPerColony(),
                        workersAlwaysWorkInRain,
                        d.gameplay().foodModifier(),
                        d.gameplay().mobAttackCitizens()),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony(),
                d.structurize());
    }

    private Building place(ColonyManager manager, String type, BlockPos pos, int level) {
        manager.huts().place(colony, type, pos, 0, UUID.randomUUID());
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private boolean tickUntil(CitizenState state, int max) {
        return tickUntil(() -> ai.state() == state, max);
    }

    private boolean tickUntil(BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        return done.getAsBoolean();
    }

    /** BUILDING_STEP or MINE_BLOCK: only their activity line names the stage, the block index and the held item. */
    private boolean atABlock() {
        return ai.jobActivity().map(line -> line.params().size() == 3).orElse(false);
    }

    @Test
    void builderStopsInTheRain() {
        start(1, false, PLAN_SIZE);
        WorkOrder order = colony.work().claimedBy(HUT).orElseThrow();
        assertTrue(tickUntil(() -> atABlock() && t.blocks.placed.size() == 1, 2_000));

        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.IDLE, 10), "MC: the rain check runs even mid-task");
        assertTrue(t.blocks.placed.size() < PLAN_SIZE, "stopped before the end of the row");
        Stage stage = order.stage();
        int index = order.progressIndex();
        int placed = t.blocks.placed.size();
        assertFalse(tickUntil(CitizenState.WORKING, 420));
        assertEquals(Optional.of(order), colony.work().claimedBy(HUT), "the hut keeps its order");
        assertEquals(stage, order.stage());
        assertEquals(index, order.progressIndex());
        assertEquals(placed, t.blocks.placed.size());

        t.world.raining = false;

        assertTrue(tickUntil(CitizenState.WORKING, 420));
        Building residence = colony.buildings().at(RES).orElseThrow();
        assertTrue(tickUntil(() -> residence.level() == 1, 5_000), "resumes the same order and finishes it");
        assertEquals(PLAN_SIZE, t.blocks.placed.stream().distinct().count());
    }

    @Test
    void aBuilderStoppedByTheRainKeepsWhatItHolds() {
        start(1, false, PLAN_SIZE);
        assertTrue(tickUntil(() -> atABlock() && t.blocks.placed.size() == 1, 2_000));
        ItemKey held = t.bodies.bodies.get(body).held;
        int slot = citizen.equipment().held(CitizenEquipment.Hand.MAIN);

        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.IDLE, 10));
        assertNotNull(held);
        assertEquals(held, t.bodies.bodies.get(body).held, "MC resetAI leaves the entity's hand");
        assertEquals(slot, citizen.equipment().held(CitizenEquipment.Hand.MAIN));
    }

    @Test
    void workerResumesWithinTenTicksWhenTheRainStopsWhileWandering() {
        start(1, false, 0);
        assertTrue(tickUntil(CitizenState.WORKING, 40));
        t.world.raining = true;
        assertTrue(tickUntil(CitizenState.IDLE, 10));
        t.bodies.frozen = true; // a wander walk that never ends: only the work decision ends the idling
        int moves = t.bodies.moves.size();
        assertTrue(tickUntil(() -> t.bodies.moves.size() > moves, 420), "wanders meanwhile");

        t.world.raining = false;

        assertTrue(tickUntil(CitizenState.WORKING, 10), "MC CitizenAI re-decides every 10 ticks in any state");
    }

    @Test
    void builderWorksInTheRainWhenConfigured() {
        start(1, true, 0);
        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.WORKING, 40));
        tickUntil(CitizenState.IDLE, 420);
        assertEquals(CitizenState.WORKING, ai.state());
    }

    @Test
    void maxLevelBuilderWorksInTheRain() {
        start(ConstructionBuildingTypes.BUILDER.maxLevel(), false, 0);
        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.WORKING, 40)); // MC canWorkDuringTheRain: level >= max level
        tickUntil(CitizenState.IDLE, 420);
        assertEquals(CitizenState.WORKING, ai.state());
    }
}
