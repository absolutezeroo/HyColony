package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryTest {
    private static final ItemKey STONE = new ItemKey("hycolony:stone");
    private static final ItemKey DIRT = new ItemKey("hycolony:dirt");
    private static final ItemKey SAND = new ItemKey("hycolony:sand");

    @Test
    void insertMergesThenFillsEmpty() {
        Inventory inv = new Inventory(4);
        assertNull(inv.insert(new ItemAmount(STONE, 70), item -> 64));
        assertNull(inv.insert(new ItemAmount(STONE, 30), item -> 64));
        assertEquals(List.of(new ItemAmount(STONE, 64), new ItemAmount(STONE, 36)), inv.contents());
    }

    @Test
    void insertReturnsRemainderWhenFull() {
        Inventory inv = new Inventory(1);
        ItemAmount remainder = inv.insert(new ItemAmount(STONE, 15), item -> 10);
        assertEquals(new ItemAmount(STONE, 5), remainder);
    }

    @Test
    void extractTakesFromLastSlotsFirst() {
        Inventory inv = new Inventory(3);
        inv.insert(new ItemAmount(STONE, 12), item -> 5); // slots: 5, 5, 2
        int removed = inv.extract(STONE, 6);
        assertEquals(6, removed);
        assertEquals(List.of(new ItemAmount(STONE, 5), new ItemAmount(STONE, 1)), inv.contents());
    }

    @Test
    void countAndFreeSlots() {
        Inventory inv = new Inventory(3);
        inv.insert(new ItemAmount(STONE, 5), item -> 64);
        assertEquals(5, inv.count(STONE));
        assertEquals(0, inv.count(DIRT));
        assertEquals(2, inv.freeSlots());
        assertFalse(inv.isFull());
        inv.insert(new ItemAmount(DIRT, 1), item -> 64);
        inv.insert(new ItemAmount(SAND, 1), item -> 64);
        assertTrue(inv.isFull());
        assertEquals(0, inv.freeSlots());
    }

    @Test
    void roundTripJson() {
        Inventory inv = new Inventory(3);
        inv.insert(new ItemAmount(STONE, 5), item -> 64);
        JsonArray json = inv.write();
        Inventory restored = Inventory.read(json, 3);
        assertEquals(3, restored.size());
        assertEquals(inv.contents(), restored.contents());
    }
}
