package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.home.BedModule;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The housing a load rebuilds: MC reassigns saved residents in order, and a residence finds its planned beds. */
class HousingHealTest {
    private static final BlockKey BED = new BlockKey("bed");
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

    /** A residence read from a save listing {@code residents}, as the serializer leaves it before healing. */
    private static Building savedResidence(Colony c, int x, int level, int... residents) {
        Building b = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(x, 64, 0), 0);
        b.setLevel(level);
        b.setBuilt(level > 0);
        JsonObject saved = new JsonObject();
        JsonArray ids = new JsonArray();
        for (int id : residents) {
            ids.add(id);
        }
        saved.add("residents", ids);
        b.module(LivingModule.class).orElseThrow().read(saved);
        c.buildings().add(b);
        for (int id : residents) {
            c.citizens().get(id).ifPresent(d -> d.setHomeBuilding(b.position()));
        }
        return b;
    }

    private static List<Integer> residents(Building b) {
        return b.module(LivingModule.class).orElseThrow().residents();
    }

    @Test
    void citizenListedInTwoHomesEndsInOne() {
        Colony c = colony(1);
        Building first = savedResidence(c, 8, 1, 1);
        Building second = savedResidence(c, 20, 1, 1);

        assertTrue(HousingHeal.heal(c));

        assertTrue(residents(first).isEmpty());
        assertEquals(List.of(1), residents(second));
        assertEquals(second.position(), c.citizens().get(1).orElseThrow().homeBuilding());
    }

    @Test
    void unknownResidentIsDropped() {
        Colony c = colony(0);
        Building b = savedResidence(c, 8, 1, 99);

        HousingHeal.heal(c);

        assertTrue(residents(b).isEmpty());
    }

    @Test
    void strayHomeIsCleared() {
        Colony c = colony(1);
        Building b = savedResidence(c, 8, 1);
        CitizenData d = c.citizens().get(1).orElseThrow();
        d.setHomeBuilding(b.position());
        d.setBedPos(new BlockPos(9, 64, 0));

        assertTrue(HousingHeal.heal(c));

        assertNull(d.homeBuilding());
        assertNull(d.bedPos());
    }

    @Test
    void bedsOfAResidenceBuiltBeforeSp4AreFoundInItsPlan() {
        t.catalog.beds.add(BED);
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                return Optional.of(new Blueprint(
                        "k",
                        List.of(new BlueprintEntry(new BlockPos(2, 0, 1), new BlockState(BED, 0), false)),
                        new BlockPos(0, 0, 0),
                        new BlockPos(2, 0, 1)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        Colony c = colony(0);
        Building b = savedResidence(c, 8, 1);
        b.setStyle("s");

        assertTrue(HousingHeal.heal(c));
        assertFalse(HousingHeal.heal(c), "found once");

        assertEquals(
                List.of(new BlockPos(10, 64, 1)),
                b.module(BedModule.class).orElseThrow().beds());
    }

    @Test
    void healReportsNoChangeOnAHealthySave() {
        Colony c = colony(1);
        Building b = savedResidence(c, 8, 1, 1);

        assertFalse(HousingHeal.heal(c));
        assertEquals(List.of(1), residents(b));
    }
}
