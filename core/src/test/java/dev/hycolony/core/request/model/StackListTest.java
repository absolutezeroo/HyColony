package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class StackListTest {
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey BIRCH = new ItemKey("Wood_Birch_Trunk");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");

    @Test
    void matchesAnyAcceptedItem() {
        FakeCatalog catalog = new FakeCatalog();
        StackList trunks = new StackList(List.of(OAK, BIRCH), "type:Wood_Trunk", 8, 4);

        assertTrue(trunks.matches(OAK, catalog));
        assertTrue(trunks.matches(BIRCH, catalog));
        assertFalse(trunks.matches(STONE, catalog));
        assertTrue(trunks.canBeResolvedByBuilding());
        assertEquals("8 x type:Wood_Trunk", trunks.describe());
    }

    @Test
    void withCountKeepsTheAcceptedItems() {
        StackList trunks = new StackList(List.of(OAK, BIRCH), "type:Wood_Trunk", 8, 4);

        StackList fewer = trunks.withCount(3);

        assertEquals(List.of(OAK, BIRCH), fewer.accepted());
        assertEquals("type:Wood_Trunk", fewer.description());
        assertEquals(3, fewer.count());
        assertEquals(4, fewer.minCount());
    }

    @Test
    void equalityComparesOnlyTheAcceptedItemsLikeMc() {
        StackList trunks = new StackList(List.of(OAK, BIRCH), "type:Wood_Trunk", 8, 4);
        StackList sameItems = new StackList(List.of(BIRCH, OAK), "other", 1, 1);

        assertEquals(trunks, sameItems, "MC StackList.equals ignores order, counts and description");
        assertEquals(trunks.hashCode(), sameItems.hashCode());
        assertNotEquals(trunks, new StackList(List.of(OAK), "type:Wood_Trunk", 8, 4));
    }
}
