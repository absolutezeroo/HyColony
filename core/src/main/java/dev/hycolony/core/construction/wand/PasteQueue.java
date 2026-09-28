package dev.hycolony.core.construction.wand;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/**
 * The creative pastes still to place, in memory only (ST Manager.onWorldTick and PlaceStructureOperation): only the
 * head advances, by at most Structurize {@code maxOperationsPerTick} world changes per tick. A paste first breaks what
 * its plan leaves empty (ST CreativeStructureHandler.allowReplace, drops ignored), then places its solid blocks, then
 * its decorations and fluids, each bottom up, all quietly (no particles nor sound, as ST). A placed block with a
 * container joins the hut's building (MC AbstractBuildingContainer.registerBlockPosition).
 *
 * <p>Deviation from MC: a pasted chest is empty, since our plans carry no container contents (ST
 * ContainerPlacementHandler pastes them); no entity phase, our prefabs have no entities.
 */
final class PasteQueue {
    private static final System.Logger LOG = System.getLogger(PasteQueue.class.getName());
    private static final int CLEAR = 0;
    private static final int SOLID = 1;
    private static final int DECO = 2;
    private static final int DONE = DECO + 1;

    private final ColonyManager manager;
    private final ArrayDeque<StructurePlan> queue = new ArrayDeque<>();
    private int phase = CLEAR;
    private int index;
    private boolean warned;

    PasteQueue(ColonyManager manager) {
        this.manager = manager;
    }

    /** Queues {@code plan} behind the pastes already waiting. */
    void add(StructurePlan plan) {
        queue.add(plan);
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    /** One core tick: advances the head paste, then the next ones, until the tick's budget is spent. */
    void tick() {
        int budget = manager.context().config().structurize().maxOperationsPerTick();
        while (budget > 0 && !queue.isEmpty()) {
            if (step(queue.getFirst())) {
                budget--;
            } else {
                queue.removeFirst();
                phase = CLEAR;
                index = 0;
            }
        }
    }

    /** Does the next operation of {@code plan}; false once it has none left. */
    private boolean step(StructurePlan plan) {
        while (phase < DONE) {
            if (phase == CLEAR ? clearNext(plan) : placeNext(plan)) {
                return true;
            }
            phase++;
            index = 0;
        }
        return false;
    }

    /** Breaks the next position the plan leaves empty; false once the box is clear. */
    private boolean clearNext(StructurePlan plan) {
        List<BlockPos> box = plan.clearList();
        while (index < box.size()) {
            BlockPos pos = box.get(index++);
            if (plan.stateAt(pos) == null) {
                blocks().breakQuietly(pos); // creative: the drops are not kept
                return true;
            }
        }
        return false;
    }

    /** Places the next entry of the current list; false once the list is done. */
    private boolean placeNext(StructurePlan plan) {
        List<BlueprintEntry> list = phase == SOLID ? plan.solidList() : plan.decoList();
        if (index >= list.size()) {
            return false;
        }
        BlockPos pos = (phase == SOLID ? plan.solidPositions() : plan.decoPositions()).get(index);
        BlueprintEntry e = list.get(index++);
        if (blocks().get(pos).filter(e.state()::equals).isPresent()) {
            return true; // ST StructurePlacer: a block already matching is left as is (a chest keeps its items)
        }
        if (!blocks().placeQuietly(pos, e.state(), e.hasContainer())) {
            skipped(pos, e);
            return true;
        }
        if (e.hasContainer()) {
            Optional<Colony> colony = manager.colonyAt(plan.hut());
            colony.flatMap(c -> c.buildings().at(plan.hut())).ifPresent(b -> {
                b.registeredBlocks().addContainer(pos);
                colony.get().markDirty();
            });
        }
        return true;
    }

    /** An unloaded section or a refused block: skipped, never retried, so a paste always ends. */
    private void skipped(BlockPos pos, BlueprintEntry e) {
        System.Logger.Level level = warned ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING;
        warned = true;
        LOG.log(
                level,
                "Paste: failed to place {0} at {1}; skipped",
                e.state().key().id(),
                pos);
    }

    private WorldBlocks blocks() {
        return manager.context().ports().blocks();
    }
}
