package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.CitizenAI;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.CitizenState;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import dev.hycolony.core.testing.food.FakeDiningHall;
import dev.hycolony.core.testing.food.FakeEatingRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A hungry citizen eating: MC CitizenAI.shouldEat then EntityAIEatTask, end to end through its AI. */
class EatAITest {
    private static final int MAX_TICKS = 6000;
    private static final double EPS = 1e-9;

    private final TestContexts t = contexts();
    private final ItemKey apple = t.catalog.food("apple", 4, 0);
    private final ItemKey pie = t.catalog.food("pie", 12, 3);
    private final CitizenData citizen = new CitizenData(1);
    private final BodyId body = t.bodies.existing(1, 1, new Vec3(0, 64, 0));
    private final Colony colony = colony();
    private CitizenAI ai;
    /** Job AIs created for {@link #counting}'s workers. */
    private int jobAis;

    private final JobType counting = new JobType("test:counting", c -> new CountingJob(this.counting, c));

    /** A job whose AIs are counted: a dropped job AI is created anew. */
    private final class CountingJob extends Job {
        CountingJob(JobType type, CitizenData citizen) {
            super(type, citizen);
        }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            jobAis++;
            return new JobAI() {
                @Override
                public void tick() {}

                @Override
                public String stateName() {
                    return "working";
                }

                @Override
                public boolean canBeInterrupted() {
                    return true;
                }
            };
        }
    }

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.bodies.instant = true;
        return t;
    }

    private Colony colony() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        c.citizens().restore(citizen);
        c.citizens().onBodyLoaded(body, citizen.id());
        return c;
    }

    private Building hut(String id, BlockPos pos, ModuleProducer module) {
        Building b = Building.create(new BuildingType(id, id, 5, List.of(module)), pos, 0);
        b.setLevel(1);
        b.setBuilt(true);
        colony.buildings().add(b);
        return b;
    }

    /** A dining hall at {@code pos} with {@code pie} on its menu, five of them in stock. */
    private FakeDiningHall hall(BlockPos pos) {
        FakeDiningHall[] hall = new FakeDiningHall[1];
        Building b = hut("test:hall", pos, new ModuleProducer("hall", () -> {
            hall[0] = new FakeDiningHall();
            return hall[0];
        }));
        hall[0].menu.add(pie);
        stock(b.position(), pie, 5);
        return hall[0];
    }

    private void stock(BlockPos container, ItemKey item, int count) {
        Map<ItemKey, Integer> content = new HashMap<>();
        content.put(item, count);
        t.containers.containers.put(container, content);
    }

    private void tickUntil(BooleanSupplier done) {
        for (int i = 0; i < MAX_TICKS && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), "not reached in " + MAX_TICKS + " ticks; state " + ai.state());
    }

    private void start(double saturation) {
        citizen.setSaturation(saturation);
        ai = new CitizenAI(colony, citizen, body);
    }

    @Test
    void aHungryCitizenEatsWhatItCarriesUntilFull() {
        citizen.inventory().insert(new ItemAmount(apple, 20), _ -> 64);
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        tickUntil(() -> ai.state() == CitizenState.IDLE);
        assertEquals(60, citizen.saturation(), EPS);
        assertEquals(5, citizen.inventory().count(apple)); // 15 apples from 2 to 62, capped at 60
        assertTrue(citizen.hunger().justAte());
        assertEquals(List.of(apple), citizen.hunger().history().foods());
        assertTrue(t.effects.meals.size() >= 15);
    }

    @Test
    void anEatingCitizenHoldsTheSlotOfItsFood() {
        citizen.inventory().set(5, Optional.of(new ItemAmount(apple, 20)));
        start(2);

        tickUntil(() -> apple.equals(t.bodies.bodies.get(body).held));

        assertEquals(5, citizen.equipment().held(CitizenEquipment.Hand.MAIN), "MC setHeldItem(MAIN_HAND, foodSlot)");
    }

    @Test
    void aCitizenAboveTwoAndAHalfDoesNotGoEating() {
        citizen.inventory().insert(new ItemAmount(apple, 20), _ -> 64);
        start(3);
        for (int i = 0; i < 200; i++) {
            ai.tick();
        }
        assertFalse(ai.state() == CitizenState.EATING);
    }

    @Test
    void withoutFoodItFetchesAStackAtItsWorkHut() {
        Building hut = hut("test:hut", new BlockPos(10, 64, 0), new ModuleProducer("rule", FakeEatingRule::new));
        stock(hut.position(), apple, 10);
        citizen.setWorkBuilding(hut.position());
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        tickUntil(() -> ai.state() == CitizenState.IDLE);
        assertEquals(42, citizen.saturation(), EPS); // all 10 apples: the slot ran empty before full
        assertEquals(0, t.containers.count(hut.containers(), apple));
    }

    @Test
    void theWorkHutKeepsWhatItRefusesToFeed() {
        Building hut = hut("test:hut", new BlockPos(10, 64, 0), new ModuleProducer("rule", FakeEatingRule::new));
        hut.module(FakeEatingRule.class).orElseThrow().refused = apple;
        stock(hut.position(), apple, 10);
        citizen.setWorkBuilding(hut.position());
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        for (int i = 0; i < 2000; i++) {
            ai.tick();
        }
        assertEquals(CitizenState.EATING, ai.state()); // nothing to eat, no dining hall: it keeps looking
        assertEquals(10, t.containers.count(hut.containers(), apple));
        assertEquals(2, citizen.saturation(), EPS);
    }

    @Test
    void aWorkerFedFullWhileEatingGoesStraightBackToWork() {
        citizen.setJob(TestJobs.TYPE.factory().apply(citizen));
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        citizen.setSaturation(60); // fed by a player meanwhile
        tickUntil(() -> ai.state() != CitizenState.EATING);
        assertEquals(CitizenState.WORKING, ai.state()); // MC: the same decision picks work, without an IDLE step
    }

    @Test
    void goingToEatDropsTheJobAiSoWorkStartsAfresh() {
        citizen.setJob(counting.factory().apply(citizen));
        citizen.inventory().insert(new ItemAmount(apple, 20), _ -> 64);
        start(60);
        tickUntil(() -> ai.state() == CitizenState.WORKING);
        assertEquals(1, jobAis);

        citizen.setSaturation(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        tickUntil(() -> ai.state() == CitizenState.WORKING);

        assertEquals(2, jobAis); // MC resetAI on leaving WORK
    }

    @Test
    void withoutFoodNorHallAFedEnoughCitizenGivesUp() {
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        citizen.setSaturation(10); // fed by someone else meanwhile
        tickUntil(() -> ai.state() == CitizenState.IDLE);
        assertTrue(citizen.hunger().justAte());
    }

    @Test
    void atADiningHallItSitsWaitsThenServesItselfFromTheMenu() {
        FakeDiningHall hall = hall(new BlockPos(20, 64, 0));
        BlockPos seat = new BlockPos(21, 64, 0);
        hall.seats.add(seat);
        t.bodies.seats.add(seat);
        start(2);
        tickUntil(() -> t.bodies.bodies.get(body).seat != null);
        assertTrue(hall.customers.contains(citizen.id()));
        tickUntil(() -> ai.state() == CitizenState.IDLE);
        // (60 - 2) / 24 = 2 pies needed, 3 taken (1.5 x); eaten while below 60: 26, 50, then full.
        assertEquals(60, citizen.saturation(), EPS);
        assertEquals(0, citizen.inventory().count(pie));
        assertEquals(
                2,
                t.containers.count(
                        colony.buildings()
                                .at(new BlockPos(20, 64, 0))
                                .orElseThrow()
                                .containers(),
                        pie));
        assertEquals(List.of(pie), citizen.hunger().history().foods()); // the first noted at once, not twice
        assertEquals(null, t.bodies.bodies.get(body).seat);
    }

    @Test
    void atAHallWithoutSeatsItWaitsToBeServed() {
        FakeDiningHall hall = hall(new BlockPos(20, 64, 0));
        start(2);
        tickUntil(() -> hall.customers.contains(citizen.id()));
        for (int i = 0; i < 2 * DiningVisit.WAITING_TRANSITIONS * EatAI.TICK_INTERVAL; i++) {
            t.clock.tick++;
            ai.tick();
        }
        // MC waitForFood: no seat, no self-service; it waits for the waiter, in the hall
        assertEquals(CitizenState.EATING, ai.state());
        assertEquals(0, citizen.inventory().count(pie));

        citizen.inventory().insert(new ItemAmount(pie, 3), _ -> 64); // served
        tickUntil(() -> ai.state() == CitizenState.IDLE);
        assertEquals(60, citizen.saturation(), EPS);
    }

    @Test
    void itPrefersAHallWithAWaiterOverACloserOne() {
        FakeDiningHall unstaffed = hall(new BlockPos(20, 64, 0));
        FakeDiningHall staffed = hall(new BlockPos(40, 64, 0));
        staffed.waiter = true;
        start(2);
        tickUntil(() -> !staffed.customers.isEmpty() || !unstaffed.customers.isEmpty());
        assertTrue(staffed.customers.contains(citizen.id())); // MC searchRestaurant: STAFFED_RESTAURANTS first
        assertTrue(unstaffed.customers.isEmpty());
    }

    @Test
    void goingToSleepEndsTheMeal() {
        start(2);
        tickUntil(() -> ai.state() == CitizenState.EATING);
        citizen.setHomeBuilding(new BlockPos(5, 64, 0));
        t.clock.daytime = false;
        t.clock.dayTime = 13000; // past nightfall: it is late to go to bed
        tickUntil(() -> ai.state() == CitizenState.SLEEP);
    }
}
