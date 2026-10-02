package dev.hycolony.core.construction.tape;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;

/**
 * The construction tape around a building site (MC ConstructionTapeHelper): placed quietly on the ground around the
 * footprint ({@link TapeLayout}), removed from the same border without drops (MC's tape has no loot table).
 */
public final class ConstructionTape {
    /** MC removeConstructionTape: a tape is looked for from this many blocks under the footprint's bottom. */
    private static final int BELOW = 5;
    /** MC removeConstructionTape: ... up to this many blocks over its top. */
    private static final int ABOVE = 1;

    private ConstructionTape() {}

    /**
     * MC placeConstructionTape(building.getCorners(), colony): around the footprint of the building's level; its plan
     * is not even read while the colony's construction tape setting is off.
     */
    public static void place(Colony colony, Building building) {
        if (colony.settings().constructionTape()) {
            place(colony, HutFootprint.of(colony.context().ports(), building));
        }
    }

    /**
     * MC placeConstructionTape: the tapes of {@link TapeLayout} around {@code box}, where the game has the block;
     * nothing while the colony's construction tape setting is off.
     */
    public static void place(Colony colony, HutFootprint.Box box) {
        if (!colony.settings().constructionTape()) {
            return;
        }
        GamePorts p = colony.context().ports();
        for (Tape tape : TapeLayout.of(box.min(), box.max(), p.blocks(), p.catalog())) {
            p.tape()
                    .block(tape.shape())
                    .ifPresent(key -> p.blocks().placeQuietly(tape.pos(), new BlockState(key, tape.rotation()), false));
        }
    }

    /** MC removeConstructionTape(building.getCorners(), world): around the footprint of the building's level. */
    public static void remove(Colony colony, Building building) {
        remove(colony, HutFootprint.of(colony.context().ports(), building));
    }

    /**
     * MC removeConstructionTape: at each visit of MC's border walk around {@code box}, the lowest tape within reach;
     * a corner column, visited three times, loses up to three.
     *
     * <p>Deviation from MC: MC loads a column's chunk to read it; a column whose chunk is not loaded keeps its tape
     * here (a port loads nothing, CLAUDE.md § 4), as when an order far away is cancelled. Nothing waits on it, and a
     * player breaks it in one hit.
     */
    public static void remove(Colony colony, HutFootprint.Box box) {
        GamePorts p = colony.context().ports();
        int from = Math.min(box.min().y(), box.max().y()) - BELOW;
        int to = Math.max(box.min().y(), box.max().y()) + ABOVE;
        for (BlockPos column : TapeLayout.removalColumns(box.min(), box.max())) {
            for (int y = from; y <= to; y++) {
                BlockPos at = new BlockPos(column.x(), y, column.z());
                if (p.blocks().get(at).filter(s -> p.tape().isTape(s.key())).isPresent()) {
                    p.blocks().breakQuietly(at);
                    break;
                }
            }
        }
    }
}
