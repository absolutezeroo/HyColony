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
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    /** Residence plans by level. */
    private final Map<Integer, Blueprint> plans = new HashMap<>();

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
                        ? Optional.ofNullable(plans.get(level))
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

            @Override
            public List<BlockKey> fillBlockChoices() {
                return List.of(DIRT, GRAVEL);
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

    /** Level 1: stone at (1,0,0); air at (2,0,0); fill at (1,-1,0) and (2,-1,0); fluid at (3,0,0); the rest absent. */
    private void mineColoniesPlan() {
        plans.put(1, mineColonies());
    }

    private static Blueprint mineColonies() {
        return new Blueprint(
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
        run(WorkOrderType.BUILD);
    }

    private void run(WorkOrderType type) {
        colony.work().request(alice, RES, type, "", Optional.of(HUT));
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

    /** The residence built at level 1 from a plain plan with stone on every cell the MineColonies level uses. */
    private void builtPlainLevelThenMineColoniesLevel() {
        List<BlueprintEntry> stone = List.of(
                new BlueprintEntry(new BlockPos(1, 0, 0), new BlockState(STONE, 0), false),
                new BlueprintEntry(new BlockPos(2, 0, 0), new BlockState(STONE, 0), false),
                new BlueprintEntry(new BlockPos(1, -1, 0), new BlockState(STONE, 0), false),
                new BlueprintEntry(new BlockPos(3, 0, 0), new BlockState(STONE, 0), false),
                new BlueprintEntry(new BlockPos(4, 1, 0), new BlockState(STONE, 0), false));
        plans.put(1, new Blueprint("plain", stone, new BlockPos(0, -1, 0), new BlockPos(4, 1, 0)));
        plans.put(2, mineColonies());
        stone.forEach(e -> put(e.offset().x(), e.offset().y(), e.offset().z(), STONE));
        Building res = colony.buildings().at(RES).orElseThrow();
        res.setLevel(1);
        res.setBuilt(true);
    }

    @Test
    void upgradeToAMineColoniesLevelOnlyClearsItsAirCells() {
        builtPlainLevelThenMineColoniesLevel();

        run(WorkOrderType.UPGRADE);

        assertNull(world(2, 0, 0), "air cell cleared");
        assertEquals(new BlockState(STONE, 0), world(1, -1, 0), "a good floor under a fill cell stays");
        assertEquals(new BlockState(STONE, 0), world(3, 0, 0), "a solid block on a fluid cell stays");
        assertEquals(new BlockState(STONE, 0), world(4, 1, 0), "an absent cell keeps what is there");
        assertEquals(16, citizen.inventory().count(DIRT_I) + 1, "only the hole at (2,-1,0) was filled");
    }

    @Test
    void removalLeavesFillAndFluidCellsAlone() {
        mineColoniesPlan();
        build();
        put(3, 0, 0, STONE); // a player's block on the fluid cell

        run(WorkOrderType.REMOVE);

        assertNull(world(1, 0, 0), "the building's block is removed");
        assertEquals(new BlockState(DIRT, 0), world(2, -1, 0), "the filled ground stays");
        assertEquals(new BlockState(STONE, 0), world(3, 0, 0), "MC skipRemoval skips fluid substitutions");
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
