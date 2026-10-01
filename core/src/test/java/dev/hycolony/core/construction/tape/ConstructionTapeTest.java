package dev.hycolony.core.construction.tape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.tape.FakeTapeBlocks;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Placing and removing the tape around a site (MC ConstructionTapeHelper place/removeConstructionTape). */
class ConstructionTapeTest {
    private static final BlockState STONE = new BlockState(new BlockKey("Rock_Stone"), 0);
    private static final HutFootprint.Box BOX = new HutFootprint.Box(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2));

    private final TestContexts t = new TestContexts();
    private final Colony colony;

    ConstructionTapeTest() {
        for (TapeShape shape : TapeShape.values()) {
            t.catalog.kinds.put(FakeTapeBlocks.key(shape), BlockKind.NON_SOLID);
        }
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                t.blocks.blocks.put(new BlockPos(x, 63, z), STONE);
            }
        }
        TerritoryIndex territory = new TerritoryIndex();
        BlockPos center = new BlockPos(0, 64, 0);
        territory.claimSquare(1, ClaimCell.of(center), 4);
        colony = new Colony(
                t.context(),
                territory,
                new Colony.Founding(1, "Test", center, Permissions.createDefault(UUID.randomUUID(), "A")));
    }

    private long tapes() {
        return t.blocks.blocks.values().stream()
                .filter(s -> s.key().id().startsWith("Tape_"))
                .count();
    }

    @Test
    void placeTapesTheBorderAroundTheSite() {
        ConstructionTape.place(colony, BOX);

        assertEquals(16, tapes());
        assertEquals(
                new BlockState(FakeTapeBlocks.key(TapeShape.CORNER), 3), t.blocks.blocks.get(new BlockPos(-1, 64, -1)));
        assertEquals(
                new BlockState(FakeTapeBlocks.key(TapeShape.STRAIGHT), 1),
                t.blocks.blocks.get(new BlockPos(0, 64, -1)));
    }

    @Test
    void removeTakesTheFirstTapeOfEachBorderColumnFromFiveBelowToOneAbove() {
        ConstructionTape.place(colony, BOX);
        BlockState straight = new BlockState(FakeTapeBlocks.key(TapeShape.STRAIGHT), 0);
        t.blocks.blocks.put(new BlockPos(-1, 58, 1), straight); // under the reach (64 - 5)
        t.blocks.blocks.put(new BlockPos(-1, 68, 2), straight); // over the reach (66 + 1)
        t.blocks.blocks.put(new BlockPos(1, 64, 3), STONE); // not a tape

        ConstructionTape.remove(colony, BOX);

        assertEquals(2, tapes());
        assertEquals(STONE, t.blocks.blocks.get(new BlockPos(1, 64, 3)));
    }
}
