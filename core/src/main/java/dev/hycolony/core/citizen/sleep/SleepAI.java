package dev.hycolony.core.citizen.sleep;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.citizen.home.HomePosition;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.colony.HutFootprint;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.ai.AITarget;
import dev.hycolony.core.kernel.ai.IState;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.ai.TickRateStateMachine;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A citizen in SLEEP: walks home, finds its bed and sleeps there. Port of MC EntityAISleep: its bed is the one of its
 * rank among the residents, else it stands in its home; without a home, by the town hall, never asleep.
 */
public final class SleepAI {
    private static final System.Logger LOG = System.getLogger(SleepAI.class.getName());
    /** MC EntityAISleep: the SLEEP state's first transition (checkSleep) runs after 20 ticks. */
    private static final int INIT_TICKS = 20;
    /** MC EntityAISleep: WALKING_HOME, FIND_BED and SLEEPING (TICK_INTERVAL) run every 30 ticks. */
    private static final int TICK_INTERVAL = 30;
    /** MC CitizenConstants.RANGE_TO_BE_HOME: a homeless citizen is home within this squared distance (4 blocks). */
    static final int RANGE_TO_BE_HOME = 16;
    /** MC EntityAISleep.MAX_BED_TICKS: arrivals at the bed before sleeping without it. */
    static final int MAX_BED_TICKS = 10;
    /** MC EntityAISleep: walkToPosInBuilding(citizen, usedBed, home, 12). */
    private static final int IN_BUILDING_REACH = 12;
    /** MC EntityAISleep.sleep: a citizen this far from its bed (squared, 3 blocks) walks back. */
    private static final int BED_RANGE_SQ = 9;

    /** MC EntityAISleep.SleepState, plus the SLEEP state's first transition (checkSleep). */
    public enum State implements IState {
        INIT,
        WALKING_HOME,
        FIND_BED,
        SLEEPING
    }

    private final Colony colony;
    private final CitizenData data;
    private final BodyId body;
    private final SleepHandler handler;
    private final CitizenBodies bodies;
    private final GamePorts ports;
    private final BlockApproach walker;
    private final TickRateStateMachine<State> machine;
    private @Nullable BlockPos usedBed;
    private int bedTicks;
    private boolean failed;

    public SleepAI(Colony colony, CitizenData data, BodyId body, SleepHandler handler) {
        this.colony = colony;
        this.data = data;
        this.body = body;
        this.handler = handler;
        this.bodies = colony.context().bodies();
        this.ports = colony.context().ports();
        this.walker = new BlockApproach(
                ports,
                new BodyWalker(
                        bodies, body, colony.context().clock()::currentTick, new CitizenWalkReports(colony, data)));
        this.machine = new TickRateStateMachine<>(State.INIT, this::onException);
        machine.addTransition(new AITarget<>(State.INIT, (IStateSupplier<State>) this::checkSleep, INIT_TICKS));
        machine.addTransition(
                new AITarget<>(State.WALKING_HOME, (IStateSupplier<State>) this::walkHome, TICK_INTERVAL));
        machine.addTransition(new AITarget<>(State.FIND_BED, this::findBed, () -> State.SLEEPING, TICK_INTERVAL));
        machine.addTransition(new AITarget<>(State.SLEEPING, (IStateSupplier<State>) this::sleep, TICK_INTERVAL));
    }

    public void tick() {
        machine.tick();
    }

    public State state() {
        return machine.getState();
    }

    /** As CitizenAI's handler: the first exception is a WARNING, the next ones DEBUG; the state stays. */
    private void onException(RuntimeException e) {
        LOG.log(
                failed ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                "Sleep AI failed for " + data.name(),
                e);
        failed = true;
    }

    /** MC checkSleep: a fresh search, then home. */
    private State checkSleep() {
        usedBed = null;
        bedTicks = 0;
        return State.WALKING_HOME;
    }

    /** MC walkHome: FIND_BED once inside the home (or within 4 blocks of the town hall without one), else walks on. */
    private @Nullable State walkHome() {
        BlockPos at = here().orElse(null);
        if (at == null) {
            return null;
        }
        Optional<Building> home = home();
        if (home.isEmpty()) {
            Optional<BlockPos> townHall = HomePosition.of(colony, data);
            if (townHall.isPresent() && townHall.get().distSq(at) <= RANGE_TO_BE_HOME) {
                return State.FIND_BED;
            }
            townHall.ifPresent(walker::walkToSafePos); // MC walkToPos(homePosition, 4, true)
            return State.WALKING_HOME;
        }
        if (HutFootprint.isInBuilding(ports, home.get(), at)) {
            return State.FIND_BED;
        }
        walker.walkToBuilding(home.get());
        return State.WALKING_HOME;
    }

