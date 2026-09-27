package dev.hycolony.core.construction.wand;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
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
 * Validates and places the hut chosen with the build tool (MC SurvivalHandler.handle): permission, town hall
 * distance or footprint in the colony, hut rules, hut block in the inventory, then the block and the building at
 * level 0, with no work order.
 *
 * <p>A creative player may take this survival path too (Structurize lists SurvivalHandler beside Pretty), only
 * without consuming the hut block; the creative paste is {@link WandPaste}.
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
        Optional<Refused> location = locationRefusal(s, pos, colony);
        if (location.isPresent()) {
            return location.get();
        }
        HutPlacement check = manager.huts().checkHutRules(player, pos, s.buildingTypeId());
        if (check instanceof HutPlacement.Denied(var reason)) {
            return new Refused(reason);
        }
        return place(player, playerName, s, type.get(), check);
    }

    /**
     * MC SurvivalHandler.handle l.112-132: a town hall inside a colony passes, and outside one it must be far enough
     * from every colony (TOWNHALL_TOO_CLOSE); any other hut must stand in a colony with its whole footprint.
     */
    private Optional<Refused> locationRefusal(WandSession s, BlockPos pos, Optional<Colony> colony) {
        if (BuildingTypes.TOWN_HALL.id().equals(s.buildingTypeId())) {
            var claims = manager.context().config().claims();
            boolean fits = colony.isPresent()
                    || manager.territory()
                            .isFreeForNewColony(pos, claims.initialColonySize(), claims.minColonyDistance());
            return fits ? Optional.empty() : Optional.of(refused("hycolony.colony.tooClose"));
        }
        if (colony.isEmpty()) {
            return Optional.of(refused("hycolony.wand.outsideColony"));
        }
        return footprintRefusal(s, pos, colony.get());
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

    /**
     * Steps 4 and 5: checks a survival player has the hut block, breaks what stands at the anchor, places the hut
     * block, and only then takes one, so nothing is consumed if placing fails; then registers the hut or begins
     * founding.
     */
    private Result place(UUID player, String playerName, WandSession s, BuildingType type, HutPlacement check) {
        ItemKey item = hutItem.apply(type.hutBlockKey());
        boolean creative = manager.context().players().isCreative(player);
        if (!creative && ports().playerInventory().count(player, item) < 1) {
            // Deviation from MC: SurvivalHandler only plays an error sound here; the core has no sound port, so the
            // player gets a chat message instead.
            return refused("hycolony.wand.missingHut");
        }
        BlockPos pos = s.anchor().orElseThrow();
        Optional<Refused> failed = placeHutBlock(pos, type, s.rotation());
        if (failed.isPresent()) {
            return failed.get();
        }
        if (!creative) {
            ports().playerInventory().take(player, item, 1);
        }
        if (check instanceof HutPlacement.Allowed(var colony)) {
            manager.huts().place(colony, type.id(), pos, s.rotation());
            Building building = colony.buildings().at(pos).orElseThrow();
            building.setStyle(s.style());
            colony.markDirty();
            return new Placed(building);
        }
        manager.foundation().begin(player, playerName, pos, s.rotation(), s.style());
        return new FoundColony();
    }

    /**
     * Breaks what stands at {@code pos} and places the hut block there, turned by {@code rotation}; the refusal if
     * the block could not be placed (a hut broken meanwhile is then unregistered).
     */
    Optional<Refused> placeHutBlock(BlockPos pos, BuildingType type, int rotation) {
        Optional<BlockState> before = ports().blocks().get(pos);
        breakAnchor(pos);
        BlockState state = new BlockState(hutBlock.apply(type.hutBlockKey()), rotation);
        if (!ports().blocks().place(pos, state, false)) {
            if (!ports().blocks().get(pos).equals(before)) {
                manager.huts().onRemoved(pos); // a hut broken at the anchor must not stay registered without its block
            }
            return Optional.of(refused("hycolony.wand.placeFailed"));
        }
        return Optional.empty();
    }

    /**
     * MC SurvivalHandler.handle l.168 {@code world.destroyBlock(blockPos, true)}: breaks what stands at the anchor and
     * drops its items there.
     */
    private void breakAnchor(BlockPos pos) {
        ports().blocks().drop(pos, ports().blocks().breakBlock(pos));
    }

    private ConstructionPorts ports() {
        return manager.context().ports();
    }

    static Refused refused(String key) {
        return new Refused(Msg.of(key));
    }
}
