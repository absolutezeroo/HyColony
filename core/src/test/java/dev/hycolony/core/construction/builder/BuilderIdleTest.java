package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.calculateNextState and EntityAIStructureBuilder.canGoIdle: an idle builder wanders. */
class BuilderIdleTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final ItemKey STONE_ITEM = new ItemKey("stone_item");

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager;
    private final Colony colony;
    private final CitizenAI ai;
    private final BodyId body;

    BuilderIdleTest() {
        t.bodies.instant = true;
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.itemForBlock.put(STONE, STONE_ITEM);
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                // One stone the builder does not hold: the order stays open while it waits for it.
                var stone = new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(STONE, 0), false);
                return Optional.of(new Blueprint("bp", List.of(stone), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        Building hut = hut(ConstructionBuildingTypes.BUILDER, HUT, 5);
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new CitizenAI(colony, citizen, body);
    }

    private Building hut(BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private WorkOrder claimOrder() {
        Building res = hut(ConstructionBuildingTypes.RESIDENCE, RES, 0);
        var r = colony.work().request(alice, res.position(), WorkOrderType.BUILD, "", Optional.of(HUT));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        return colony.work().byBuilding(RES).orElseThrow();
    }

    private boolean tickUntil(CitizenState state, int max) {
        for (int i = 0; i < max && ai.state() != state; i++) {
            t.clock.tick++;
            ai.tick();
        }
        return ai.state() == state;
    }

    /** Idle, it wanders around (MC EntityAICitizenWander): a walk every 100 ticks at most. */
    private boolean wanders(int max) {
        int moves = t.bodies.moves.size();
        for (int i = 0; i < max && t.bodies.moves.size() == moves; i++) {
            t.clock.tick++;
            ai.tick();
        }
        return ai.state() == CitizenState.IDLE && t.bodies.moves.size() > moves;
    }

    @Test
    void builderWithoutOrderWanders() {
        assertTrue(wanders(120));
    }

    @Test
    void builderWithClaimedOrderWorks() {
        claimOrder();
        assertTrue(tickUntil(CitizenState.WORKING, 40));
        tickUntil(CitizenState.IDLE, 420);
        assertEquals(CitizenState.WORKING, ai.state()); // the claimed order keeps it at work
    }

    @Test
    void orderCreatedWhileWanderingBringsBuilderBackToWork() {
        assertTrue(wanders(120));
        claimOrder();
        assertTrue(tickUntil(CitizenState.WORKING, 40));
    }

    @Test
    void builderReturnsToWanderingWhenItsOrderIsCancelledMidWork() {
        WorkOrder order = claimOrder();
        assertTrue(tickUntil(CitizenState.WORKING, 40));
        t.bodies.bodies.get(body).held = STONE_ITEM;

        colony.work().cancel(order.id());

        assertTrue(tickUntil(CitizenState.IDLE, 40));
        assertNull(t.bodies.bodies.get(body).held, "MC resetAI clears the held item");
        assertTrue(wanders(120), "then wanders like any idle citizen");
    }
}
