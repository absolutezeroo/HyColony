package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.RegisteredBlocks;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Registers what the plan puts on a cell with the hut's building (MC AbstractBuilding.registerBlockPosition): its
 * container, and its bench, at the plan's tier when the builder places it, at the tier it has when found already as
 * planned (nothing is written to the world then). MC registers both: Structurize's iterator calls triggerSuccess on a
 * cell that already matches the plan (AbstractBlueprintIterator.iterateWithCondition l. 109-111), so a warehouse's
 * planned chests fill even when the builder placed none of them.
 */
final class PlannedBlocks {
    private static final System.Logger LOG = System.getLogger(BuilderAI.class.getName());

    private final BuilderContext ctx;
    /** A refused bench tier is logged once as a warning, then at DEBUG. */
    private boolean tierWarned;

    PlannedBlocks(BuilderContext ctx) {
        this.ctx = ctx;
    }

    /**
     * The builder placed {@code e} at {@code pos}: its container and bench join the building, which is told of it, and
     * a field block becomes a field of the colony. MC BlockScarecrow.setPlacedBy takes the colony at the block: an
     * order's plan lay in the builder's colony when the order was made (a plan past its border is refused). Should the
     * territory shrink later (a hut removed, then a reload), FieldRegistry.cleanUp drops the field at the next slow
     * tick and it joins the colony at the block at its first use (FieldActions.open).
     */
    void placed(BlockPos pos, BlueprintEntry e) {
        if (e.hasContainer()) {
            ctx.site().target().registeredBlocks().addContainer(pos); // MC: racks placed become the containers
        }
        e.workstation().ifPresent(bench -> registerBench(pos, bench));
        BuildingEventsModule.blockPlaced(
                ctx.colony(), ctx.site().target(), pos, e.state().key());
        if (ctx.colony().registries().planBlockPlaced(pos)) {
            ctx.colony().markDirty();
        }
    }

    /**
     * The cells {@code from} to {@code to} (excluded) of a placing {@code stage}, which the scan skipped: those found
     * as planned register their container, bench and bed, once. CLEAR, REMOVE and CLEAR_LEFTOVERS place nothing. Beds
     * are only looked for in a hut that keeps them, sparing a large plan a catalog lookup per cell.
     */
    void foundAsPlanned(Stage stage, int from, int to) {
        if (stage != Stage.SOLID && stage != Stage.DECORATE) {
            return;
        }
        List<BlockPos> positions = ctx.site().positions(stage);
        boolean keepsBeds = ctx.site().target().module(BedModule.class).isPresent();
        for (int i = from; i < to; i++) {
            BlueprintEntry e = ctx.site().entry(stage, i);
            if (e.hasContainer()
                    || e.workstation().isPresent()
                    || (keepsBeds && ctx.catalog().isBed(e.state().key()))) {
                registerIfAsPlanned(positions.get(i), e);
            }
        }
    }

    /**
     * Registers the container and bench the plan puts at {@code pos} if the world there satisfies it, writing nothing
     * to the world: the bench joins at the tier it has (MC triggerSuccess with placement false only registers).
     */
    private void registerIfAsPlanned(BlockPos pos, BlueprintEntry e) {
        @Nullable BlockState world = ctx.blocks().get(pos).orElse(null);
        if (!ctx.site().plan().satisfied(e, world, ctx.catalog())) {
            return;
        }
        RegisteredBlocks registered = ctx.site().target().registeredBlocks();
        if (e.hasContainer()) {
            registered.addContainer(pos);
        }
        e.workstation()
                .ifPresent(bench ->
                        registered.addFoundWorkstation(pos, bench, ctx.blocks().benchTier(pos)));
        BuildingEventsModule.blockPlaced(
                ctx.colony(), ctx.site().target(), pos, e.state().key());
    }

    /**
     * Gives the bench its planned tier, then registers it with the target hut (MC triggerSuccess ->
     * registerBlockPosition). The hut keeps the planned tier even if the world refused it: the builder paid for it.
     *
     * <p>Deviation from MC: MC blocks have no tier; Hytale benches do (SP3b-1 spec, deviations 2 and 3).
     */
    private void registerBench(BlockPos pos, Workstation bench) {
        if (!ctx.blocks().setBenchTier(pos, bench.tier())) {
            LOG.log(
                    tierWarned ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                    "Builder {0}: could not set the bench at {1} to tier {2}; the hut registers it at that tier",
                    ctx.citizen().name(),
                    pos,
                    bench.tier());
            tierWarned = true;
        }
        ctx.site().target().registeredBlocks().addWorkstation(pos, bench);
    }
}
