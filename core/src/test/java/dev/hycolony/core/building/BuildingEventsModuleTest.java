package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildingEventsModuleTest {
    private final TestContexts t = new TestContexts();

    static final class Recorder implements BuildingEventsModule {
        final List<String> events = new ArrayList<>();

        @Override
        public void onRemoved(Colony colony, Building building) {
            events.add("removed " + building.position());
            colony.requests().byRequester(building.requesterId()).stream()
                    .filter(r -> r.state() != RequestState.CANCELLED)
                    .forEach(r -> events.add("saw open request"));
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
    void eventModulesHearOfRemovalBeforeTheHutsRequestsAreCancelled() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        colony.requests().createAndAssign(b, new StackRequest(new ItemKey("Wood_Planks"), 4, 4, true), 1);
        colony.buildings().remove(b.position());
        assertEquals(List.of("removed " + b.position(), "saw open request"), recorder.events);
    }

    @Test
    void aSameLevelRepairDoesNotTellEventModules() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        b.setLevel(2);
        UpgradeCompletion.reach(colony, b, 2, Optional.empty());
        assertEquals(List.of(), recorder.events);
    }

    @Test
    void rebuildingADeconstructedHutAtItsLevelTellsEventModules() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        b.setLevel(2);
        b.setDeconstructed(true);
        UpgradeCompletion.reach(colony, b, 2, Optional.empty());
        assertEquals(List.of("upgraded to 2"), recorder.events);
    }

    @Test
    void reachingALevelTellsItsEventModules() {
        Recorder recorder = new Recorder();
        Building b = addHut(recorder);
        UpgradeCompletion.reach(colony, b, 2, Optional.empty());
        assertEquals(List.of("upgraded to 2"), recorder.events);
    }
}
