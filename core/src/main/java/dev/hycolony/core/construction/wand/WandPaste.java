package dev.hycolony.core.construction.wand;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.ColonyFoundation;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

/**
 * The build tool's creative "Pretty" paste of a hut (ST BlueprintPlacementHandling.process, MC AbstractBlockHut.setup
 * and canPaste, CreativeBuildingStructureHandler): no footprint check, no hut block taken, no work order, no log nor
 * completion message. The hut stands at once, built at the chosen level; its blocks follow over the next ticks.
 *
 * <p>Deviation from MC: no "Complete" paste, since our prefabs carry no substitution blocks and both would place the
 * same blocks. The town hall rules include the founding distance checks at paste time, where MC only checks them
 * when the colony is created.
 */
final class WandPaste {
    private final ColonyManager manager;
    private final WandPlacement placement;
    private final PasteQueue queue;

    WandPaste(ColonyManager manager, WandPlacement placement, PasteQueue queue) {
        this.manager = manager;
        this.placement = placement;
        this.queue = queue;
    }

    /**
     * Refuses on the first failed check (PLACE_HUTS in a colony, the town hall rules, a missing plan), otherwise
     * places the hut block, queues the plan's blocks and registers the hut built at the session's level; a town hall
     * outside every colony begins the founding at that level instead. The caller checks creative mode.
     */
    WandPlacement.Result paste(UUID player, String playerName, WandSession s) {
        Optional<BuildingType> type = manager.context().buildingTypes().byId(s.buildingTypeId());
        if (!s.hasBuilding() || type.isEmpty()) {
            return WandPlacement.refused("hycolony.wand.noBuilding");
        }
        if (s.anchor().isEmpty()) {
            return WandPlacement.refused("hycolony.wand.missingPos");
        }
        BlockPos pos = s.anchor().get();
        HutPlacement check = manager.huts().checkPlacement(player, pos, s.buildingTypeId());
        if (check instanceof HutPlacement.Denied(var reason)) {
            return new WandPlacement.Refused(reason);
        }
        Optional<Blueprint> bp =
                manager.context().ports().blueprints().load(s.style(), s.buildingTypeId(), s.level(), s.rotation());
        if (bp.isEmpty()) {
            return WandPlacement.refused("hycolony.workorder.refused.no_blueprint");
        }
        Optional<WandPlacement.Refused> failed = placement.placeHutBlock(pos, type.get(), s.rotation());
        if (failed.isPresent()) {
            return failed.get();
        }
        queue.add(StructurePlan.build(bp.get(), pos, manager.context().ports().catalog()));
        if (check instanceof HutPlacement.Allowed(var colony)) {
            manager.huts().place(colony, type.get().id(), pos, s.rotation());
            Building building = colony.buildings().at(pos).orElseThrow();
            building.setStyle(s.style());
            // MC setup: onUpgradeComplete(blueprint, level). Deviation from MC: the chosen level, which MC's code
            // intends; its path parsing reads the wrong character and would give level 1.
            UpgradeCompletion.reach(colony, building, s.level());
            return new WandPlacement.Placed(building);
        }
        manager.foundation()
                .begin(player, playerName, new ColonyFoundation.TownHall(pos, s.rotation(), s.style(), s.level()));
        return new WandPlacement.FoundColony();
    }
}
