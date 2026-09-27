package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** The courier AI tests' colony: a warehouse with two racks, a level-5 courier hut and a residence to serve. */
abstract class CourierAITestBase {
    static final ItemKey STONE = new ItemKey("Rock_Stone");
    static final ItemKey LOG = new ItemKey("Wood_Oak_Trunk");
    static final ItemKey DIRT = new ItemKey("Soil_Dirt");
    static final BlockPos RACK = new BlockPos(2, 64, 0);
    static final BlockPos OTHER_RACK = new BlockPos(3, 64, 0);
    final TestContexts t = contexts();
    final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    final RequestManager m = colony.requests();
    final Building warehouse = building(WarehouseBuilding.TYPE, new BlockPos(0, 64, 0), 1);
    final Building hut = building(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0), 5);
    final Building target = building(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 1);
    final CitizenData citizen = new CitizenData(1);
    DeliverymanJob job;
    BodyId body;
    JobAI ai;

    /** The fakes of the colony; a subclass may change their config (called while the base is built). */
    TestContexts contexts() {
        return new TestContexts();
    }

    CourierAITestBase() {
        warehouse.addContainer(RACK);
        warehouse.addContainer(OTHER_RACK);
        t.bodies.instant = true;
    }

    Building building(BuildingType type, BlockPos pos, int level) {
        Building b = Building.create(type, pos, 0);
        b.setLevel(level);
        b.setBuilt(true);
        colony.buildings().add(b);
        return b;
    }

    /** Hires the courier at its hut, attaches it to the warehouse and starts its AI at the hut. */
    void hire(int agility, int adaptability) {
        citizen.skills().set(Skill.Agility, agility, 0);
        citizen.skills().set(Skill.Adaptability, adaptability, 0);
        citizen.setSaturation(CitizenData.MAX_SATURATION); // a starving citizen earns no XP
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        colony.buildings().onColonyTick(colony);
        job = (DeliverymanJob) citizen.job().orElseThrow();
        body = t.bodies.existing(1, 1, Vec3.center(hut.position()));
        ai = job.createAI(colony, body);
    }

    /** Agility 5: an award of a few XP does not level it up (nor hits the cap of a homeless citizen). */
    void hire() {
        hire(5, 0);
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            ai.tick();
        }
    }

    void runUntil(BooleanSupplier done) {
        for (int i = 0; i < 5_000 && !done.getAsBoolean(); i++) {
            run(1);
        }
        assertTrue(done.getAsBoolean(), "never happened; courier in " + ai.stateName());
    }

    void put(BlockPos pos, ItemKey item, int count) {
        t.containers.containers.computeIfAbsent(pos, p -> new LinkedHashMap<>()).put(item, count);
    }

    int stored(BlockPos pos, ItemKey item) {
        return t.containers.containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
    }

    RequestToken delivery(BlockPos from, ItemKey item, int count) {
        return m.createAndAssign(
                warehouse, new Delivery(from, target.requesterId(), new ItemAmount(item, count), 13), -1);
    }

    RequestToken pickup(Building from, int priority) {
        return m.createAndAssign(from, new Pickup(priority, 0, 10), -1);
    }

    boolean completed(RequestToken token) {
        return m.get(token).map(r -> r.state() == RequestState.COMPLETED).orElse(false);
    }

    boolean failed(RequestToken token) {
        return m.get(token)
                .map(r -> r.state() == RequestState.FAILED || r.state() == RequestState.CANCELLED)
                .orElse(true);
    }

    /** The Agility XP of an award of {@code xp}: x1.5 for the level-5 hut, x1.01 for Intelligence 1 (MC). */
    static double awarded(double xp) {
        return xp * 1.5 * 1.01;
    }

    int carried(ItemKey item) {
        return citizen.inventory().count(item);
    }
}
