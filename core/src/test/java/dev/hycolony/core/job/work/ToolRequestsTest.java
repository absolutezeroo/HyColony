package dev.hycolony.core.job.work;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC AbstractEntityAIBasic.checkForToolOrWeapon: one tool request per type, up to the hut's equipment level. */
class ToolRequestsTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);

    private final TestContexts t = new TestContexts();
    private final CitizenData citizen = new CitizenData(1);
    private final Colony colony;
    private final Building hut;
    private final ToolRequests tools;

    ToolRequestsTest() {
        UUID alice = UUID.randomUUID();
        ColonyManager manager = new ColonyManager(t.context());
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, DeliverymanHut.TYPE_ID, HUT, 0);
        hut = colony.buildings().at(HUT).orElseThrow();
        colony.citizens().restore(citizen);
        tools = new ToolRequests(colony, citizen, hut);
    }

    private List<Request> toolRequests() {
        return colony.requests().byRequester(hut.requesterId()).stream()
                .filter(r -> r.requestable() instanceof ToolRequest)
                .toList();
    }

    @Test
    void aMissingToolIsRequestedOnceUpToTheHutsEquipmentLevel() {
        tools.requestTool(ToolType.PICKAXE);
        tools.requestTool(ToolType.PICKAXE);

        assertEquals(1, toolRequests().size());
        Request r = toolRequests().get(0);
        assertEquals(new ToolRequest(ToolType.PICKAXE, 0, hut.maxEquipmentLevel()), r.requestable());
        assertEquals(citizen.id(), r.citizenId());
    }

    @Test
    void anotherToolTypeIsRequestedToo() {
        tools.requestTool(ToolType.PICKAXE);
        tools.requestTool(ToolType.AXE);

        assertEquals(2, toolRequests().size());
    }
}
