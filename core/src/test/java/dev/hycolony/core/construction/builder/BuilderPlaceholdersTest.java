package dev.hycolony.core.construction.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintMarkers;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A MineColonies blueprint's placeholder cells (Structurize substitutions), built by a builder. */
class BuilderPlaceholdersTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final BlockKey GRAVEL = new BlockKey("gravel");
    private static final BlockKey LEAVES = new BlockKey("leaves");
    private static final BlockState WATER = new BlockState(new BlockKey("~fluid:Water_Source"), 0);
    private static final ItemKey STONE_I = new ItemKey("stone_item");
    private static final ItemKey DIRT_I = new ItemKey("dirt_item");
    private static final ItemKey GRAVEL_I = new ItemKey("gravel_item");

    @TempDir
    Path dir;

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private Blueprint plan = new Blueprint("none", List.of(), new BlockPos(0, 0, 0), new BlockPos(0, 0, 0));

    private Colony colony;
    private CitizenData citizen;
    private BuilderAI ai;

    @BeforeEach
    void setUp() {
        t.bodies.instant = true;
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                return buildingTypeId.equals(ConstructionBuildingTypes.RESIDENCE.id())
                        ? Optional.of(plan)
                        : Optional.empty();
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }

            @Override
            public Optional<BlockKey> defaultFillBlock() {
                return Optional.of(DIRT);
            }
        };
        for (BlockKey b : List.of(STONE, DIRT, GRAVEL, LEAVES)) {
            t.catalog.kinds.put(b, BlockKind.SOLID);
        }
        t.catalog.kinds.put(WATER.key(), BlockKind.FLUID);
        t.catalog.notGoodFloor.add(LEAVES);
        t.catalog.itemForBlock.put(STONE, STONE_I);
        t.catalog.itemForBlock.put(DIRT, DIRT_I);
        t.catalog.itemForBlock.put(GRAVEL, GRAVEL_I);
        ColonyManager manager = new ColonyManager(t.context());
        manager.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0);
        Building hut = colony.buildings().at(HUT).orElseThrow();
        hut.setLevel(5);
        hut.setBuilt(true);
        manager.huts().place(colony, ConstructionBuildingTypes.RESIDENCE.id(), RES, 0);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        BodyId body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new BuilderAI(colony, citizen, body);
        citizen.inventory().insert(new ItemAmount(STONE_I, 16), t.catalog::maxStack);
        citizen.inventory().insert(new ItemAmount(DIRT_I, 16), t.catalog::maxStack);
        citizen.inventory().insert(new ItemAmount(GRAVEL_I, 16), t.catalog::maxStack);
    }

    /** Stone at (1,0,0); air at (2,0,0); fill at (1,-1,0) and (2,-1,0); fluid at (3,0,0); the rest absent. */
    private void mineColoniesPlan() {
        plan = new Blueprint(
                "mc",
                List.of(new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(STONE, 0), false)),
                new BlockPos(0, -1, 0),
                new BlockPos(4, 1, 0),
                Optional.of(new BlueprintMarkers(
                        List.of(new BlockPos(2, 0, 0)),
                        List.of(new BlockPos(1, -1, 0), new BlockPos(2, -1, 0)),
                        List.of(new BlueprintEntry(new BlockPos(3, 0, 0), WATER, false)))));
    }

    private void build() {
        colony.work().request(alice, RES, WorkOrderType.BUILD, "", Optional.of(HUT));
        BooleanSupplier done = () -> colony.work().byBuilding(RES).isEmpty();
        for (int i = 0; i < 10_000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "not finished; state " + ai.stateName());
        assertNull(ai.lastError);
    }

    private BlockState world(int dx, int dy, int dz) {
        return t.blocks.blocks.get(RES.offset(dx, dy, dz));
    }

    private void put(int dx, int dy, int dz, BlockKey key) {
        t.blocks.blocks.put(RES.offset(dx, dy, dz), new BlockState(key, 0));
    }

    @Test
    void absentCellsKeepTheTerrainAndAirCellsAreCleared() {
        mineColoniesPlan();
        put(4, 1, 0, STONE); // absent: a substitution, kept
        put(2, 0, 0, STONE); // explicit air: cleared

        build();

        assertEquals(new BlockState(STONE, 0), world(4, 1, 0));
        assertNull(world(2, 0, 0));
        assertEquals(new BlockState(STONE, 0), world(1, 0, 0));
    }

    @Test
    void fillCellKeepsAGoodFloorAndFillsAHoleWithTheDefaultFillBlock() {
        mineColoniesPlan();
        put(1, -1, 0, STONE); // already a good floor: kept, not replaced by dirt

        build();

        assertEquals(new BlockState(STONE, 0), world(1, -1, 0));
        assertEquals(new BlockState(DIRT, 0), world(2, -1, 0));
        assertEquals(15, citizen.inventory().count(DIRT_I), "one dirt for the hole only");
    }

    @Test
    void fillCellReplacesLeavesWithTheHutsFillBlock() {
        mineColoniesPlan();
        colony.buildings()
                .at(HUT)
                .orElseThrow()
                .module(BuilderSettingsModule.class)
                .orElseThrow()
                .setFillBlock(GRAVEL);
        put(1, -1, 0, LEAVES); // MC unsuitable_solid_for_placeholder: not a good floor

        build();

        assertEquals(new BlockState(GRAVEL, 0), world(1, -1, 0));
        assertEquals(new BlockState(GRAVEL, 0), world(2, -1, 0));
    }

    @Test
    void fluidCellGetsWater() {
        mineColoniesPlan();
        build();
        assertEquals(WATER, world(3, 0, 0));
    }

    @Test
    void fluidCellKeepsASolidBlock() {
        mineColoniesPlan();
        put(3, 0, 0, STONE);
        build();
        assertEquals(new BlockState(STONE, 0), world(3, 0, 0));
    }
}
