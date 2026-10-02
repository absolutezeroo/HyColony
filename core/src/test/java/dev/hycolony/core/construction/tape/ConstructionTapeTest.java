package dev.hycolony.core.construction.tape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonySettings;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.tape.FakeTapeBlocks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Placing and removing the tape around a site (MC ConstructionTapeHelper place/removeConstructionTape). */
class ConstructionTapeTest {
    private static final BlockState STONE = new BlockState(new BlockKey("Rock_Stone"), 0);
    private static final HutFootprint.Box BOX = new HutFootprint.Box(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2));

    private final TestContexts t = new TestContexts();
    private final Colony colony;

    ConstructionTapeTest() {
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
    void removeVisitsACornerColumnThreeTimesAsMcDoes() {
        // MC removeConstructionTape walks the X edges, the Z edges, then the four corners: up to three tapes each.
        BlockState corner = new BlockState(FakeTapeBlocks.key(TapeShape.CORNER), 0);
        for (int y = 63; y <= 66; y++) {
            t.blocks.blocks.put(new BlockPos(-1, y, -1), corner);
        }

        ConstructionTape.remove(colony, BOX);

        assertEquals(1, tapes(), "the fourth tape of the column stays");
    }

    @Test
    void aTapeInAnUnloadedColumnIsLeftWhereItIs() {
        BlockPos far = new BlockPos(0, 64, -1);
        t.blocks.blocks.put(far, new BlockState(FakeTapeBlocks.key(TapeShape.STRAIGHT), 1));
        t.blocks.unloaded.add(far);

        ConstructionTape.remove(colony, BOX);

        t.blocks.unloaded.clear();
        assertEquals(1, tapes(), "a port loads nothing (CLAUDE.md § 4); the player breaks it in one hit");
    }

    @Test
    void aColonyThatTurnedTheTapeOffGetsNone() {
        colony.settings().set(ColonySettings.Toggle.CONSTRUCTION_TAPE, false);

        ConstructionTape.place(colony, BOX);

        assertEquals(0, tapes());
    }

    @Test
    void removeTakesTheFirstTapeOfAColumnFromFiveBelowToOneAbove() {
        // One edge column per case (each visited once): the reach is 59..67 for a footprint from 64 to 66.
        BlockState straight = new BlockState(FakeTapeBlocks.key(TapeShape.STRAIGHT), 0);
        BlockPos lowest = new BlockPos(0, 59, -1);
        BlockPos under = new BlockPos(1, 58, -1);
        BlockPos highest = new BlockPos(2, 67, -1);
        BlockPos over = new BlockPos(0, 68, 3);
        BlockPos first = new BlockPos(1, 60, 3);
        BlockPos second = new BlockPos(1, 61, 3);
        BlockPos stone = new BlockPos(2, 64, 3);
        for (BlockPos p : List.of(lowest, under, highest, over, first, second)) {
            t.blocks.blocks.put(p, straight);
        }
        t.blocks.blocks.put(stone, STONE);

        ConstructionTape.remove(colony, BOX);

        assertEquals(
                Set.of(under, over, second),
                t.blocks.blocks.entrySet().stream()
                        .filter(e -> t.tape.isTape(e.getValue().key()))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()),
                "out of reach, or the second tape of a column, stays");
        assertEquals(STONE, t.blocks.blocks.get(stone));
    }
}
