package dev.hycolony.core.construction.tape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * When the tape goes up and comes down (MC WorkOrderBuilding.onAdded and onRemoved, AbstractBuilding.onUpgradeComplete
 * and onDestroyed).
 */
class ConstructionTapeFlowTest {
    private static final String RESIDENCE = ConstructionBuildingTypes.RESIDENCE.id();
    private static final BlockPos RES = new BlockPos(10, 64, 0);
    /** Level 2's plan reaches one block further on each side than level 1's (FakeBlueprints.hut). */
    private static final Blueprint WIDER = new Blueprint(
            "wider",
            List.of(FakeBlueprints.entry(0, 0, 0, FakeBlueprints.PLANKS)),
            new BlockPos(-2, 0, -2),
            new BlockPos(2, 2, 2));

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager;
    private final Colony colony;

    ConstructionTapeFlowTest() {
        t.blueprints = new FakeBlueprints()
                .put(RESIDENCE, 1, FakeBlueprints.hut(false))
                .put(RESIDENCE, 2, WIDER);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                t.blocks.blocks.put(RES.offset(x, -1, z), FakeBlueprints.state(FakeBlueprints.DIRT));
            }
        }
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        // An employed builder, which any order needs (WorkOrderValidation BUILDER_NECESSARY).
        BlockPos builderHut = new BlockPos(30, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), builderHut, 0, alice);
        Building builder = colony.buildings().at(builderHut).orElseThrow();
        builder.setLevel(5);
        builder.setBuilt(true);
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, citizen);
    }

    private Building residence(int level) {
        manager.huts().place(colony, RESIDENCE, RES, 0, alice);
        Building b = colony.buildings().at(RES).orElseThrow();
        b.setStyle(FakeBlueprints.STYLE);
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private WorkOrder order(WorkOrderType type) {
        return switch (colony.work().request(alice, RES, type, FakeBlueprints.STYLE, Optional.empty())) {
            case Either.Left(var o) -> o;
            case Either.Right(var refusal) -> throw new AssertionError("refused: " + refusal);
        };
    }

    private long tapes() {
        return t.blocks.blocks.values().stream()
                .filter(s -> t.tape.isTape(s.key()))
                .count();
    }

    @Test
    void anOrderTapesItsSite() {
        residence(0);

        order(WorkOrderType.BUILD);

        assertEquals(16, tapes(), "the border of the level 1 plan widened by one");
    }

    @Test
    void aCancelledOrderTakesItsTapeDown() {
        residence(0);
        WorkOrder o = order(WorkOrderType.BUILD);

        colony.work().cancel(o.id());

        assertEquals(0, tapes());
    }

    @Test
    void aFinishedUpgradeTakesDownTheTapeOfTheOldPlanAndOfTheNewOne() {
        Building b = residence(1);
        WorkOrder o = order(WorkOrderType.UPGRADE);
        assertEquals(16, tapes());
        BlockState stray = new BlockState(t.tape.block(TapeShape.STRAIGHT).orElseThrow(), 0);
        t.blocks.blocks.put(RES.offset(-3, 0, 0), stray); // on the level 2 plan's border

        colony.work().finish(o, b);

        assertEquals(0, tapes());
    }

    @Test
    void aRemovedBuildingTakesItsTapeDown() {
        residence(0);
        order(WorkOrderType.BUILD);

        manager.huts().onRemoved(RES, alice);

        assertEquals(0, tapes());
    }
}
