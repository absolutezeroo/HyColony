package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildingEventsModuleTest {
    private final TestContexts t = new TestContexts();

    static final class Recorder implements BuildingEventsModule {
        final List<String> events = new ArrayList<>();

        @Override
        public void onRemoved(Colony colony, Building building) {
            events.add("removed " + building.position());
        }

        @Override
        public void onUpgradeComplete(Colony colony, Building building, int newLevel) {
            events.add("upgraded to " + newLevel);
        }
    }

    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));

    private Building addHut(Recorder recorder) {
        BuildingType type =
                new BuildingType("test:events", "hut.events", 5, List.of(new ModuleProducer("rec", () -> recorder)));
        Building b = Building.create(type, new BlockPos(3, 64, 3), 0);
        colony.buildings().add(b);
        return b;
    }

    @Test
    void removingAHutTellsItsEventModules() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        colony.buildings().remove(b.position());
        assertEquals(List.of("removed " + b.position()), recorder.events);
    }

    @Test
    void reachingALevelTellsItsEventModules() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        UpgradeCompletion.reach(colony, b, 2);
        assertEquals(List.of("upgraded to 2"), recorder.events);
    }
}
