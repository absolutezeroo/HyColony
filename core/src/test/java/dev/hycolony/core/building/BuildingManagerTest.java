package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildingManagerTest {
    private final TestContexts t = new TestContexts();

    static final class Counter implements TickingModule {
        int ticks;

        @Override
        public void onColonyTick(Building building) {
            ticks++;
        }
    }

    private Colony colony() {
        return new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    }

    @Test
    void registryFindsTownHallByIdAndHutKey() {
        BuildingRegistry r = BuildingTypes.defaults();
        assertEquals(BuildingTypes.TOWN_HALL, r.byId("hycolony:townhall").orElseThrow());
        assertEquals(BuildingTypes.TOWN_HALL, r.byHutKey("hut.townhall").orElseThrow());
    }

    @Test
    void createInstantiatesModulesInOrderAndTicksThem() {
        BuildingType type =
                new BuildingType("test:b", "hut.b", 5, List.of(new ModuleProducer("counter", Counter::new)));
        Building b = Building.create(type, new BlockPos(1, 2, 3), 1);
        BuildingManager m = new BuildingManager();
        m.add(b);
        Colony c = colony();
        m.onColonyTick(c);
        m.onColonyTick(c);
        assertEquals(2, b.module(Counter.class).orElseThrow().ticks);
        assertEquals(0, b.level());
    }

    @Test
    void townHallLookupAndRemoval() {
        BuildingManager m = new BuildingManager();
        BlockPos pos = new BlockPos(0, 64, 0);
        m.add(Building.create(BuildingTypes.TOWN_HALL, pos, 0));
        assertTrue(m.townHall().isPresent());
        assertTrue(m.remove(pos).isPresent());
        assertTrue(m.townHall().isEmpty());
    }

    @Test
    void unknownBuildingsAreKeptVerbatim() {
        BuildingManager m = new BuildingManager();
        JsonObject raw = new JsonObject();
        raw.addProperty("type", "removed:thing");
        m.keepUnknown(raw);
        assertEquals(List.of(raw), m.unknown());
    }
}
