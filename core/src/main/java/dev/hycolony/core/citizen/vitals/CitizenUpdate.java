package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.wander.LeisureTimer;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;

/** MC CitizenData.update: what a citizen with a living body does every {@link Colony#CITIZEN_DATA_INTERVAL} ticks. */
public final class CitizenUpdate {
    private CitizenUpdate() {}

    /**
     * Records its body's position (the way since the last one counts as walking, MC decreaseWalkingSaturation), counts
     * its job's inactivity and its leisure time.
     */
    public static void update(Colony colony, CitizenData data, BodyId body) {
        colony.context().bodies().position(body).ifPresent(pos -> moved(data, pos));
        data.job().ifPresent(job -> job.tickInactivity(colony));
        LeisureTimer.tick(
                data,
                Colony.CITIZEN_DATA_INTERVAL,
                homeLevel(colony, data),
                colony.context().random());
    }

    /** Its body stands at {@code pos} now: the way from its last position counts as walking (MC walkDist). */
    private static void moved(CitizenData data, Vec3 pos) {
        Vec3 last = data.lastPosition();
        if (last != null) {
            data.hunger().walked(last, pos);
        }
        data.setLastPosition(pos);
    }

    /** The level of the citizen's home; 1 without one (MC CitizenData.update). */
    private static int homeLevel(Colony colony, CitizenData data) {
        BlockPos home = data.homeBuilding();
        return home == null
                ? 1
                : colony.buildings().at(home).map(Building::level).orElse(1);
    }
}
