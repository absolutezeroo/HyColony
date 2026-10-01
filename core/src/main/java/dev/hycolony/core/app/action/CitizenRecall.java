package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyRefusal;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.Msg;
import java.util.UUID;

/** Brings citizens back to a hut: one to the town hall (MC RecallSingleCitizenMessage), or a hut's residents. */
public final class CitizenRecall {
    private final ColonyManager manager;

    public CitizenRecall(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * The town hall's "Recall Citizen" (MANAGE_HUTS): the citizen is teleported to the town hall, or gets a body there
     * (MC updateEntityIfNecessary), else the player is told MC's recallfail; the town hall shows again. False without
     * the right, the citizen or the town hall.
     */
    public boolean recall(UUID player, int colonyId, int citizenId) {
        Colony c = manager.byId(colonyId).orElse(null);
        Building hall = c == null ? null : c.buildings().townHall().orElse(null);
        if (c == null || hall == null || c.citizens().get(citizenId).isEmpty()) {
            return false;
        }
        if (!ColonyAccess.allows(c, player, Action.MANAGE_HUTS)) {
            ColonyRefusal.tellNoPermission(c, player);
            return false;
        }
        if (!bring(manager, c, citizenId, hall.position())) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.recallFail"));
        }
        manager.windows().showTownHall(c, player);
        return true;
    }

    /**
     * Teleports citizen {@code id} to {@code hut} (asleep, it wakes first), or gives it a body there; false if no body
     * could appear. MC setNextRespawnPosition then updateEntityIfNecessary: the position is used once.
     *
     * <p>Deviation from MC: MC says "recall failed" when TeleportHelper finds no free spot; HyColony's teleport never
     * fails, so a body that cannot appear is what the callers report as failed.
     */
    static boolean bring(ColonyManager manager, Colony c, int id, BlockPos hut) {
        boolean alive = c.citizens()
                .bodyOf(id)
                .filter(manager.context().bodies()::isAlive)
                .isPresent();
        if (alive) {
            c.citizens().ai(id).ifPresent(ai -> ai.teleport(Vec3.center(hut)));
            return true;
        }
        CitizenData d = c.citizens().get(id).orElse(null);
        if (d == null) {
            return false;
        }
        d.setRespawnPosition(hut);
        boolean spawned = c.citizens().respawnBody(id);
        if (spawned) {
            d.setRespawnPosition(null);
        }
        return spawned;
    }
}
