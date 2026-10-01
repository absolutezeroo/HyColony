package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/**
 * The creative pastes still to place, in memory only (ST Manager.onWorldTick and PlaceStructureOperation): only the
 * head advances, by at most Structurize {@code maxOperationsPerTick} world changes per tick. A paste first breaks what
 * its plan leaves empty (ST CreativeStructureHandler.allowReplace, drops ignored), then places its solid blocks, then
 * its decorations and fluids, each bottom up, all quietly (no particles nor sound, as ST). A placed block with a
 * container, or a crafting bench, joins the hut's building (MC AbstractBuildingContainer.registerBlockPosition); one
 * found already as planned joins too, as it stands.
 *
 * <p>Deviation from MC: a pasted chest is empty, since our plans carry no container contents (ST
 * ContainerPlacementHandler pastes them); no entity phase, our prefabs have no entities. A pasted bench gets its
 * planned Hytale tier for free, as a creative paste costs nothing (SP3b-1 spec, deviation 3).
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
        if (plan.satisfied(
                e, blocks().get(pos).orElse(null), manager.context().ports().catalog())) {
            // ST StructurePlacer: a block already matching is left as is (a chest keeps its items), yet its container
            // or bench joins the hut, as ST's iterator calls triggerSuccess on it (CreativeBuildingStructureHandler).
            registerFound(plan, pos, e);
            return true;
        }
        if (!blocks().placeQuietly(pos, e.state(), e.hasContainer())) {
            // An unloaded section or a refused block: skipped, never retried, so a paste always ends.
            warn("Paste: failed to place {0} at {1}; skipped", e.state().key().id(), pos);
            return true;
        }
        placed(plan, pos, e);
        return true;
    }

    /** A placed bench gets its planned tier; a placed container or bench joins the hut's building. */
    private void placed(StructurePlan plan, BlockPos pos, BlueprintEntry e) {
        Optional<Workstation> bench = e.workstation();
        if (bench.isPresent() && !blocks().setBenchTier(pos, bench.get().tier())) {
            warn(
                    "Paste: could not set the bench at {0} to tier {1}",
                    pos, bench.get().tier());
        }
        if (e.hasContainer() || bench.isPresent() || isBed(e)) {
            register(plan, pos, e);
        }
    }

    /**
     * The container and bench of a cell found as planned join the hut's building as they stand, the bench at the tier
     * it has, writing nothing (MC CreativeBuildingStructureHandler.triggerSuccess only registers).
     */
    private void registerFound(StructurePlan plan, BlockPos pos, BlueprintEntry e) {
        if (!e.hasContainer() && e.workstation().isEmpty() && !isBed(e)) {
            return;
        }
        Optional<Colony> colony = manager.colonyAt(plan.hut());
        colony.flatMap(c -> c.buildings().at(plan.hut())).ifPresent(b -> {
            if (e.hasContainer()) {
                b.registeredBlocks().addContainer(pos);
            }
            e.workstation()
                    .ifPresent(bench -> b.registeredBlocks().addFoundWorkstation(pos, bench, blocks().benchTier(pos)));
            BuildingEventsModule.blockPlaced(colony.get(), b, pos, e.state().key());
            colony.get().markDirty();
        });
    }

    /**
     * The placed container or bench joins the hut's building, if the paste is a hut of a colony (MC
     * CreativeBuildingStructureHandler.triggerSuccess -> registerBlockPosition). A bench keeps its planned tier.
     */
    private void register(StructurePlan plan, BlockPos pos, BlueprintEntry e) {
        Optional<Colony> colony = manager.colonyAt(plan.hut());
        colony.flatMap(c -> c.buildings().at(plan.hut())).ifPresent(b -> {
            if (e.hasContainer()) {
                b.registeredBlocks().addContainer(pos);
            }
            e.workstation().ifPresent(bench -> b.registeredBlocks().addWorkstation(pos, bench));
            BuildingEventsModule.blockPlaced(colony.get(), b, pos, e.state().key());
            colony.get().markDirty();
        });
    }

    /** A bed the hut's bed module registers (MC BedHandlingModule, through registerBlockPosition). */
    private boolean isBed(BlueprintEntry e) {
        return manager.context().ports().catalog().isBed(e.state().key());
    }

    /** Logs a paste problem: the first one as a warning, the next ones at DEBUG. */
    private void warn(String message, Object... params) {
        LOG.log(warned ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING, message, params);
        warned = true;
    }

    private WorldBlocks blocks() {
        return manager.context().ports().blocks();
    }
}
