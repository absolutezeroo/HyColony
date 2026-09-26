package dev.hycolony.core.construction.goggles;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.PreviewPort;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The build sites a goggles wearer sees, and what is left to do on each. MC ColonyBlueprintRenderer's BuildGoggles
 * rule: the work orders of the colony the player stands in (or the nearest one) whose hut is within
 * {@code buildgogglerange}.
 *
 * <p>Deviation from MC: only orders claimed by a builder, and only their remaining blocks (MC draws every order's
 * full blueprint, a box for REMOVE and the anchor of unbuilt huts).
 */
final class GogglesView {
    /** MC ClientConfiguration.buildgogglerange default, 50 blocks, compared squared to the hut. */
    static final long RANGE_SQ = 50L * 50L;

    /** One visible claimed order; {@code id} names its preview. */
    record Site(String id, Colony colony, WorkOrder order) {}

    private final ColonyManager manager;

    GogglesView(ColonyManager manager) {
        this.manager = manager;
    }

    /** The claimed orders of the player's colony whose hut is within range; empty when there is no colony. */
    List<Site> visible(BlockPos player) {
        Optional<Colony> colony = manager.colonyAt(player).or(() -> nearest(player));
        if (colony.isEmpty()) {
            return List.of();
        }
        List<Site> sites = new ArrayList<>();
        for (WorkOrder o : colony.get().work().ordered()) {
            if (o.claimedBy().isPresent() && o.buildingPos().distSq(player) <= RANGE_SQ) {
                sites.add(new Site(colony.get().id() + ":" + o.id(), colony.get(), o));
            }
        }
        return sites;
    }

    /**
     * The blocks still to place (planned state not yet in the world), or for REMOVE the blocks still standing on the
     * plan, relative to the hut. Bounded by the blueprint; empty if the hut or its blueprint is gone.
     */
    List<PreviewPort.Block> remaining(Site site) {
        WorkOrder o = site.order();
        Optional<Building> b = site.colony().buildings().at(o.buildingPos());
        Optional<Blueprint> bp = b.flatMap(building -> manager.context()
                .ports()
                .blueprints()
                .load(o.style(), building.type().id(), o.blueprintLevel(), o.rotation()));
        if (bp.isEmpty()) {
            return List.of();
        }
        WorldBlocks world = manager.context().ports().blocks();
        ItemCatalog catalog = manager.context().ports().catalog();
        boolean remove = o.type() == WorkOrderType.REMOVE;
        List<PreviewPort.Block> blocks = new ArrayList<>();
        for (BlueprintEntry e : bp.get().entries()) {
            if (catalog.kind(e.state().key()) == BlockKind.AIR) {
                continue;
            }
            Optional<BlockState> current = world.get(o.buildingPos()
                    .offset(e.offset().x(), e.offset().y(), e.offset().z()));
            if (remove) {
                current.filter(s -> standing(catalog, s))
                        .ifPresent(s -> blocks.add(new PreviewPort.Block(e.offset(), s)));
            } else if (!current.map(e.state()::equals).orElse(false)) {
                blocks.add(new PreviewPort.Block(e.offset(), e.state()));
            }
        }
        return blocks;
    }

    /** A block the builder's REMOVE stage still mines (StructureScan.mineable). */
    private static boolean standing(ItemCatalog catalog, BlockState state) {
        BlockKind kind = catalog.kind(state.key());
        return kind != BlockKind.AIR && kind != BlockKind.FLUID && kind != BlockKind.UNBREAKABLE;
    }

    private Optional<Colony> nearest(BlockPos player) {
        return manager.all().stream()
                .min(Comparator.comparingLong(c -> c.center().distSq(player)));
    }
}
