package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.request.Request;
import java.util.Optional;

/**
 * What the courier AI and its steps share (MC AbstractEntityAIBasic's worker, job and building): the courier, its
 * walks, its hut and warehouse, and the work delay (MC setDelay).
 */
final class CourierContext {
    /** MC EntityAIWorkDeliveryman WALK_DELAY: ticks between two checks of a walk. */
    static final int WALK_DELAY = 20;

    private final Colony colony;
    private final DeliverymanJob job;
    private final BodyId body;
    private final BodyWalker walker;
    private int delay;

    CourierContext(Colony colony, DeliverymanJob job, BodyId body) {
        this.colony = colony;
        this.job = job;
        this.body = body;
        this.walker =
                new BodyWalker(colony.context().bodies(), body, colony.context().clock()::currentTick);
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
        return Optional.ofNullable(citizen().workBuilding()).flatMap(colony.buildings()::at);
    }

    /** MC JobDeliveryman.findWareHouse: the warehouse the courier is attached to. */
    Optional<Building> warehouse() {
        return CourierAssignmentModule.warehouseOf(colony, citizen().id());
    }

    /** The head of the courier's own queue, without pulling from the warehouse (see {@link DeliverymanJob#ownTask}). */
    Optional<Request> task() {
        return job.ownTask(colony);
    }

    /** MC walkToBuilding / walkToSafePos: true once there (or once the walk ended anyway). */
    boolean walkTo(BlockPos pos) {
        return walker.walkTo(pos);
    }

    /** MC setDelay: the AI does nothing for {@code ticks}. */
    void setDelay(int ticks) {
        delay = ticks;
    }

    /** MC waitingForSomething: true while a delay runs, which then shrinks by {@code elapsed} ticks. */
    boolean waiting(int elapsed) {
        if (delay <= 0) {
            return false;
        }
        delay -= elapsed;
        return true;
    }

    /** MC setHeldItem(SLOT_HAND): the courier shows what is in its first slot. */
    void showHeld() {
        colony.context().bodies().setHeldItem(body, inventory().slot(0).map(ItemAmount::item));
    }

    /** MC CitizenExperienceHandler.addExperience, split between the hut's skills (Agility, Adaptability). */
    void award(double xp) {
        Optional<Building> hut = hut();
        Optional<WorkerModule> work = hut.flatMap(h -> h.module(WorkerModule.class));
        Skill primary = work.map(WorkerModule::primary).orElse(Skill.Agility);
        Skill secondary = work.map(WorkerModule::secondary).orElse(Skill.Adaptability);
        int homeLevel = Optional.ofNullable(citizen().homeBuilding())
                .flatMap(colony.buildings()::at)
                .map(Building::level)
                .orElse(0);
        JobXp.award(
                citizen(),
                primary,
                secondary,
                xp,
                new JobXp.Levels(hut.map(Building::level).orElse(0), homeLevel));
    }
}
