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
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a builder removes that the plan does not want: a REPAIR's misplaced blocks, an UPGRADE's old leftovers. */
class BuilderCleanupTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos RES = new BlockPos(30, 64, 0);
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final ItemKey STONE_I = new ItemKey("stone_item");
    private static final ItemKey DIRT_I = new ItemKey("dirt_item");

    @TempDir
    Path dir;

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    /** Residence plans by level. */
    private final Map<Integer, Blueprint> plans = new HashMap<>();

    private ColonyManager manager;
    private Colony colony;
    private CitizenData citizen;
    private BodyId body;
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
        };
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.catalog.kinds.put(DIRT, BlockKind.SOLID);
        t.catalog.itemForBlock.put(STONE, STONE_I);
        t.catalog.itemForBlock.put(DIRT, DIRT_I);
        manager = newManager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        Building hut = hut(ConstructionBuildingTypes.BUILDER.id(), HUT, 5);
        citizen = new CitizenData(1);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        body = t.bodies.existing(colony.id(), 1, Vec3.center(HUT));
        ai = new BuilderAI(
                BuilderContext.of(colony, citizen.job().orElseThrow(), body).orElseThrow());
        citizen.inventory().insert(new ItemAmount(STONE_I, 16), t.catalog::maxStack);
    }

    private ColonyManager newManager() {
        ColonyManager m = new ColonyManager(t.context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp3b());
        return m;
    }

    private Building hut(String type, BlockPos pos, int level) {
        manager.huts().place(colony, type, pos, 0);
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        b.setBuilt(level > 0);
        return b;
    }

    /** The server stops and starts again: save, load, a new AI for the loaded builder. */
    private void restart() {
        int id = colony.id();
        manager.persistence().saveAll();
        manager = newManager();
        manager.persistence().loadAll();
        colony = manager.byId(id).orElseThrow();
        citizen = colony.citizens().get(1).orElseThrow();
        ai = new BuilderAI(
                BuilderContext.of(colony, citizen.job().orElseThrow(), body).orElseThrow());
    }

    private static Blueprint bp(BlockPos max, BlueprintEntry... entries) {
        return new Blueprint("bp", List.of(entries), new BlockPos(0, 0, 0), max);
    }

    private static BlueprintEntry entry(int x, int y, int z, BlockKey key) {
        return new BlueprintEntry(new BlockPos(x, y, z), new BlockState(key, 0), false);
    }

    private WorkOrder order(WorkOrderType type) {
        colony.work().request(alice, RES, type, "", Optional.of(HUT));
        return colony.work().byBuilding(RES).orElseThrow();
    }

    private void tickUntil(BooleanSupplier done) {
        for (int i = 0; i < 10_000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "not reached; state " + ai.stateName());
        assertTrue(ai.lastError().isEmpty());
    }

    private boolean finished() {
        return colony.work().byBuilding(RES).isEmpty();
    }

    private BlockState world(int dx, int dy, int dz) {
        return t.blocks.blocks.get(RES.offset(dx, dy, dz));
    }

    private void put(int dx, int dy, int dz, BlockKey key) {
        t.blocks.blocks.put(RES.offset(dx, dy, dz), new BlockState(key, 0));
    }

    @Test
    void repairRemovesPlayerBlocksThePlanDoesNotWantAndRepairsThePlan() {
        hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(1, bp(new BlockPos(3, 1, 0), entry(1, 0, 0, STONE), entry(2, 0, 0, STONE)));
        put(1, 0, 0, STONE);
        put(3, 1, 0, DIRT); // a player's block where the plan wants nothing
        t.blocks.drops.put(RES.offset(3, 1, 0), List.of(new ItemAmount(DIRT_I, 1)));
        WorkOrder o = order(WorkOrderType.REPAIR);
        assertEquals(Stage.CLEAR, o.stage());

        tickUntil(this::finished);

        assertNull(world(3, 1, 0));
        assertEquals(new BlockState(STONE, 0), world(2, 0, 0));
        assertEquals(new BlockState(STONE, 0), world(1, 0, 0));
        assertEquals(1, citizen.inventory().count(DIRT_I)); // dumped into the hut later
    }

    @Test
    void repairResumesItsClearingAfterARestart() {
        hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(1, bp(new BlockPos(3, 1, 0), entry(1, 0, 0, STONE)));
        put(3, 1, 0, DIRT);
        put(2, 1, 0, DIRT);
        WorkOrder o = order(WorkOrderType.REPAIR);
        tickUntil(() -> world(3, 1, 0) == null || world(2, 1, 0) == null);
        assertEquals(Stage.CLEAR, o.stage());

        restart();
        assertEquals(Stage.CLEAR, colony.work().byBuilding(RES).orElseThrow().stage());
        tickUntil(this::finished);

        assertNull(world(3, 1, 0));
        assertNull(world(2, 1, 0));
        assertEquals(new BlockState(STONE, 0), world(1, 0, 0));
    }

    /** Level 1 built: stone at x 1..3, and a stone roof at (3, 1, 0) that level 2 no longer has. */
    private void builtLevelOneThenUpgradeDropsTheRoof() {
        hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(
                1,
                bp(
                        new BlockPos(3, 1, 0),
                        entry(1, 0, 0, STONE),
                        entry(2, 0, 0, STONE),
                        entry(3, 0, 0, STONE),
                        entry(3, 1, 0, STONE)));
        plans.put(2, bp(new BlockPos(3, 0, 0), entry(1, 0, 0, STONE), entry(2, 0, 0, STONE), entry(3, 0, 0, STONE)));
        put(1, 0, 0, STONE);
        put(2, 0, 0, STONE);
        put(3, 0, 0, STONE);
        put(3, 1, 0, STONE);
    }

    @Test
    void upgradeRemovesOldLevelBlocksTheNewPlanDoesNotWant() {
        builtLevelOneThenUpgradeDropsTheRoof();
        t.blocks.drops.put(RES.offset(3, 1, 0), List.of(new ItemAmount(STONE_I, 1)));

        order(WorkOrderType.UPGRADE);
        tickUntil(this::finished);

        assertNull(world(3, 1, 0));
        assertEquals(17, citizen.inventory().count(STONE_I)); // the drop joins the 16 given
    }

    @Test
    void upgradeKeepsTheOldFloorOutsideTheNewFootprint() {
        hut(ConstructionBuildingTypes.RESIDENCE.id(), RES, 1);
        plans.put(1, bp(new BlockPos(4, 0, 0), entry(1, 0, 0, STONE), entry(4, -1, 0, STONE)));
        plans.put(2, bp(new BlockPos(1, 0, 0), entry(1, 0, 0, STONE)));
        put(1, 0, 0, STONE);
        put(4, -1, 0, STONE); // the old level's floor, sticking out of the smaller new footprint

        order(WorkOrderType.UPGRADE);
        tickUntil(this::finished);

        assertEquals(new BlockState(STONE, 0), world(4, -1, 0), "no trench where the old floor was");
    }

    @Test
    void upgradeKeepsOldBlocksTheNewPlanReuses() {
        builtLevelOneThenUpgradeDropsTheRoof();

        order(WorkOrderType.UPGRADE);
        tickUntil(this::finished);

        assertEquals(new BlockState(STONE, 0), world(1, 0, 0));
        assertEquals(new BlockState(STONE, 0), world(2, 0, 0));
        assertEquals(new BlockState(STONE, 0), world(3, 0, 0));
        assertTrue(t.blocks.placed.isEmpty(), "nothing broken, nothing placed again");
    }

    @Test
    void upgradeLeavesPlayerBlocksThatAreNotFromTheOldPlan() {
        builtLevelOneThenUpgradeDropsTheRoof();
        put(3, 1, 0, DIRT); // the player swapped the old roof
        put(0, 1, 0, DIRT); // and added a block the old plan never had

        order(WorkOrderType.UPGRADE);
        tickUntil(this::finished);

        assertEquals(new BlockState(DIRT, 0), world(3, 1, 0));
        assertEquals(new BlockState(DIRT, 0), world(0, 1, 0));
    }

    @Test
    void upgradeResumesItsLeftoverRemovalAfterARestart() {
        builtLevelOneThenUpgradeDropsTheRoof();
        plans.put(2, bp(new BlockPos(3, 0, 0), entry(1, 0, 0, STONE), entry(2, 0, 0, STONE)));
        WorkOrder o = order(WorkOrderType.UPGRADE);
        tickUntil(() -> world(3, 1, 0) == null);
        assertEquals(Stage.CLEAR_LEFTOVERS, o.stage());
        assertEquals(new BlockState(STONE, 0), world(3, 0, 0)); // removed top down: next

        restart();
        assertEquals(
                Stage.CLEAR_LEFTOVERS,
                colony.work().byBuilding(RES).orElseThrow().stage());
        tickUntil(this::finished);

        assertNull(world(3, 0, 0));
        assertEquals(new BlockState(STONE, 0), world(2, 0, 0));
    }
}
