package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The item a request shows first, which creative mode hands over (MC IRequest.getDisplayStacks). */
class DeliverableDisplayedTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");

    private final FakeCatalog catalog = new FakeCatalog();

    @Test
    void aStackRequestShowsItsItem() {
        assertEquals(Optional.of(PLANKS), new StackRequest(PLANKS, 16, 16, true).displayed(catalog));
    }

    @Test
    void aStackListShowsItsFirstAcceptedItem() {
        assertEquals(Optional.of(STONE), new StackList(List.of(STONE, PLANKS), "any", 4, 4).displayed(catalog));
    }

    @Test
    void aToolRequestShowsTheLowestTierCatalogToolItAccepts() {
        catalog.tools.put(new ItemKey("Tool_Pickaxe_Iron"), new ToolInfo(ToolType.PICKAXE, 2, 1));
        catalog.tools.put(new ItemKey("Tool_Pickaxe_Crude"), new ToolInfo(ToolType.PICKAXE, 0, 1));
        catalog.tools.put(new ItemKey("Tool_Hatchet_Crude"), new ToolInfo(ToolType.AXE, 0, 1));
        catalog.tools.put(new ItemKey("Tool_Pickaxe_Adamantite"), new ToolInfo(ToolType.PICKAXE, 3, 1));

        assertEquals(
                Optional.of(new ItemKey("Tool_Pickaxe_Iron")),
                new ToolRequest(ToolType.PICKAXE, 1, 3).displayed(catalog),
                "the crude one is below the levels asked; Adamantite, first by id, is a higher tier");
        assertEquals(
                Optional.of(new ItemKey("Tool_Pickaxe_Crude")),
                new ToolRequest(ToolType.PICKAXE, 0, 3).displayed(catalog),
                "the lowest tier first");
    }

    @Test
    void aToolRequestNoCatalogToolAcceptsShowsNothing() {
        assertEquals(Optional.empty(), new ToolRequest(ToolType.SHOVEL, 0, 3).displayed(catalog));
    }
}
