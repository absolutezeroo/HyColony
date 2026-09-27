package dev.hycolony.core.construction.wand;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Validates and places the hut chosen with the build tool (MC SurvivalHandler.handle): permission, town hall rules,
 * footprint in the colony, hut block in the inventory, then the block and the building at level 0, with no work
 * order.
 */
final class WandPlacement {
    /** Outcome of {@link #confirm}. */
    sealed interface Result permits Refused, Placed, FoundColony {}

    /** The first failed check, as the message to show the player; nothing was changed. */
    record Refused(Msg reason) implements Result {}

    /** The hut was placed and registered in its colony. */
    record Placed(Building building) implements Result {}

    /** A town hall outside every colony: its block stands and the founding flow has begun. */
    record FoundColony() implements Result {}

    private final ColonyManager manager;
    private final Function<String, ItemKey> hutItem;
    private final Function<String, BlockKey> hutBlock;

    /**
     * {@code hutItem} and {@code hutBlock} turn a hut's logical {@code hutBlockKey} into its item and block keys: the
     * core does not know the asset ids, which live in the plugin's id map.
     */
    WandPlacement(ColonyManager manager, Function<String, ItemKey> hutItem, Function<String, BlockKey> hutBlock) {
        this.manager = manager;
        this.hutItem = hutItem;
        this.hutBlock = hutBlock;
    }

    /**
     * MC SurvivalHandler.handle, steps 1 to 6 of the spec in order: refuses on the first failed check, otherwise
     * places the hut block at the anchor, takes one from a survival player and registers the hut (or begins the
     * founding of a colony for a town hall outside every colony). The caller clears the session.
     */
    Result confirm(UUID player, String playerName, WandSession s) {
        Optional<BuildingType> type = manager.context().buildingTypes().byId(s.buildingTypeId());
        if (!s.hasBuilding() || type.isEmpty()) {
            return refused("hycolony.wand.noBuilding");
        }
        if (s.anchor().isEmpty()) {
            return refused("hycolony.wand.missingPos");
        }
        BlockPos pos = s.anchor().get();
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isPresent() && !colony.get().permissions().hasPermission(player, Action.MANAGE_HUTS)) {
            return refused("hycolony.wand.noPermission");
        }
        HutPlacement check = manager.huts().checkPlacement(player, pos, s.buildingTypeId());
        if (check instanceof HutPlacement.Denied denied) {
            return new Refused(denied.reason());
        }
        if (check instanceof HutPlacement.Allowed) {
            Optional<Refused> outside = footprintRefusal(s, pos, colony.orElseThrow());
            if (outside.isPresent()) {
                return outside.get();
            }
        }
        return place(player, playerName, s, type.get(), check);
    }

    /** Step 3: the plan of the chosen level, as rotated, must stay in the colony (MC BP_OUTSIDE_COLONY). */
    private Optional<Refused> footprintRefusal(WandSession s, BlockPos pos, Colony colony) {
        Optional<Blueprint> bp = ports().blueprints().load(s.style(), s.buildingTypeId(), s.level(), s.rotation());
        if (bp.isEmpty()) {
            return Optional.of(refused("hycolony.workorder.refused.no_blueprint"));
        }
        BlockPos min = bp.get().min();
        BlockPos max = bp.get().max();
        boolean inside =
                ClaimCell.allOwned(pos.offset(min.x(), 0, min.z()), pos.offset(max.x(), 0, max.z()), colony::contains);
        return inside ? Optional.empty() : Optional.of(refused("hycolony.wand.outsideColony"));
    }

    /** Steps 4 and 5: takes the hut block (survival only), places it, then registers the hut or begins founding. */
    private Result place(UUID player, String playerName, WandSession s, BuildingType type, HutPlacement check) {
        ItemKey item = hutItem.apply(type.hutBlockKey());
        boolean creative = manager.context().players().isCreative(player);
        if (!creative && ports().playerInventory().count(player, item) < 1) {
            return refused("hycolony.wand.missingHut");
        }
        BlockPos pos = s.anchor().orElseThrow();
        BlockState state = new BlockState(hutBlock.apply(type.hutBlockKey()), s.rotation());
        if (!ports().blocks().place(pos, state, false)) {
            return refused("hycolony.wand.placeFailed");
        }
        if (!creative) {
            ports().playerInventory().take(player, item, 1);
        }
        if (check instanceof HutPlacement.Allowed allowed) {
            manager.huts().place(allowed.colony(), type.id(), pos, s.rotation());
            Building building = allowed.colony().buildings().at(pos).orElseThrow();
            building.setStyle(s.style());
            allowed.colony().markDirty();
            return new Placed(building);
        }
        manager.foundation().begin(player, playerName, pos, s.rotation());
        return new FoundColony();
    }

    private ConstructionPorts ports() {
        return manager.context().ports();
    }

    private static Refused refused(String key) {
        return new Refused(Msg.of(key));
    }
}
