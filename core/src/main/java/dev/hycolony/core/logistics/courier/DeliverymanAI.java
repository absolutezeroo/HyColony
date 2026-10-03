package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.WorkerMachine;
import dev.hycolony.core.kernel.ai.IStateSupplier;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.warehouse.WarehouseStorage;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;

/**
 * The courier's work AI (MC EntityAIWorkDeliveryman): decides at its warehouse every {@link #DECISION_DELAY} ticks,
 * then loads and delivers ({@link DeliveryPreparation}, {@link DeliveryDrop}), empties a hut ({@link PickupRound}),
 * or stores what it carries at the warehouse. Unlike other workers, it has no generic inventory dump (MC excludes
 * JobDeliveryman from inventoryNeedsDump).
 */
final class DeliverymanAI implements JobAI {
    /** MC DECISION_DELAY: ticks between two decisions in START_WORKING. */
    static final int DECISION_DELAY = 100;
    /** MC STANDARD_DELAY and PICKUP_DELAY. */
    static final int STANDARD_DELAY = 5;
    /** MC TICKS_SECOND: the DUMPING rate. */
    static final int DUMP_DELAY = 20;
    /** MC CitizenConstants.BASE_MOVEMENT_SPEED. */
    static final double BASE_MOVEMENT_SPEED = 0.3;
    /** MC JobDeliveryman.BONUS_SPEED_PER_LEVEL, per level of the hut's primary skill (Agility). */
    static final double BONUS_SPEED_PER_LEVEL = 0.003;

    private final CourierContext ctx;
    private final WorkerMachine<CourierState> machine;
    private double speed = -1;

    DeliverymanAI(Colony colony, DeliverymanJob job, BodyId body) {
        this.ctx = new CourierContext(colony, job, body);
        DeliveryPreparation preparation = new DeliveryPreparation(ctx);
        DeliveryDrop drop = new DeliveryDrop(ctx);
        PickupRound pickup = new PickupRound(ctx);
        this.machine = new WorkerMachine<>(
                CourierState.IDLE,
                () -> "courier " + ctx.citizen().name(),
                () -> ctx.delay().waiting(WorkerMachine.MACHINE_RATE),
                ctx.delay()::set);
        target(CourierState.IDLE, () -> CourierState.START_WORKING, 1);
        machine.target(CourierState.START_WORKING, this::checkIfExecute, this::decide, DECISION_DELAY);
        target(CourierState.PREPARE_DELIVERY, preparation::prepare, STANDARD_DELAY);
        target(CourierState.DELIVERY, drop::deliver, STANDARD_DELAY);
        target(CourierState.PICKUP, pickup::pickup, STANDARD_DELAY);
        target(CourierState.DUMPING, this::dump, DUMP_DELAY);
        applySpeed();
    }

    private void target(CourierState s, IStateSupplier<CourierState> action, int rate) {
        machine.target(s, action, rate);
    }

    @Override
    public void tick() {
        machine.tick();
    }

    @Override
    public int failures() {
        return machine.failures();
    }

    @Override
    public String stateName() {
        return machine.state().name();
    }

    /** Without a task, it waits at the warehouse for one (MC decide). */
    @Override
    public boolean waiting() {
        return machine.state() == CourierState.START_WORKING
                && ctx.job().taskQueue().isEmpty();
    }

    /** Preparing, delivering or picking up works on the head of its queue. */
    @Override
    public boolean servesQueueHead() {
        return switch (machine.state()) {
            case PREPARE_DELIVERY, DELIVERY, PICKUP -> true;
            default -> false;
        };
    }

    @Override
    public List<RequestToken> queue() {
        return ctx.job().taskQueue();
    }

    /** MC isOkayToEat of each registered target. */
    @Override
    public boolean canBeInterrupted() {
        return switch (machine.state()) {
            case DELIVERY, DUMPING -> false;
            default -> true;
        };
    }

    /** MC AbstractAISkeleton.resetAI: back to its first state, its walk forgotten; its speed and tasks stay. */
    @Override
    public void resetAI() {
        machine.reset();
        ctx.forgetWalk();
    }

    /** True without a hut. The rain rule is every worker's, in {@code CitizenAI} (MC CitizenAI.calculateNextState). */
    @Override
    public boolean canGoIdle() {
        return ctx.hut().isEmpty();
    }

    /**
     * MC checkIfExecute: works only with a warehouse ({@code setWorking}, which also restarts the inactivity timer).
     * Deviation from MC: no "no warehouse" chat interaction (no interaction system yet).
     */
    private boolean checkIfExecute() {
        boolean hasWarehouse = ctx.warehouse().isPresent();
        ctx.job().setWorking(ctx.colony(), hasWarehouse);
        applySpeed();
        return hasWarehouse;
    }

    /**
     * MC decide: no task, wait at the warehouse (storing what is carried); a delivery starts empty-handed.
     *
     * <p>A full warehouse keeps a loaded courier cycling DUMPING and START_WORKING, its deliveries pending: a
     * documented exception to CLAUDE.md § 4, as in MC. The way out is a player action (freeing room), which the
     * "warehouse full" message announces every 5 minutes ({@link WarehouseStorage#TICKS_FIVE_MIN}).
     */
    private CourierState decide() {
        Optional<Request> task = ctx.job().currentTask(ctx.colony());
        boolean empty = ctx.inventory().freeSlots() == ctx.inventory().size();
        if (task.isEmpty()) {
            Building warehouse = ctx.warehouse().orElse(null);
            if (warehouse == null || !ctx.walkToBuilding(warehouse)) {
                ctx.delay().set(CourierContext.WALK_DELAY);
                return CourierState.START_WORKING;
            }
            return empty ? CourierState.START_WORKING : CourierState.DUMPING;
        }
        if (task.get().requestable() instanceof Delivery) {
            return empty ? CourierState.PREPARE_DELIVERY : CourierState.DUMPING;
        }
        return CourierState.PICKUP;
    }

    /** MC dump: walks to the warehouse and stores everything it can (MC dumpInventoryIntoWareHouse). */
    private CourierState dump() {
        Building warehouse = ctx.warehouse().orElse(null);
        if (warehouse == null) {
            return CourierState.START_WORKING;
        }
        if (!ctx.walkToBuilding(warehouse)) {
            ctx.delay().set(CourierContext.WALK_DELAY);
            return CourierState.DUMPING;
        }
        warehouse
                .module(WarehouseStorage.class)
                .ifPresent(storage -> storage.store(ctx.colony(), warehouse, ctx.inventory()));
        ctx.showHeld();
        return CourierState.START_WORKING;
    }

    /**
     * MC JobDeliveryman.onLevelUp: the base speed plus {@link #BONUS_SPEED_PER_LEVEL} per level of the primary skill,
     * given to the body as a factor of its base speed. Set again only when it changes.
     */
    private void applySpeed() {
        Skill primary = ctx.hut()
                .flatMap(hut -> hut.module(WorkerModule.class))
                .map(WorkerModule::primary)
                .orElse(Skill.Agility);
        double now = speedFactor(ctx.citizen().skills().level(primary));
        if (now != speed) {
            speed = now;
            ctx.colony().context().bodies().setMovementSpeed(ctx.body(), now);
        }
    }

    /** The walking speed of a courier with {@code level} in its primary skill, as a factor of the base speed. */
    static double speedFactor(int level) {
        return (BASE_MOVEMENT_SPEED + level * BONUS_SPEED_PER_LEVEL) / BASE_MOVEMENT_SPEED;
    }
}
