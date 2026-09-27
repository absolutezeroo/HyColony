package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.calculateNextState rain rule and WorkerBuildingModule.canWorkDuringTheRain, on the builder. */
class BuilderRainTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");

    private final TestContexts t = new TestContexts();
    private CitizenAI ai;

    /** A builder at a hut of {@code level} with a claimed order it cannot finish (it lacks the stone). */
    private void start(int level, boolean workersAlwaysWorkInRain) {
        t.bodies.instant = true;
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.itemForBlock.put(STONE, new ItemKey("stone_item"));
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int lvl, int rotation) {
                var stone = new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(STONE, 0), false);
                return Optional.of(new Blueprint("bp", List.of(stone), new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }
        };
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                new ColonyConfig.Gameplay(
                        d.gameplay().initialCitizenAmount(),
                        d.gameplay().maxCitizenPerColony(),
                        workersAlwaysWorkInRain),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony());
        UUID alice = UUID.randomUUID();
        ColonyManager manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "A").orElseThrow();
        Building hut = place(manager, colony, ConstructionBuildingTypes.BUILDER.id(), HUT, level);
        place(manager, colony, ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        CitizenData citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        var r = colony.work().request(alice, RES, WorkOrderType.BUILD, "", Optional.of(HUT));
        assertTrue(r instanceof Either.Left, () -> "refused: " + r);
        ai = new CitizenAI(colony, citizen, t.bodies.existing(colony.id(), 1, Vec3.center(HUT)));
    }

    private static Building place(ColonyManager manager, Colony colony, String type, BlockPos pos, int level) {
        manager.huts().place(colony, type, pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    private boolean tickUntil(CitizenState state, int max) {
        for (int i = 0; i < max && ai.state() != state; i++) {
            t.clock.tick++;
            ai.tick();
        }
        return ai.state() == state;
    }

    @Test
    void builderStopsInTheRain() {
        start(1, false);
        assertTrue(tickUntil(CitizenState.WORKING, 40));

        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.IDLE, 20), "MC: the rain check runs even mid-task");
        assertFalse(tickUntil(CitizenState.WORKING, 420));

        t.world.raining = false;

        assertTrue(tickUntil(CitizenState.WORKING, 420));
    }

    @Test
    void builderWorksInTheRainWhenConfigured() {
        start(1, true);
        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.WORKING, 40));
        tickUntil(CitizenState.IDLE, 420);
        assertEquals(CitizenState.WORKING, ai.state());
    }

    @Test
    void maxLevelBuilderWorksInTheRain() {
        start(ConstructionBuildingTypes.BUILDER.maxLevel(), false);
        t.world.raining = true;

        assertTrue(tickUntil(CitizenState.WORKING, 40)); // MC canWorkDuringTheRain: level >= max level
        tickUntil(CitizenState.IDLE, 420);
        assertEquals(CitizenState.WORKING, ai.state());
    }
}
