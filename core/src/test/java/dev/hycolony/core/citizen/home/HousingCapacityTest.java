package dev.hycolony.core.citizen.home;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HousingCapacityTest {
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

    private static Building residence(Colony c, int x, int level) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(x, 64, 0), 0);
        b.setLevel(level);
        c.buildings().add(b);
        return b;
    }

    private void capAt25() {
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                new ColonyConfig.Gameplay(4, 25, false),
                d.claims(),
                d.permissions(),
                d.commands(),
                d.client(),
                d.hycolony(),
                d.structurize());
    }

    @Test
    void sumsBuiltResidencesAndLockedOnesCountTheirResidents() {
        Colony c = colony(2);
        residence(c, 8, 3);
        residence(c, 20, 0);
        Building locked = residence(c, 40, 4);
        LivingModule living = locked.module(LivingModule.class).orElseThrow();
        living.assign(c, locked, c.citizens().get(1).orElseThrow());
        living.setHiringMode(HiringMode.LOCKED);

        assertEquals(4, HousingCapacity.of(c).housing());
    }

    @Test
    void noHousingStillAllowsOneAndConfigCaps() {
        assertEquals(1, HousingCapacity.of(colony(0)).housing());
        capAt25();
        Colony c = colony(0);
        for (int i = 0; i < 6; i++) {
            residence(c, 8 + 12 * i, 5);
        }
        assertEquals(25, HousingCapacity.of(c).housing());
    }

    @Test
    void populationFollowsMcStatisticsColours() {
        capAt25();
        Colony ok = colony(1);
        residence(ok, 8, 5);
        assertEquals(HousingCapacity.Population.OK, HousingCapacity.of(ok).population());

        Colony crowded = colony(5);
        residence(crowded, 8, 5);
        HousingCapacity full = HousingCapacity.of(crowded);
        assertEquals(HousingCapacity.Population.NEEDS_HOUSING, full.population());
        assertEquals(5, full.shownMax());

        Colony capped = colony(25);
        assertEquals(
                HousingCapacity.Population.CONFIG_LIMITED,
                HousingCapacity.of(capped).population());
        assertEquals(25, HousingCapacity.of(capped).shownMax());
    }
}
