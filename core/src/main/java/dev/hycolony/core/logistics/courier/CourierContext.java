package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.inventory.HeldItems;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.WorkDelay;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.request.Request;
import java.util.List;
import java.util.Optional;

/**
 * What the courier AI and its steps share (MC AbstractEntityAIBasic's worker, job and building): the courier, its
 * walks, its hut and warehouse, and the work delay (MC setDelay).
 */
final class CourierContext {
    private static final System.Logger LOG = System.getLogger(CourierContext.class.getName());

    /** MC EntityAIWorkDeliveryman WALK_DELAY: ticks between two checks of a walk. */
    static final int WALK_DELAY = 20;

    private final Colony colony;
    private final DeliverymanJob job;
    private final BodyId body;
    private final BodyWalker walker;
    private final BlockApproach approach;
    private final WorkDelay delay = new WorkDelay();
    private boolean warnedLost;

    CourierContext(Colony colony, DeliverymanJob job, BodyId body) {
        this.colony = colony;
        this.job = job;
        this.body = body;
        this.walker = new BodyWalker(
                colony.context().bodies(),
                body,
                colony.context().clock()::currentTick,
                new CitizenWalkReports(colony, job.citizen()));
        this.approach = new BlockApproach(colony.context().ports(), walker);
    }

    Colony colony() {
        return colony;
    }

    DeliverymanJob job() {
        return job;
    }

    BodyId body() {
        return body;
    }

    /** Forgets the walk under way ({@link BlockApproach#forget}): another AI has moved the courier since. */
    void forgetWalk() {
        approach.forget();
    }

    CitizenData citizen() {
        return job.citizen();
    }

    Inventory inventory() {
        return job.citizen().inventory();
    }

    ContainerAccess containers() {
        return colony.context().ports().containers();
    }

    ItemCatalog catalog() {
        return colony.context().ports().catalog();
    }

    /** The courier's hut; empty once it is gone. */
    Optional<Building> hut() {
        return job.hut(colony);
    }

    /** MC JobDeliveryman.findWareHouse: the warehouse the courier is attached to. */
    Optional<Building> warehouse() {
        return CourierAssignmentModule.warehouseOf(colony, citizen().id());
    }

    /** The head of the courier's own queue, without pulling from the warehouse (see {@link DeliverymanJob#ownTask}). */
    Optional<Request> task() {
        return job.ownTask(colony);
    }

    /** MC walkToSafePos: true once beside the block at {@code pos} ({@link BlockApproach}). */
    boolean walkToSafePos(BlockPos pos) {
        return approach.walkToSafePos(pos);
    }

    /** MC walkToBuilding: true once beside the hut block of {@code building} ({@link BlockApproach}). */
    boolean walkToBuilding(Building building) {
        return approach.walkToBuilding(building);
    }

    /** MC setDelay: the ticks the courier waits before its next step. */
    WorkDelay delay() {
        return delay;
    }

    /**
     * Puts {@code amount} back into {@code containers} (items taken out that found no place). What still does not fit
     * is dropped on the ground there and logged, WARNING the first time then FINE, so it is never lost silently.
     */
    void putBack(List<BlockPos> containers, ItemAmount amount) {
        ItemAmount left = containers().insert(containers, amount);
        if (left == null || containers.isEmpty()) {
            return;
        }
        LOG.log(
                warnedLost ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
                "Courier {0} could not put back {1} x {2} at {3}; dropped",
                citizen().name(),
                left.count(),
                left.item().id(),
                containers.getFirst());
        warnedLost = true;
        colony.context().ports().blocks().drop(containers.getFirst(), List.of(left));
    }

    /** MC setHeldItem(SLOT_HAND): the courier holds its first slot and shows what is in it. */
    void showHeld() {
        HeldItems.holdSlot(citizen(), colony.context().bodies(), body, 0);
    }

    /** MC CitizenExperienceHandler.addExperience, split between the hut's skills (Agility, Adaptability). */
    void award(double xp) {
        Optional<Building> hut = hut();
        Optional<WorkerModule> work = hut.flatMap(h -> h.module(WorkerModule.class));
        Skill primary = work.map(WorkerModule::primary).orElse(Skill.Agility);
        Skill secondary = work.map(WorkerModule::secondary).orElse(Skill.Adaptability);
        JobXp.award(
                citizen(),
                primary,
                secondary,
                xp,
                JobXp.levels(colony, citizen(), hut.map(Building::level).orElse(0)));
    }
}