    /** MC findBed: tries to lie down until asleep or MAX_BED_TICKS arrivals. */
    private boolean findBed() {
        if (!data.asleep() && bedTicks < MAX_BED_TICKS) {
            findBedAndTryToSleep();
            return false;
        }
        return true;
    }

    /**
     * MC findBedAndTryToSleep: picks the bed of its rank (again while it has none or only the hut), walks to it, and at
     * each arrival tries to lie down; nothing without a home.
     */
    private void findBedAndTryToSleep() {
        Building hut = home().orElse(null);
        if (hut == null) {
            return;
        }
        BlockPos homePos = hut.position();
        if (usedBed == null || usedBed.equals(homePos)) {
            Optional<BlockPos> pick = pickBed(hut, homePos);
            if (pick.isEmpty()) {
                return; // MC: a stale bed was just removed; the next attempt picks again
            }
            usedBed = pick.get();
            if (!usedBed.equals(homePos)) {
                return; // MC: the bed is chosen; the walk starts at the next attempt
            }
        }
        if (walker.walkToPosInBuilding(usedBed, hut, IN_BUILDING_REACH)) {
            bedTicks++;
            // MC: a bed someone sleeps in falls back to the hut, which is no bed; sleepIn refuses both alike.
            if (!handler.trySleep(usedBed)) {
                data.setBedPos(null);
                usedBed = null;
            }
            HappinessEvents.reachedBed(data); // MC: at every arrival, a bed lain in or not
        } else {
            bedTicks = 0;
        }
    }

    /**
     * The bed of the citizen's rank among the residents if it is still a bed with room above (MC: a bed, a panel, a
     * trapdoor or no solid block); the hut without one, or when its block is not loaded; empty once a bed that is no
     * bed any more was removed (MC removeBed).
     */
    private Optional<BlockPos> pickBed(Building hut, BlockPos homePos) {
        int rank = hut.module(LivingModule.class)
                .map(l -> l.residents().indexOf(data.id()))
                .orElse(-1);
        BedModule beds = hut.module(BedModule.class).orElse(null);
        BlockPos bed = beds == null ? null : beds.bed(rank).orElse(null);
        BlockState state = bed == null ? null : ports.blocks().get(bed).orElse(null);
        if (beds == null || bed == null || state == null) {
            return Optional.of(homePos);
        }
        if (!ports.catalog().isBed(state.key())) {
            beds.removeBed(bed);
            colony.markDirty();
            return Optional.empty();
        }
        boolean free = ports.blocks()
                .get(bed.offset(0, 1, 0))
                .map(a -> ports.catalog().isBed(a.key()) || ports.catalog().kind(a.key()) != BlockKind.SOLID)
                .orElse(true);
        return Optional.of(free ? bed : homePos);
    }

    /**
     * MC sleep: a citizen more than 3 blocks from its bed walks back to it, its bed and arrivals kept; without a bed it
     * tries again; zZz particles either way.
     *
     * <p>Deviation from MC: MC only re-applies the sleeping pose by the bed; here a citizen by its bed but not lying
     * lies down again in full (trySleep: asleep, bed, announce, held item). Hytale can get a sleeper out of its bed
     * without us (players skipping the night, a broken bed, a teleport, see {@link CitizenBodies#isInBed}): it is
     * awake then, without the wake-up hooks, until it lies down again.
     */
    private @Nullable State sleep() {
        BlockPos at = here().orElse(null);
        if (at == null) {
            return null;
        }
        BlockPos bed = usedBed;
        if (bed != null) {
            if (data.asleep() && !bodies.isInBed(body)) {
                bodies.wakeUp(body); // its sleeping pose ends, though Hytale already got it off the bed
                handler.leftBed();
            }
            if (bed.distSq(at) > BED_RANGE_SQ) {
                if (data.asleep()) {
                    bodies.wakeUp(body); // gets a displaced body off its bed
                    handler.leftBed();
                }
                return State.WALKING_HOME;
            }
            if (!data.asleep() && !handler.trySleep(bed)) {
                data.setBedPos(null);
                usedBed = null;
            }
        } else {
            findBedAndTryToSleep();
        }
        ports.effects().sleeping(new Vec3(at.x() + 0.5, at.y() + 1.0, at.z() + 0.5)); // MC: one block above it
        return null;
    }

    private Optional<Building> home() {
        return Optional.ofNullable(data.homeBuilding()).flatMap(colony.buildings()::at);
    }

    private Optional<BlockPos> here() {
        return bodies.position(body).map(Vec3::toBlockPos);
    }
}
