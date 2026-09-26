package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenNameplatesTest {
    private static final ItemKey PLANKS_I = new ItemKey("Wood_Planks");
    private static final StackRequest PLANKS = new StackRequest(PLANKS_I, 4, 4, true);
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData jean = new CitizenData(1);
    private final BodyId body;

    CitizenNameplatesTest() {
        manager.beginFoundation(alice, "Alice", hall, 0);
        colony = manager.confirmFoundation(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        jean.setName("Jean");
        colony.citizens().restore(jean);
        body = t.bodies.existing(colony.id(), 1, Vec3.center(hall));
        colony.citizens().onBodyLoaded(body, 1);
    }

    private String shown() {
        return t.bodies.bodies.get(body).name;
    }

    @Test
    void markerAppearsWhileOnlyAPlayerCanProvideThenClears() {
        RequestToken token = colony.requests().createAndAssign(hut, PLANKS, jean.id());
        colony.nameplates().refresh();
        assertEquals("Jean", shown(), "held by the retrying resolver: no marker yet");
        assertEquals(List.of("Jean"), t.bodies.renames, "a body seen for the first time gets its name once");

        colony.requests().reassign(token, Set.of(RetryingResolver.ID)); // now the player's
        colony.nameplates().refresh();
        colony.nameplates().refresh();
        assertEquals("! Jean", shown());
        assertEquals("! Jean", colony.nameplates().nameFor(jean), "a body spawned now gets the marker");
        assertEquals(List.of("Jean", "! Jean"), t.bodies.renames, "renamed on change only");

        colony.requests().overrule(token, List.of(new ItemAmount(PLANKS_I, 4)), true); // supplied
        colony.nameplates().refresh();
        assertEquals("Jean", shown());
        assertEquals(List.of("Jean", "! Jean", "Jean"), t.bodies.renames);
    }

    @Test
    void cancelledRequestClearsTheMarker() {
        RequestToken token = colony.requests().createAndAssign(hut, PLANKS, jean.id());
        colony.requests().reassign(token, Set.of(RetryingResolver.ID));
        colony.nameplates().refresh();
        assertEquals("! Jean", shown());

        colony.requests().cancelAllFrom(hut.requesterId());
        colony.nameplates().refresh();
        assertEquals("Jean", shown());
    }

    @Test
    void theColonyRefreshesNameplatesWhileActive() {
        t.players.online.put(alice, hall);
        RequestToken token = colony.requests().createAndAssign(hut, PLANKS, jean.id());
        colony.requests().reassign(token, Set.of(RetryingResolver.ID));
        for (int i = 0; i < 200; i++) {
            colony.tick();
        }
        assertEquals("! Jean", shown());
    }
}
