package dev.hycolony.core.citizen.sleep;

import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.HeldItems;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;

/** A citizen lying down in its bed and getting up: MC CitizenSleepHandler.trySleep and onWakeUp. */
public final class SleepHandler {
    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final CitizenBodies bodies;

    public SleepHandler(Colony colony, CitizenData data, BodyId body) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.bodies = colony.context().bodies();
    }

    /**
     * MC trySleep: lies in the bed at {@code bed}, its held item put away, asleep (its leisure over) with this bed; the
     * colony learns of it for its "all asleep" notice. False, changing nothing, when the body cannot lie there.
     */
    public boolean trySleep(BlockPos bed) {
        if (!bodies.sleepIn(body, bed)) {
            return false;
        }
        HeldItems.holdItem(data, bodies, body, Optional.empty());
        data.setAsleep(true);
        data.setBedPos(bed);
        colony.markDirty();
        SleepNotice.onCitizenSleep(colony);
        return true;
    }

    /** MC onWakeUp for an asleep citizen (MC calculateNextState and TeleportHelper check isAsleep); else nothing. */
    public void wakeUp() {
        if (data.asleep()) {
            onWakeUp();
        }
    }

    /**
     * MC onWakeUp, asleep or not (MC CitizenData.initEntityValues calls it at each body appearance): its workplace,
     * job and home learn of it; an asleep citizen gets out of bed, bedless; it ends awake, its leisure over.
     */
    public void onWakeUp() {
        Optional.ofNullable(data.workBuilding())
                .flatMap(colony.buildings()::at)
                .ifPresent(b -> BuildingEventsModule.wakeUp(colony, b));
        data.job().ifPresent(job -> job.onWakeUp(colony));
        Optional.ofNullable(data.homeBuilding())
                .flatMap(colony.buildings()::at)
                .ifPresent(b -> BuildingEventsModule.wakeUp(colony, b));
        if (data.asleep()) {
            bodies.wakeUp(body);
            data.setBedPos(null);
        }
        data.setAsleep(false);
        colony.markDirty();
    }

    /**
     * Its bed was left without us (a broken bed, players skipping the night, a teleport): awake and bedless, without
     * the wake-up hooks. Deviation from MC: nothing gets a sleeping citizen out of its bed in MC.
     */
    public void leftBed() {
        data.setAsleep(false);
        data.setBedPos(null);
        colony.markDirty();
    }
}
