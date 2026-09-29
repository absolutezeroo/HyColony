package dev.hycolony.core.logistics.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HutKeepTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final ItemKey WOOD_PICK = new ItemKey("Tool_Pickaxe_Wood");
    private static final ItemKey IRON_PICK = new ItemKey("Tool_Pickaxe_Iron");
    private static final ItemKey WOOD_AXE = new ItemKey("Tool_Hatchet_Wood");

    private final TestContexts t = new TestContexts();
    private final Colony colony;
    private final Building builderHut;

    HutKeepTest() {
        ColonyManager manager = t.manager();
        UUID alice = UUID.randomUUID();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        builderHut = Building.create(ConstructionBuildingTypes.BUILDER, new BlockPos(10, 64, 0), 0);
        builderHut.setLevel(1);
        colony.buildings().add(builderHut);
        t.catalog.tools.put(WOOD_PICK, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        t.catalog.tools.put(IRON_PICK, new ToolInfo(ToolType.PICKAXE, 2, 1f));
        t.catalog.tools.put(WOOD_AXE, new ToolInfo(ToolType.AXE, 0, 1f));
    }

    @Test
    void buildingKeepsItsToolsAndOpenRequestItems() {
        RequestToken parent = colony.requests().createAndAssign(builderHut, new StackRequest(STONE, 1, 1, true), -1);
        RequestToken child = colony.requests()
                .createChild(builderHut.resolvers().get(0), parent, new StackRequest(PLANKS, 5, 5, true));
        colony.requests().addDelivery(child, new ItemAmount(PLANKS, 5));

        HutKeep keep = HutKeep.of(colony, builderHut, false);

        assertEquals(0, keep.removable(new ItemAmount(WOOD_PICK, 1)), "first pickaxe kept");
        assertEquals(1, keep.removable(new ItemAmount(WOOD_PICK, 1)), "second pickaxe goes");
        assertEquals(0, keep.removable(new ItemAmount(WOOD_AXE, 1)), "one tool per type");
        assertEquals(3, keep.removable(new ItemAmount(PLANKS, 8)), "the 5 delivered planks stay");
        assertEquals(4, keep.removable(new ItemAmount(PLANKS, 4)), "already kept 5");
        assertEquals(7, keep.removable(new ItemAmount(STONE, 7)), "nothing asks for stone");
    }

    @Test
    void toolAboveTheHutLevelIsNotKept() {
        HutKeep keep = HutKeep.of(colony, builderHut, false);

        assertEquals(1, keep.removable(new ItemAmount(IRON_PICK, 1)));
    }

    @Test
    void level0HutKeepsAStoneTool() {
        ItemKey stonePick = new ItemKey("Tool_Pickaxe_Stone");
        t.catalog.tools.put(stonePick, new ToolInfo(ToolType.PICKAXE, 1, 1f));
        builderHut.setLevel(0);

        HutKeep keep = HutKeep.of(colony, builderHut, false);

        assertEquals(0, keep.removable(new ItemAmount(stonePick, 1)), "MC BASIC_TOOL_LEVEL at hut level 0");
    }

    @Test
    void workerDumpIgnoresRequestDeliveries() {
        RequestToken parent = colony.requests().createAndAssign(builderHut, new StackRequest(STONE, 1, 1, true), -1);
        RequestToken child = colony.requests()
                .createChild(builderHut.resolvers().get(0), parent, new StackRequest(PLANKS, 5, 5, true));
        colony.requests().addDelivery(child, new ItemAmount(PLANKS, 5));

        HutKeep keep = HutKeep.of(colony, builderHut, true);

        assertEquals(8, keep.removable(new ItemAmount(PLANKS, 8)), "MC: deliveries are kept in the hut only");
        assertEquals(0, keep.removable(new ItemAmount(WOOD_PICK, 1)));
    }
}
