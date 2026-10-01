package dev.hycolony.core.citizen.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A citizen walking home, finding its bed and sleeping: MC EntityAISleep. */
class SleepAITest {
    private static final BlockPos HOUSE = new BlockPos(20, 64, 0);
    private static final BlockPos BED_A = new BlockPos(21, 64, 0);
    private static final BlockPos BED_B = new BlockPos(19, 64, 1);
    private static final BlockKey BED = new BlockKey("bed");
    private static final BlockKey STONE = new BlockKey("stone");
    private static final int MAX_TICKS = 3000;

    private final TestContexts t = contexts();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final Building house = house();

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.bodies.instant = true;
        t.catalog.beds.add(BED);
        t.catalog.kinds.put(BED, BlockKind.NON_SOLID);
        t.catalog.kinds.put(STONE, BlockKind.SOLID);
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                return Optional.of(new Blueprint("k", List.of(), new BlockPos(-3, 0, -3), new BlockPos(3, 4, 3)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        return t;
    }

    private Building house() {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, HOUSE, 0);
        b.setLevel(2);
        b.setBuilt(true);
        b.setStyle("s");
        c.buildings().add(b);
        return b;
    }

    private void bed(BlockPos pos) {
        house.module(BedModule.class).orElseThrow().addBed(pos);
        t.blocks.blocks.put(pos, new BlockState(BED, 0));
        t.bodies.beds.add(pos);
    }

    /** A resident (ranked in assignment order) whose body stands at {@code at}. */
    private Sleeper resident(int id, BlockPos at) {
        CitizenData d = new CitizenData(id);
        c.citizens().restore(d);
        assertTrue(house.module(LivingModule.class).orElseThrow().assign(c, house, d));
        return sleeper(d, at);
    }

    private Sleeper sleeper(CitizenData d, BlockPos at) {
        BodyId body = t.bodies.spawn(new WorldKey("world"), at, 1, d.id(), "C").orElseThrow();
        t.bodies.bodies.get(body).position = Vec3.center(at);
        return new Sleeper(d, body, new SleepAI(c, d, body, new SleepHandler(c, d, body)));
    }

    private record Sleeper(CitizenData data, BodyId body, SleepAI ai) {}

    private void tickUntil(Sleeper s, BooleanSupplier done) {
        for (int i = 0; i < MAX_TICKS && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            s.ai().tick();
        }
    }

    private void tick(Sleeper s, int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            s.ai().tick();
        }
    }

    @Test
    void walksHomeThenLiesInTheBedOfItsRank() {
        bed(BED_A);
        bed(BED_B);
        resident(1, new BlockPos(0, 64, 0));
        Sleeper second = resident(2, new BlockPos(0, 64, 0));

        tickUntil(second, () -> second.data().asleep() && second.ai().state() == SleepAI.State.SLEEPING);

        assertEquals(SleepAI.State.SLEEPING, second.ai().state()); // MC: FIND_BED ends the round after lying down
        assertEquals(BED_B, second.data().bedPos());
        assertEquals(BED_B, t.bodies.bodies.get(second.body()).inBed);
    }

    @Test
    void rankPastTheBedsSleepsStandingInTheHut() {
        bed(BED_A);
        resident(1, HOUSE);
        Sleeper second = resident(2, HOUSE);

        tickUntil(second, () -> second.ai().state() == SleepAI.State.SLEEPING);
        tick(second, 60);

        assertEquals(SleepAI.State.SLEEPING, second.ai().state());
        assertFalse(second.data().asleep());
        assertFalse(t.effects.sleeps.isEmpty(), "zZz even standing (MC EntityAISleep.sleep)");
    }

    @Test
    void bedNoLongerABedIsRemovedWithoutWalkingAndTheRankIsRecounted() {
        bed(BED_A);
        bed(BED_B);
        t.blocks.blocks.put(BED_A, new BlockState(STONE, 0));
        Sleeper first = resident(1, HOUSE);

        tickUntil(first, () -> first.data().asleep());

        assertEquals(List.of(BED_B), house.module(BedModule.class).orElseThrow().beds());
        assertEquals(BED_B, first.data().bedPos());
    }

    @Test
    void solidBlockAboveRefusesTheBed() {
        bed(BED_A);
        t.blocks.blocks.put(BED_A.offset(0, 1, 0), new BlockState(STONE, 0));
        Sleeper first = resident(1, HOUSE);

        tickUntil(first, () -> first.ai().state() == SleepAI.State.SLEEPING);

        assertFalse(first.data().asleep());
        assertNull(t.bodies.bodies.get(first.body()).inBed);
    }

    @Test
    void takenBedFallsBackAndClearsTheBedPos() {
        bed(BED_A);
        t.bodies.takenBeds.add(BED_A);
        Sleeper first = resident(1, HOUSE);

        tickUntil(first, () -> first.ai().state() == SleepAI.State.SLEEPING);

        assertFalse(first.data().asleep());
        assertNull(first.data().bedPos());
    }

    @Test
    void farFromItsBedWalksBack() {
        bed(BED_A);
        Sleeper first = resident(1, HOUSE);
        tickUntil(first, () -> first.data().asleep());

        t.bodies.bodies.get(first.body()).position = Vec3.center(new BlockPos(60, 64, 0));
        tickUntil(first, () -> first.ai().state() == SleepAI.State.WALKING_HOME);

        assertFalse(first.data().asleep());
        tickUntil(first, () -> first.data().asleep());
        assertEquals(BED_A, first.data().bedPos());
    }

    @Test
    void bedLeftWithoutUsLiesDownAgain() {
        bed(BED_A);
        Sleeper first = resident(1, HOUSE);
        tickUntil(first, () -> first.data().asleep());

        t.bodies.bodies.get(first.body()).inBed = null; // a broken bed, or players skipping the night
        tickUntil(first, () -> !first.data().asleep());
        assertEquals(SleepAI.State.WALKING_HOME, first.ai().state());

        tickUntil(first, () -> first.data().asleep());
        assertEquals(BED_A, t.bodies.bodies.get(first.body()).inBed);
    }

    @Test
    void homelessStandsByTheTownHall() {
        Building hall = Building.create(BuildingTypes.TOWN_HALL, new BlockPos(-30, 64, 0), 0);
        c.buildings().add(hall);
        CitizenData d = new CitizenData(1);
        c.citizens().restore(d);
        Sleeper homeless = sleeper(d, new BlockPos(0, 64, 0));

        tickUntil(homeless, () -> homeless.ai().state() == SleepAI.State.FIND_BED);
        tick(homeless, 600);

        assertEquals(SleepAI.State.FIND_BED, homeless.ai().state()); // MC: never sleeps, stands until dawn
        assertFalse(d.asleep());
        BlockPos at = t.bodies.bodies.get(homeless.body()).position.toBlockPos();
        assertTrue(at.distSq(hall.position()) <= 16, "within 4 blocks of the town hall: " + at);
    }
}
