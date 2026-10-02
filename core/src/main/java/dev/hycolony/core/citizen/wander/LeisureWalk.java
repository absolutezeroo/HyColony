package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.nav.DangerousCells;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's leisure walk, MC EntityAICitizenWander's GO_TO_LEISURE_SITE then WANDER_AT_LEISURE_SITE: to the site,
 * then strolls in its building until it leaves, one time in 300.
 *
 * <p>Deviation from MC: a sub-state of IDLE, not states of their own; no tagged seats nor stands (HyColony's plans
 * have no tags) nor reading; the stroll targets a random spot of the building's box, without a path search; the walk
 * to the site is given up after {@link CitizenWander#WANDER_TIMEOUT_TICKS} (MC's stuck handler teleports).
 */
final class LeisureWalk {
    /** MC goToLeisureSite: walkToPos(citizen, leisureSite, 3, true), arrived within that many blocks. */
    private static final int ARRIVED_BLOCKS = 3;
    /** MC wanderAtLeisureSite: nextInt(60 * 5) < 1 leaves the site. */
    private static final int LEAVE_BOUND = 60 * 5;
    /** MC wanderAtLeisureSite: nextInt(10) <= 0 strolls to a random spot of the building. */
    private static final int STROLL_BOUND = 10;
    /** MC wanderAtLeisureSite: setCurrentDelay(30) after starting a stroll. */
    static final int STROLL_DELAY_TICKS = 30;

    private static final long NOT_STARTED = -1;

    private final Colony colony;
    private final BodyId body;
    private final CitizenBodies bodies;
    private final RandomGenerator random;
    private final IntConsumer delay;
    private final DangerousCells danger;
    private @Nullable BlockPos site;
    private boolean atSite;
    /** The tick the walk to the site was first ticked, {@link #NOT_STARTED} before. */
    private long goingSince = NOT_STARTED;

    LeisureWalk(Colony colony, BodyId body, IntConsumer delay, DangerousCells danger) {
        this.colony = colony;
        this.body = body;
        this.bodies = colony.context().bodies();
        this.random = colony.context().random();
        this.delay = delay;
        this.danger = danger;
    }

    boolean active() {
        return site != null;
    }

    /** MC decide: off to {@code to}. */
    void start(BlockPos to) {
        site = to;
        atSite = false;
        goingSince = NOT_STARTED;
    }

    /** Back to plain wandering (MC: the state machine back in IDLE). */
    void stop() {
        site = null;
        atSite = false;
    }

    /** One leisure step, every 20 ticks (MC's GO_TO_LEISURE_SITE and WANDER_AT_LEISURE_SITE transitions). */
    void tick() {
        BlockPos to = site;
        if (to == null) {
            return;
        }
        if (atSite) {
            strollAt(to);
        } else {
            goTo(to);
        }
    }

    /** MC goToLeisureSite: walks to the site until within {@link #ARRIVED_BLOCKS}; gives up after the timeout. */
    private void goTo(BlockPos to) {
        long now = colony.context().clock().currentTick();
        if (goingSince == NOT_STARTED) {
            goingSince = now;
        }
        Optional<Vec3> here = bodies.position(body);
        if (here.isPresent() && within(here.get().toBlockPos(), to)) {
            atSite = true;
        } else if (now - goingSince >= CitizenWander.WANDER_TIMEOUT_TICKS) {
            stop();
        } else if (bodies.navStatus(body) != NavStatus.MOVING) {
            bodies.moveTo(body, centre(to));
        }
    }

    /** MC wanderAtLeisureSite: leaves one time in 300, or if the site is no building; else strolls one time in 10. */
    private void strollAt(BlockPos to) {
        Optional<Building> building = colony.buildings().at(to);
        if (random.nextInt(LEAVE_BOUND) < 1 || building.isEmpty()) {
            stop();
            return;
        }
        if (random.nextInt(STROLL_BOUND) <= 0 && bodies.navStatus(body) != NavStatus.MOVING) {
            HutFootprint.Box box = HutFootprint.of(colony.context().ports(), building.get());
            int x = box.min().x() + random.nextInt(box.max().x() - box.min().x() + 1);
            int z = box.min().z() + random.nextInt(box.max().z() - box.min().z() + 1);
            BlockPos spot = new BlockPos(x, to.y(), z);
            if (!danger.near(spot, CitizenWander.WANDER_DANGER_HALF_HEIGHT)) { // MC PathJobRandomPos avoids danger
                bodies.moveTo(body, centre(spot));
                delay.accept(STROLL_DELAY_TICKS);
            }
        }
    }

    private static boolean within(BlockPos a, BlockPos b) {
        long dx = a.x() - b.x();
        long dy = a.y() - b.y();
        long dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz <= (long) ARRIVED_BLOCKS * ARRIVED_BLOCKS;
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }
}
