package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

/** A hut a player may manage (MANAGE_HUTS, as MC's building messages), with its colony. */
public record ManagedHut(Colony colony, Building building) {
    /** The hut at {@code pos} if {@code player} may manage it; empty otherwise, the player told of a missing right. */
    public static Optional<ManagedHut> find(ColonyManager manager, UUID player, BlockPos pos) {
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isPresent() && !ColonyAccess.allows(colony.get(), player, Action.MANAGE_HUTS)) {
            ColonyRefusal.tellNoPermission(colony.get(), player);
            return Optional.empty();
        }
        return colony.flatMap(c -> c.buildings().at(pos).map(b -> new ManagedHut(c, b)));
    }
}
