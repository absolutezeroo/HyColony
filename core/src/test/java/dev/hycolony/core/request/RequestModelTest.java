package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestModelTest {

    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");

    @Test
    void stackMatchesOnlySameItem() {
        FakeCatalog catalog = new FakeCatalog();
        StackRequest r = new StackRequest(OAK, 64, 1, true);
        assertTrue(r.matches(OAK, catalog));
        assertFalse(r.matches(STONE, catalog));
        assertEquals("64 x Wood_Oak_Trunk", r.describe());
    }

    @Test
    void toolRequestMatchesTypeAndLevelRange() {
        FakeCatalog catalog = new FakeCatalog();
        ItemKey woodPick = new ItemKey("Tool_Pickaxe_Wood");
        ItemKey ironPick = new ItemKey("Tool_Pickaxe_Iron");
        ItemKey ironAxe = new ItemKey("Tool_Axe_Iron");
        catalog.tools.put(woodPick, new ToolInfo(ToolType.PICKAXE, 0, 2f));
        catalog.tools.put(ironPick, new ToolInfo(ToolType.PICKAXE, 2, 6f));
        catalog.tools.put(ironAxe, new ToolInfo(ToolType.AXE, 2, 6f));

        ToolRequest r = new ToolRequest(ToolType.PICKAXE, 1, 3);
        assertTrue(r.matches(ironPick, catalog));
        assertFalse(r.matches(woodPick, catalog), "level below min");
        assertFalse(r.matches(ironAxe, catalog), "wrong type");
        assertFalse(r.matches(STONE, catalog), "not a tool");
        assertFalse(new ToolRequest(ToolType.PICKAXE, 0, 1).matches(ironPick, catalog), "level above max");
        assertEquals(1, r.count());
        assertEquals(1, r.minCount());
    }

    @Test
    void withCountKeepsOtherFields() {
        StackRequest r = new StackRequest(OAK, 64, 16, false);
        assertEquals(new StackRequest(OAK, 10, 16, false), r.withCount(10));
        ToolRequest t = new ToolRequest(ToolType.AXE, 1, 2);
        assertEquals(t, t.withCount(5));
    }

    @Test
    void stateOrdinalsMatchMineColonies() {
        List<String> expected = List.of(
                "CREATED",
                "REPORTED",
                "ASSIGNING",
                "ASSIGNED",
                "IN_PROGRESS",
                "RESOLVED",
                "FOLLOWUP_IN_PROGRESS",
                "COMPLETED",
                "OVERRULED",
                "CANCELLED",
                "RECEIVED",
                "FINALIZING",
                "FAILED");
        assertEquals(
                expected, Arrays.stream(RequestState.values()).map(Enum::name).toList());
    }

    @Test
    void requestTokenRandomUnique() {
        Set<RequestToken> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(RequestToken.random());
        }
        assertEquals(1000, tokens.size());
        assertNotEquals(RequestToken.random(), RequestToken.random());
    }
}
