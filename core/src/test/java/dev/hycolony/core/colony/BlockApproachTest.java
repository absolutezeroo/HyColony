package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC walkCloseToXNearY's end node (PathJobMoveCloseToXNearY): beside the block, towards the building's centre. */
class BlockApproachTest {
    private static final BuildingType TYPE = new BuildingType("test:shed", "test:shed", 5, List.of());
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final BlockPos NORTH = HUT.offset(0, 0, -1), EAST = HUT.offset(1, 0, 0);
    private static final BlockPos SOUTH = HUT.offset(0, 0, 1), WEST = HUT.offset(-1, 0, 0);
    private static final BlockKey FIRE = new BlockKey("test:fire");
    private static final BlockKey WATER = new BlockKey("test:water");
    private static final BlockKey STATUE = new BlockKey("test:statue");

    private final TestContexts t = new TestContexts();
    private final FakeBlueprints blueprints = new FakeBlueprints();
    private final Building shed = Building.create(TYPE, HUT, 0);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(30, 64, 0));
    private final BodyWalker walker = new BodyWalker(t.bodies, body, () -> t.clock.tick);
    private final BlockApproach approach;

    BlockApproachTest() {
        FakeBlueprints.registerBlocks(t.catalog);
        t.catalog.kinds.put(FIRE, BlockKind.NON_SOLID);
        t.catalog.harmful.add(FIRE);
        t.catalog.kinds.put(WATER, BlockKind.FLUID);
        t.catalog.kinds.put(STATUE, BlockKind.UNBREAKABLE);
        // The building spreads east of its hut: x -1..5, so its centre is 2 blocks east.
        blueprints.put(TYPE.id(), 1, new Blueprint("shed", List.of(), new BlockPos(-1, 0, -1), new BlockPos(5, 3, 1)));
        t.blueprints = blueprints;
        shed.setLevel(1);
        shed.setStyle(FakeBlueprints.STYLE);
        floor();
        approach = new BlockApproach(t.context().ports(), walker);
    }

    private void floor() {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                put(HUT.offset(x, -1, z), FakeBlueprints.PLANKS);
            }
        }
    }

    private void put(BlockPos at, BlockKey block) {
        t.blocks.blocks.put(at, FakeBlueprints.state(block));
    }

    /** Where a walk to the shed heads: the target of the body's last move. */
    private Vec3 walkToShed() {
        approach.walkToBuilding(shed);
        return t.bodies.moves.getLast();
    }

    @Test
    void standsBesideTheHutOnTheSideOfTheBuildingCentre() {
        assertEquals(Vec3.center(EAST), walkToShed());
    }

    @Test
    void sideWithAWallOrNoFloorIsSkipped() {
        put(EAST, FakeBlueprints.PLANKS);
        t.blocks.blocks.remove(NORTH.offset(0, -1, 0));

        assertEquals(Vec3.center(SOUTH), walkToShed());
    }

    @Test
    void sideNextToFireIsSkipped() {
        put(EAST.offset(1, 0, 0), FIRE);

        assertEquals(Vec3.center(NORTH), walkToShed());
    }

    @Test
    void sideWithUnbreakableFurnitureIsSkipped() {
        put(EAST.offset(0, 1, 0), STATUE);

        assertEquals(Vec3.center(NORTH), walkToShed());
    }

    @Test
    void waterSideLosesToADrySideFartherFromTheCentre() {
        put(EAST, WATER);

        assertEquals(Vec3.center(NORTH), walkToShed());
    }

    @Test
    void waterSideStillBeatsNoSideAtAll() {
        put(EAST, WATER);
        List.of(NORTH, SOUTH, WEST).forEach(side -> put(side, FakeBlueprints.PLANKS));

        assertEquals(Vec3.center(EAST), walkToShed());
    }

    @Test
    void hutWithoutAFreeSideIsWalkedToItself() {
        List.of(NORTH, EAST, SOUTH, WEST).forEach(side -> put(side, FakeBlueprints.PLANKS));

        assertEquals(Vec3.center(HUT), walkToShed());
    }

    @Test
    void unbuiltHutTakesItsCornersFromTheFirstLevel() {
        shed.setLevel(0);

        assertEquals(Vec3.center(EAST), walkToShed());
        assertTrue(blueprints.loads.contains(FakeBlueprints.STYLE + "/test:shed/1"), blueprints.loads.toString());
    }

    @Test
    void uncheckedTargetIsLookedForAgainOnceTheGroundIsThere() {
        t.blocks.blocks.clear();
        assertEquals(Vec3.center(HUT), walkToShed());

        floor();

        assertEquals(Vec3.center(EAST), walkToShed());
    }

    @Test
    void newWalkSeesABlockPlacedOnTheSpot() {
        assertEquals(Vec3.center(EAST), walkToShed());
        walker.walkTo(new BlockPos(20, 64, 0));

        put(EAST, FakeBlueprints.PLANKS);

        assertEquals(Vec3.center(NORTH), walkToShed());
    }

    @Test
    void safeWalkStandsBesideTheBlock() {
        BlockPos rack = new BlockPos(2, 64, 2);
        put(rack, FakeBlueprints.CHEST);

        approach.walkToSafePos(rack);

        assertEquals(Vec3.center(rack.offset(0, 0, -1)), t.bodies.moves.getLast());
    }
}
