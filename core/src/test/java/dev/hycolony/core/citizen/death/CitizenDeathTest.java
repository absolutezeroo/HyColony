package dev.hycolony.core.citizen.death;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessIds;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.stats.ColonyStatistics;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC EntityCitizen.die: what a citizen's death does to it and to its colony. */
class CitizenDeathTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final Vec3 WHERE = new Vec3(20.5, 64, -9.5);
    private static final DeathCause FALL = new DeathCause("Fall", Optional.empty());

    private final TestContexts t = new TestContexts();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(OWNER, "A")));
    private final Building house = hut(ConstructionBuildingTypes.RESIDENCE, new BlockPos(10, 64, 0), 2);
    private final CitizenData bob = citizen(1, "Bob");
    private final CitizenData ann = citizen(2, "Ann");
    private final CitizenData eve = citizen(3, "Eve");

    CitizenDeathTest() {
        hut(BuildingTypes.TOWN_HALL, new BlockPos(0, 64, 0));
        c.setDay(4);
        LivingModule living = house.module(LivingModule.class).orElseThrow();
        assertTrue(living.assign(c, house, bob));
        assertTrue(living.assign(c, house, ann));
        BodyId body = t.bodies.existing(1, 1, WHERE);
        c.citizens().onBodyLoaded(body, 1);
    }

    private Building hut(BuildingType type, BlockPos at) {
        return hut(type, at, 1);
    }

    private Building hut(BuildingType type, BlockPos at, int level) {
        Building b = Building.create(type, at, 0);
        b.setLevel(level);
        b.setBuilt(true);
        c.buildings().add(b);
        return b;
    }

    private CitizenData citizen(int id, String name) {
        CitizenData d = new CitizenData(id);
        d.setName(name);
        c.citizens().restore(d);
        return d;
    }

    @Test
    void aDeadCitizenIsRemovedForGood() {
        assertTrue(CitizenDeath.die(c, 1, WHERE, FALL));

        assertTrue(c.citizens().get(1).isEmpty());
        assertTrue(c.citizens().ai(1).isEmpty());
        assertTrue(c.citizens().bodyOf(1).isEmpty());
        assertTrue(c.isDirty());
    }

    @Test
    void itsInventoryAndArmourFallWhereItDied() {
        ItemAmount apples = new ItemAmount(new ItemKey("Food_Apple"), 4);
        ItemAmount helmet = new ItemAmount(new ItemKey("Armor_Iron_Head"), 1, 7);
        bob.inventory().set(3, Optional.of(apples));
        bob.equipment().armor().set(0, Optional.of(helmet));

        CitizenDeath.die(c, 1, WHERE, FALL);

        assertEquals(List.of(apples, helmet), t.blocks.dropped.get(WHERE.toBlockPos()), "MC: no grave yet");
    }

    @Test
    void itsJobAndHomeAreFreed() {
        Building work = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(30, 64, 0));
        WorkerModule workers = work.module(WorkerModule.class).orElseThrow();
        assertTrue(workers.hire(c, work, bob));

        CitizenDeath.die(c, 1, WHERE, FALL);

        assertFalse(workers.workers().contains(1));
        assertEquals(List.of(2), house.module(LivingModule.class).orElseThrow().residents());
    }

    @Test
    void theColonyIsToldCountsAndLogsIt() {
        CitizenDeath.die(c, 1, WHERE, FALL);

        Msg told = t.notifier.sent.getFirst().msg();
        assertEquals(OWNER, t.notifier.sent.getFirst().player());
        assertEquals(
                Msg.of(
                        "hycolony.citizen.died",
                        "Bob",
                        "%hycolony.citizen.deathCause.Fall",
                        "%hycolony.ui.direction.long.ne"),
                told,
                "MC: the cause, then where from the colony's centre (x 20, z -10: north-east)");
        assertEquals(1, c.registries().statistics().inPeriod(ColonyStatistics.DEATH, 4, 4));
        EventLog.Entry logged = c.log().entries().getLast();
        assertEquals("citizenDied", logged.type());
        assertEquals(List.of("Bob", "Fall", ""), logged.params());
        assertEquals(Optional.of(WHERE.toBlockPos()), logged.pos());
    }

    @Test
    void aKillerIsNamed() {
        CitizenDeath.die(c, 1, WHERE, new DeathCause("Physical", Optional.of("%server.npcRoles.Zombie.name")));

        assertEquals(
                Msg.of(
                        "hycolony.citizen.diedKilledBy",
                        "Bob",
                        "%server.npcRoles.Zombie.name",
                        "%hycolony.ui.direction.long.ne"),
                t.notifier.sent.getFirst().msg());
        assertEquals(
                List.of("Bob", "Physical", "%server.npcRoles.Zombie.name"),
                c.log().entries().getLast().params());
    }

    @Test
    void everyOtherCitizenIsSaddenedAndItsHousematesMournTomorrow() {
        CitizenDeath.die(c, 1, WHERE, FALL);

        assertTrue(ann.happiness().get(HappinessIds.DEATH).isPresent(), "MC injectModifier(DEATH, 3.0, 3 days)");
        assertTrue(eve.happiness().get(HappinessIds.DEATH).isPresent());
        assertEquals(List.of("Bob"), new ArrayList<>(ann.mourning().deceased()), "MC doesLiveWith");
        assertFalse(ann.mourning().isMourning(), "from the next morning");
        assertTrue(eve.mourning().deceased().isEmpty());
    }

    @Test
    void theDeathIsPostedAndASecondOneDoesNothing() {
        List<CitizenDied> posted = new ArrayList<>();
        t.bus.subscribe(CitizenDied.class, posted::add);

        CitizenDeath.die(c, 1, WHERE, FALL);
        boolean again = CitizenDeath.die(c, 1, WHERE, FALL);

        assertFalse(again);
        assertEquals(1, posted.size());
        assertEquals("Bob", posted.getFirst().citizen().name());
        assertEquals(1, t.notifier.sent.size());
    }
}
