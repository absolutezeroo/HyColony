package dev.hycolony.core.colony.action;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

/** A hut a player may manage (MANAGE_HUTS, as MC's building messages), with its colony. */
record ManagedHut(Colony colony, Building building) {
    static Optional<ManagedHut> find(ColonyManager manager, UUID player, BlockPos pos) {
        return manager.colonyAt(pos)
                .filter(c -> c.permissions().hasPermission(player, Action.MANAGE_HUTS))
                .flatMap(c -> c.buildings().at(pos).map(b -> new ManagedHut(c, b)));
    }
}
