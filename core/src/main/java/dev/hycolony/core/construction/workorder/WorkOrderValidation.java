package dev.hycolony.core.construction.workorder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.shared.BuilderHut;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Either;
import java.util.List;
import java.util.Optional;

/**
 * The creation checks of MC AbstractBuilding.requestWorkOrder that follow the permission and duplicate checks, in
 * MC's order: max level, repair of an unbuilt building, a type that does not fit the building, no builder of the
 * level, no builder within 100 blocks (unless one is chosen), no blueprint, footprint outside the colony.
 */
final class WorkOrderValidation {
    /** What an accepted request resolves to. */
    record Accepted(int targetLevel, WorkOrder.Layout layout) {}

    private final Colony colony;

    WorkOrderValidation(Colony colony) {
        this.colony = colony;
    }

    Either<Accepted, WorkOrderRefusal> check(Building b, WorkOrderType type, String style, Optional<BlockPos> builder) {
        Optional<WorkOrderRefusal> refusal = levelRefusal(b, type);
        // WorkOrderBuilding.create: REMOVE targets 0 but follows the plan of the current level.
        int target = targetLevel(type, b.level());
        if (refusal.isEmpty()) {
            refusal = builderRefusal(b, type, target, builder);
        }
        if (refusal.isPresent()) {
            return new Either.Right<>(refusal.get());
        }
        String resolvedStyle = resolveStyle(style, type, b);
        int blueprintLevel = type == WorkOrderType.REMOVE ? b.level() : target;
        Optional<Blueprint> blueprint = colony.context()
                .ports()
                .blueprints()
                .load(resolvedStyle, b.type().id(), blueprintLevel, b.rotation());
        if (blueprint.isEmpty()) {
            return new Either.Right<>(WorkOrderRefusal.NO_BLUEPRINT);
        }
        if (!insideColony(blueprint.get(), b.position())) {
            return new Either.Right<>(WorkOrderRefusal.OUT_OF_COLONY);
        }
        return new Either.Left<>(
                new Accepted(target, new WorkOrder.Layout(resolvedStyle, blueprintLevel, b.rotation())));
    }

    private static Optional<WorkOrderRefusal> levelRefusal(Building b, WorkOrderType type) {
        if ((type == WorkOrderType.BUILD || type == WorkOrderType.UPGRADE)
                && b.level() >= b.type().maxLevel()) {
            return Optional.of(WorkOrderRefusal.MAX_LEVEL);
        }
        if (type == WorkOrderType.REPAIR && b.level() == 0 && !b.isDeconstructed()) {
            return Optional.of(WorkOrderRefusal.NOT_BUILT);
        }
        return WorkManager.isAllowed(b, type) ? Optional.empty() : Optional.of(WorkOrderRefusal.INVALID_TYPE);
    }

    private static int targetLevel(WorkOrderType type, int level) {
        return switch (type) {
            case BUILD, UPGRADE -> level + 1;
            case REPAIR -> level;
            case REMOVE -> 0;
        };
    }

    private Optional<WorkOrderRefusal> builderRefusal(
            Building b, WorkOrderType type, int target, Optional<BlockPos> builder) {
        List<Building> employed = colony.buildings().all().stream()
                .filter(WorkManager::isEmployedBuilder)
                .toList();
        if (type != WorkOrderType.REMOVE
                && !canBeBuiltByBuilder(b, target)
                && employed.stream().noneMatch(e -> e.level() >= target)) {
            return Optional.of(WorkOrderRefusal.BUILDER_NECESSARY);
        }
        if (builder.isEmpty()) {
            boolean near =
                    employed.stream().anyMatch(e -> e.position().distSq(b.position()) <= WorkManager.MAX_DISTANCE_SQ);
            return near ? Optional.empty() : Optional.of(WorkOrderRefusal.BUILDER_TOO_FAR_AWAY);
        }
        // REMOVE targets 0, so any builder hut qualifies.
        Optional<Building> chosen = colony.buildings().at(builder.get());
        boolean fits = chosen.isPresent()
                && BuilderHut.is(chosen.get())
                && (chosen.get().level() >= target || canBeBuiltByBuilder(b, target));
        return fits ? Optional.empty() : Optional.of(WorkOrderRefusal.BUILDER_NECESSARY);
    }

    /** BuildingBuilder.canBeBuiltByBuilder: a builder hut may always order its own next level. */
    private static boolean canBeBuiltByBuilder(Building b, int target) {
        return BuilderHut.is(b) && target == b.level() + 1;
    }

    /** A built building keeps its style: only a BUILD may choose one. */
    private String resolveStyle(String style, WorkOrderType type, Building b) {
        if (type != WorkOrderType.BUILD && !b.style().isEmpty()) {
            return b.style();
        }
        if (style != null && !style.isEmpty()) {
            return style;
        }
        if (!b.style().isEmpty()) {
            return b.style();
        }
        List<String> styles = colony.context().ports().blueprints().styles();
        return styles.isEmpty() ? "" : styles.getFirst();
    }

    /** The blueprint's footprint around {@code hut} lies in this colony (see {@link ClaimCell#allOwned}). */
    private boolean insideColony(Blueprint bp, BlockPos hut) {
        return ClaimCell.allOwned(
                hut.offset(bp.min().x(), 0, bp.min().z()),
                hut.offset(bp.max().x(), 0, bp.max().z()),
                colony::contains);
    }
}
