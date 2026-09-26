package dev.hycolony.core.construction.builder;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.List;

/**
 * Compares the world with the plan (MC StructureIterators + the builder's block checks): which planned positions
 * still need work, and which blocks the builder may break.
 */
final class StructureScan {
    private final Colony colony;
    private final WorldBlocks blocks;
    private final ItemCatalog catalog;

    StructureScan(Colony colony, WorldBlocks blocks, ItemCatalog catalog) {
        this.colony = colony;
        this.blocks = blocks;
        this.catalog = catalog;
    }

    /** The first index from {@code from} and below {@code limit} whose position needs work; {@code limit} if none. */
    int firstNeedingWork(BuildSite site, Stage stage, int from, int limit) {
        List<BlockPos> positions = site.positions(stage);
        int i = from;
        while (i < limit && !needsWork(site, stage, i, positions.get(i))) {
            i++;
        }
        return i;
    }

    static Stage nextStage(Stage stage) {
        return switch (stage) {
            case CLEAR -> Stage.SOLID;
            case SOLID -> Stage.DECORATE;
            default -> Stage.DONE;
        };
    }

    /** CLEAR: a block the plan does not want there; REMOVE: any block; SOLID/DECORATE: not yet as planned. */
    private boolean needsWork(BuildSite site, Stage stage, int i, BlockPos pos) {
        BlockState world = blocks.get(pos).orElse(null);
        return switch (stage) {
            case CLEAR ->
                world != null && clearable(world) && !world.equals(site.plan().stateAt(pos)) && notAHut(pos);
            case REMOVE -> world != null && mineable(world) && notAHut(pos);
            default -> {
                BlueprintEntry e = site.entry(stage, i);
                // The final walk only refills what was broken (air); a block the player changed stays.
                boolean open = site.finalCheckDone()
                        ? world == null || catalog.kind(world.key()) == BlockKind.AIR
                        : world == null || catalog.kind(world.key()) != BlockKind.UNBREAKABLE;
                yield !e.state().equals(world)
                        && open
                        && notAHut(pos); // MC IBuilderUndestroyable: a colony hut is never built over
            }
        };
    }

    /** Air, fluids and unbreakable blocks are never mined. */
    boolean mineable(BlockState state) {
        BlockKind kind = catalog.kind(state.key());
        return kind != BlockKind.AIR && kind != BlockKind.FLUID && kind != BlockKind.UNBREAKABLE;
    }

    /** CLEAR also removes fluids the plan does not want (MC clears the footprint of water and lava). */
    boolean clearable(BlockState state) {
        return mineable(state) || catalog.kind(state.key()) == BlockKind.FLUID;
    }

    boolean mustMineFirst(BlockPos pos) {
        BlockState world = blocks.get(pos).orElse(null);
        return world != null && mineable(world) && notAHut(pos);
    }

    private boolean notAHut(BlockPos pos) {
        return colony.buildings().at(pos).isEmpty();
    }
}
