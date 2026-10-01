package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.BuildOptionsView;
import dev.hycolony.core.app.ui.BuildOptionsView.BuilderChoice;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.resources.NeededResources;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Builds the build options window's view (MC WindowBuildBuilding.onOpened: styles, builders, resources). */
final class BuildOptionsViews {
    private BuildOptionsViews() {}

    static BuildOptionsView of(Colony c, Building b, BuildingView hut) {
        String style = style(c, b, hut);
        Optional<Blueprint> plan =
                c.context().ports().blueprints().load(style, b.type().id(), level(b), b.rotation());
        boolean townHall = c.buildings().townHall().filter(b::equals).isPresent();
        boolean pickUp = !townHall && (b.level() == 0 || b.isDeconstructed() || plan.isEmpty());
        List<ItemAmount> resources = plan.map(bp -> resources(c, b, bp)).orElse(List.of());
        return new BuildOptionsView(hut, style, builders(c, b), resources, plan.isPresent(), pickUp);
    }

    /**
     * MC updateStyles: the hut's own style. Deviation from MC: a hut saved without one shows the colony's, else the
     * first the blueprints offer (MC copies the colony's pack at placement and defaults to "Colonial").
     */
    private static String style(Colony c, Building b, BuildingView hut) {
        if (!b.style().isEmpty()) {
            return b.style();
        }
        String colony = c.settings().style();
        return colony.isEmpty() && !hut.styles().isEmpty() ? hut.styles().getFirst() : colony;
    }

    /**
     * MC canBeUpgraded: the next level below the hut's max, else its own.
     *
     * <p>Deviation from MC: no parent hut rule, as HyColony has no parent huts.
     */
    private static int level(Building b) {
        return b.level() < b.type().maxLevel() ? b.level() + 1 : b.level();
    }

    /**
     * MC updateBuilders: the builder huts that have a worker, nearest first.
     *
     * <p>Deviation from MC: MC lists every builder-like hut but the miner; HyColony's only one is the builder.
     */
    private static List<BuilderChoice> builders(Colony c, Building target) {
        return c.buildings().all().stream()
                .filter(h -> h.type().id().equals(ConstructionBuildingTypes.BUILDER.id()))
                .sorted(Comparator.comparingLong(h -> h.position().distSq(target.position())))
                .flatMap(
                        h -> WorkerModule.firstWorker(c, h)
                                .map(CitizenData::name)
                                .map(name -> new BuilderChoice(h.position(), name))
                                .stream())
                .toList();
    }

    /** MC updateResources (GET_RES_REQUIREMENTS): what the plan still needs in the world. */
    private static List<ItemAmount> resources(Colony c, Building b, Blueprint bp) {
        GamePorts ports = c.context().ports();
        return NeededResources.compute(
                        StructurePlan.build(bp, b.position(), ports.catalog()),
                        ports.blocks(),
                        ports.catalog(),
                        ports.crafting().catalog())
                .remaining()
                .entrySet()
                .stream()
                .map(e -> new ItemAmount(e.getKey(), e.getValue()))
                .toList();
    }
}
