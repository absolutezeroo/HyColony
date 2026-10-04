package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import dev.hycolony.api.Actor;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Subscription;
import dev.hycolony.api.event.BuildingLevelChanged;
import dev.hycolony.api.event.BuildingPlaced;
import dev.hycolony.api.event.BuildingRemoved;
import dev.hycolony.api.event.CitizenDied;
import dev.hycolony.api.event.CitizenSpawned;
import dev.hycolony.api.event.ColonyCreated;
import dev.hycolony.api.event.ColonyDeleted;
import dev.hycolony.api.event.DayStarted;
import dev.hycolony.api.event.NightFell;
import dev.hycolony.api.event.WorkOrderCreated;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.death.DeathCause;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.Stage;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's stable events: the core's events in references, with their cause (spec 2026-09-30, § 4.2). */
class CoreColonyWorldEventsTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final BlockPos HUT = new BlockPos(8, 64, 0);

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private boolean onWorldThread = true;
    private final CoreColonyWorld world = new CoreColonyWorld(manager, () -> onWorldThread);
    private final List<Object> heard = new ArrayList<>();

    private Colony found() {
        manager.foundation().begin(alice, "Alice", HALL, 0);
        return manager.foundation().confirm(alice, "Rivendell").orElseThrow();
    }

    private ColonyRef ref(Colony c) {
        return new ColonyRef("world", c.id());
    }

    @Test
    void foundingIsCausedByTheFounder() {
        world.subscribe(ColonyCreated.class, heard::add);
        world.subscribe(BuildingPlaced.class, heard::add);

        Colony c = found();

        String hall = c.buildings().townHall().orElseThrow().type().id();
        assertEquals(
                List.of(
                        new ColonyCreated(ref(c), new Actor.Player(alice)),
                        new BuildingPlaced(ref(c), hall, new Pos(0, 64, 0), new Actor.Player(alice))),
                heard);
    }

    @Test
    void placedHutIsCausedByItsPlayer() {
        Colony c = found();
        world.subscribe(BuildingPlaced.class, heard::add);

        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, bob);

        assertEquals(
                List.of(new BuildingPlaced(
                        ref(c), ConstructionBuildingTypes.BUILDER.id(), new Pos(8, 64, 0), new Actor.Player(bob))),
                heard);
    }

    @Test
    void staleBuildingFoundGoneIsRemovedByTheColony() {
        Colony c = found();
        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, bob);
        world.subscribe(BuildingRemoved.class, heard::add);

        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, bob);

        assertEquals(
                List.of(new BuildingRemoved(
                        ref(c), ConstructionBuildingTypes.BUILDER.id(), new Pos(8, 64, 0), new Actor.Colony())),
                heard);
    }

    @Test
    void brokenHutIsRemovedByItsBreaker() {
        Colony c = found();
        manager.huts().place(c, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, bob);
        world.subscribe(BuildingRemoved.class, heard::add);

        manager.huts().breakBy(alice, HUT);

        assertEquals(
                List.of(new BuildingRemoved(
                        ref(c), ConstructionBuildingTypes.BUILDER.id(), new Pos(8, 64, 0), new Actor.Player(alice))),
                heard);
    }

    @Test
    void deletedColonyIsCausedByWhoDeletedIt() {
        Colony c = found();
        world.subscribe(ColonyDeleted.class, heard::add);

        manager.deleteColony(c.id(), alice);

        assertEquals(List.of(new ColonyDeleted(ref(c), new Actor.Player(alice))), heard);
    }

    @Test
    void coreEventsAreTranslatedIntoReferencesWithTheirCause() {
        Colony c = found();
        Building hall = c.buildings().townHall().orElseThrow();
        CitizenData citizen = new CitizenData(7);
        JsonObject saved = new JsonObject();
        saved.addProperty("id", 3);
        saved.addProperty("type", WorkOrderType.UPGRADE.name());
        saved.add("pos", SavedJson.pos(HALL));
        saved.addProperty("targetLevel", 2);
        saved.addProperty("stage", Stage.CLEAR.name());
        WorkOrder order = WorkOrder.read(saved).orElseThrow();
        world.subscribe(BuildingLevelChanged.class, heard::add);
        world.subscribe(WorkOrderCreated.class, heard::add);
        world.subscribe(CitizenSpawned.class, heard::add);
        world.subscribe(CitizenDied.class, heard::add);
        world.subscribe(DayStarted.class, heard::add);
        world.subscribe(NightFell.class, heard::add);

        t.bus.post(new ColonyEvents.BuildingLevelChanged(c, hall, 1, 2, Optional.empty())); // its builder
        t.bus.post(new ColonyEvents.BuildingLevelChanged(c, hall, 0, 3, Optional.of(alice))); // a creative paste
        t.bus.post(new ColonyEvents.WorkOrderCreated(c, order, Optional.of(bob)));
        t.bus.post(new dev.hycolony.core.citizen.CitizenSpawned(c, citizen)); // the core's, not the api's
        t.bus.post(
                new dev.hycolony.core.citizen.death.CitizenDied(c, citizen, new DeathCause("Fall", Optional.empty())));
        c.setDay(5);
        t.bus.post(new ColonyEvents.DayStarted(c));
        t.bus.post(new ColonyEvents.NightFell(c));

        Pos hallPos = new Pos(0, 64, 0);
        assertEquals(
                List.of(
                        new BuildingLevelChanged(ref(c), hall.type().id(), hallPos, 1, 2, new Actor.Colony()),
                        new BuildingLevelChanged(ref(c), hall.type().id(), hallPos, 0, 3, new Actor.Player(alice)),
                        new WorkOrderCreated(ref(c), 3, "UPGRADE", hallPos, new Actor.Player(bob)),
                        new CitizenSpawned(new CitizenRef(ref(c), 7)),
                        new CitizenDied(new CitizenRef(ref(c), 7)),
                        new DayStarted(ref(c), 5),
                        new NightFell(ref(c))),
                heard);
    }

    @Test
    void closedSubscriptionHearsNothingMore() {
        Subscription s = world.subscribe(ColonyCreated.class, heard::add);

        s.close();
        found();

        assertEquals(List.of(), heard);
    }

    @Test
    void onlyApiEventsCanBeHeard() {
        assertThrows(IllegalArgumentException.class, () -> world.subscribe(String.class, heard::add));
        assertThrows(
                IllegalArgumentException.class, () -> world.subscribe(ColonyEvents.ColonyCreated.class, heard::add));
    }

    @Test
    void subscribingRefusesAnotherThread() {
        onWorldThread = false;

        assertThrows(IllegalStateException.class, () -> world.subscribe(ColonyCreated.class, heard::add));
    }
}
