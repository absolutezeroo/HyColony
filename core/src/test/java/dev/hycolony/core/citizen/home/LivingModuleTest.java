package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivingModuleTest {
    private final TestContexts t = new TestContexts();

    private Colony colony(int citizens) {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        for (int id = 1; id <= citizens; id++) {
            c.citizens().restore(new CitizenData(id));
        }
        return c;
    }

    private static Building residence(Colony c, BlockPos at, int level) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, at, 0);
        b.setLevel(level);
        b.setBuilt(level > 0);
        c.buildings().add(b);
        return b;
    }

    private static LivingModule living(Building b) {
        return b.module(LivingModule.class).orElseThrow();
    }

    private static CitizenData citizen(Colony c, int id) {
        return c.citizens().get(id).orElseThrow();
    }

    @Test
    void autoHousingFillsTheHouseWithTheHomelessInOneTick() {
        Colony c = colony(3);
        Building b = residence(c, new BlockPos(8, 64, 0), 2);

        c.buildings().onColonyTick(c);

        assertEquals(List.of(1, 2), living(b).residents()); // MC: as many as fit, in citizen order
        assertEquals(b.position(), citizen(c, 1).homeBuilding());
        assertNull(citizen(c, 3).homeBuilding());
    }

    @Test
    void manualLockedOrColonyAutoHousingOffTakeNobody() {
        for (HiringMode mode : List.of(HiringMode.MANUAL, HiringMode.LOCKED, HiringMode.DEFAULT)) {
            Colony c = colony(1);
            c.settings().setAutoHousing(mode != HiringMode.DEFAULT);
            Building b = residence(c, new BlockPos(8, 64, 0), 1);
            living(b).setHiringMode(mode);

            c.buildings().onColonyTick(c);

            assertTrue(living(b).residents().isEmpty(), mode.name());
        }
    }

    @Test
    void levelZeroIsFullAndAutoModeIgnoresTheColonySetting() {
        Colony c = colony(1);
        c.settings().setAutoHousing(false);
        Building zero = residence(c, new BlockPos(8, 64, 0), 0);
        Building one = residence(c, new BlockPos(20, 64, 0), 1);
        living(one).setHiringMode(HiringMode.AUTO);

        assertFalse(living(zero).assign(c, zero, citizen(c, 1)));
        c.buildings().onColonyTick(c);

        assertEquals(List.of(1), living(one).residents());
    }

    @Test
    void assignRefusesDuplicatesAndFullHouses() {
        Colony c = colony(2);
        Building b = residence(c, new BlockPos(8, 64, 0), 1);

        assertTrue(living(b).assign(c, b, citizen(c, 1)));
        assertFalse(living(b).assign(c, b, citizen(c, 1)));
        assertFalse(living(b).assign(c, b, citizen(c, 2)));
    }

    @Test
    void movingHouseLeavesTheOldOneAndForgetsTheBed() {
        Colony c = colony(1);
        Building first = residence(c, new BlockPos(8, 64, 0), 1);
        Building second = residence(c, new BlockPos(20, 64, 0), 1);
        living(first).assign(c, first, citizen(c, 1));
        citizen(c, 1).setBedPos(new BlockPos(9, 64, 0));

        assertTrue(living(second).assign(c, second, citizen(c, 1)));

        assertTrue(living(first).residents().isEmpty());
        assertEquals(second.position(), citizen(c, 1).homeBuilding());
        assertNull(citizen(c, 1).bedPos());
    }

    @Test
    void housedCitizenIsNeverTakenByAnotherHouse() {
        Colony c = colony(1);
        Building first = residence(c, new BlockPos(8, 64, 0), 1);
        living(first).assign(c, first, citizen(c, 1));
        Building second = residence(c, new BlockPos(20, 64, 0), 1);

        c.buildings().onColonyTick(c);

        assertTrue(living(second).residents().isEmpty());
    }

    @Test
    void removeAndRemovalMakeHomelessWithoutWakingUp() {
        Colony c = colony(2);
        Building b = residence(c, new BlockPos(8, 64, 0), 2);
        c.buildings().onColonyTick(c);
        citizen(c, 1).setAsleep(true);

        assertTrue(living(b).remove(c, b, 1));
        assertNull(citizen(c, 1).homeBuilding());
        assertTrue(citizen(c, 1).asleep()); // MC LivingBuildingModule.onRemoval only clears the home and bed

        living(b).onRemoved(c, b);
        assertNull(citizen(c, 2).homeBuilding());
        assertTrue(living(b).residents().isEmpty());
    }

    @Test
    void residentsAndModeRoundTrip() {
        Colony c = colony(1);
        Building b = residence(c, new BlockPos(8, 64, 0), 1);
        living(b).assign(c, b, citizen(c, 1));
        living(b).setHiringMode(HiringMode.MANUAL);
        JsonObject saved = new JsonObject();
        living(b).write(saved);

        LivingModule read = new LivingModule();
        read.read(saved);

        assertEquals(HiringMode.MANUAL, read.hiringMode());
        assertEquals(List.of(1), read.takeSavedResidents());
        assertTrue(read.residents().isEmpty());
    }

    @Test
    void asleepClearsLeisure() {
        CitizenData d = new CitizenData(1);
        d.setLeisureTime(100);

        d.setAsleep(true);

        assertEquals(0, d.leisureTime());
    }
}
