package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Loads a claimed work order's structure onto the builder's site (MC AbstractEntityAIStructureWithWorkOrder
 * .loadStructure): the plan of its blueprint, the level an upgrade replaces, and the resources it needs.
 */
final class StructureLoader {
    private static final System.Logger LOG = System.getLogger(StructureLoader.class.getName());

    private final BuilderContext ctx;

    StructureLoader(BuilderContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Loads {@code o} onto the (cleared) site and starts its resources from the order's saved stage and index, the
     * order being the single owner of progress. Returns false when its building or blueprint is gone: the order is
     * then cancelled (MC handleSpecificCancelActions).
     */
    boolean load(WorkOrder o) {
        Building b = ctx.colony().buildings().at(o.buildingPos()).orElse(null);
        Blueprint bp = b == null ? null : blueprint(o, b, o.blueprintLevel()).orElse(null);
        if (b == null || bp == null) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "No blueprint for work order {0} at {1}; removing it",
                    o.id(),
                    o.buildingPos());
            ctx.colony().work().cancel(o.id());
            return false;
        }
        BuildSite site = ctx.site();
        site.load(o, b, ctx.planFor(bp, o.buildingPos()), previousPlan(o, b));
        ctx.resources().start(o, NeededResources.compute(site.plan(), ctx.blocks(), ctx.catalog(), ctx.recipes()));
        return true;
    }

    private Optional<Blueprint> blueprint(WorkOrder o, Building b, int level) {
        return ctx.colony()
                .context()
                .ports()
                .blueprints()
                .load(o.style(), b.type().id(), level, o.rotation());
    }

    /**
     * The plan of the level an UPGRADE replaces (same style and rotation), whose leftovers CLEAR_LEFTOVERS mines;
     * null for other orders or when that blueprint is missing (nothing is then removed).
     */
    private @Nullable StructurePlan previousPlan(WorkOrder o, Building b) {
        if (o.type() != WorkOrderType.UPGRADE) {
            return null;
        }
        return blueprint(o, b, o.blueprintLevel() - 1)
                .map(old -> StructurePlan.build(old, o.buildingPos(), ctx.catalog()))
                .orElse(null);
    }
}
