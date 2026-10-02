package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyFoundation;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Optional;
import java.util.UUID;

/**
 * The build tool's creative "Pretty" paste of a hut (ST BlueprintPlacementHandling.process, MC AbstractBlockHut.setup
 * and canPaste, CreativeBuildingStructureHandler): no footprint check, no hut block taken, no work order, no log nor
 * completion message. The hut stands at once, built at the chosen level; its blocks follow over the next ticks.
 *
 * <p>Deviation from MC: no "Complete" paste, which would place a MineColonies plan's placeholder blocks themselves:
 * a structure-editing need that Hytale's prefab editor covers. The town hall rules include the founding distance
 * checks at paste time, where MC only checks them when the colony is created. No construction tape is taken down
 * when the paste ends (MC CreativeBuildingStructureHandler.onCompletion): a paste places no tape, and pasting over
 * a hut first removes the old building (HutActions.place), which takes its tape down.
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
        Optional<WandPlacement.Refused> failed = placement.placeHutBlock(pos, type.get(), s.rotation(), player);
        if (failed.isPresent()) {
            return failed.get();
        }
        queue.add(plan(bp.get(), pos));
        if (check instanceof HutPlacement.Allowed(var colony)) {
            manager.huts().place(colony, type.get().id(), pos, s.rotation(), player);
            Building building = colony.buildings().at(pos).orElseThrow();
            building.setStyle(s.style());
            // MC setup: onUpgradeComplete(blueprint, level). Deviation from MC: the chosen level, which MC's code
            // intends; its path parsing reads the wrong character and would give level 1.
            UpgradeCompletion.reach(colony, building, s.level(), Optional.of(player));
            return new WandPlacement.Placed(building);
        }
        manager.foundation()
                .begin(player, playerName, new ColonyFoundation.TownHall(pos, s.rotation(), s.style(), s.level()));
        return new WandPlacement.FoundColony();
    }

    /**
     * The paste's plan; its fill cells get the source's default fill block. Deviation from MC: ST
     * CreativeStructureHandler picks the world generator's block there (BlockUtils.getSubstitutionBlockAtWorld).
     */
    private StructurePlan plan(Blueprint bp, BlockPos pos) {
        ItemCatalog catalog = manager.context().ports().catalog();
        return manager.context()
                .ports()
                .blueprints()
                .defaultFillBlock()
                .map(block -> StructurePlan.build(bp, pos, catalog, block))
                .orElseGet(() -> StructurePlan.build(bp, pos, catalog));
    }
}
