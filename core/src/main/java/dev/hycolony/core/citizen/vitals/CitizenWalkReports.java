package dev.hycolony.core.citizen.vitals;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.hurt.CitizenHurt;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.nav.StuckHandler;
import dev.hycolony.core.kernel.nav.WalkEnd;
import dev.hycolony.core.kernel.nav.WalkListener;
import dev.hycolony.core.kernel.port.NavStatus;

/**
 * One citizen's walks as its vital signs see them: each start, end and stuck action is kept in its
 * {@link CitizenVitals}, and posted as a {@link CitizenDebugEvents} only while someone listens; a teleport out of a
 * full stuck also hurts it ({@link CitizenHurt#stuck}). Every walker of that citizen is given one.
 */
public final class CitizenWalkReports implements WalkListener {
    private final Colony colony;
    private final CitizenData citizen;

    public CitizenWalkReports(Colony colony, CitizenData citizen) {
        this.colony = colony;
        this.citizen = citizen;
    }

    @Override
    public void walkStarted(BlockPos target, Vec3 from) {
        citizen.vitals().walks().started(target, now());
    }

    @Override
    public void walkEnded(BlockPos target, Vec3 at, WalkEnd how, double distance, NavStatus nav) {
        EndedWalk end = new EndedWalk(target, how, at, distance, nav, now());
        CitizenVitals v = citizen.vitals();
        v.walks().ended(end);
        if (v.keepsHistory()) {
            v.note(HistoryEntry.walkEnded(end));
        }
        EventBus bus = colony.context().bus();
        if (bus.hasListeners(CitizenDebugEvents.WalkEnded.class)) {
            bus.post(new CitizenDebugEvents.WalkEnded(colony, citizen, end));
        }
    }

    @Override
    public void stuck(BlockPos target, Vec3 at, StuckHandler.Action action) {
        long now = now();
        if (action == StuckHandler.Action.TELEPORT) {
            CitizenHurt.stuck(colony, citizen);
        }
        CitizenVitals v = citizen.vitals();
        v.walks().stuck(action, now);
        if (v.keepsHistory()) {
            v.note(HistoryEntry.stuck(now, target, at, action));
        }
        EventBus bus = colony.context().bus();
        if (bus.hasListeners(CitizenDebugEvents.StuckActed.class)) {
            bus.post(new CitizenDebugEvents.StuckActed(colony, citizen, target, at, action));
        }
    }

    private long now() {
        return colony.context().clock().currentTick();
    }
}
