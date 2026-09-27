package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InventoryTest {
    private static final ItemKey STONE = new ItemKey("hycolony:stone");
    private static final ItemKey DIRT = new ItemKey("hycolony:dirt");
    private static final ItemKey SAND = new ItemKey("hycolony:sand");
    private static final ItemKey PICK = new ItemKey("hycolony:pickaxe");

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

    @Test
    void setReplacesOrEmptiesOneSlot() {
        Inventory inv = new Inventory(3);
        inv.set(1, Optional.of(new ItemAmount(STONE, 7)));
        assertEquals(Optional.of(new ItemAmount(STONE, 7)), inv.slot(1));
        inv.set(1, Optional.of(new ItemAmount(DIRT, 2)));
        assertEquals(Optional.of(new ItemAmount(DIRT, 2)), inv.slot(1));
        inv.set(1, Optional.empty());
        assertTrue(inv.slot(1).isEmpty());
    }

    @Test
    void everyMutationBumpsTheChangeCounterButNoOpsDoNot() {
        Inventory inv = new Inventory(2);
        long start = inv.changes();
        inv.insert(new ItemAmount(STONE, 3), item -> 64);
        long afterInsert = inv.changes();
        assertNotEquals(start, afterInsert);
        inv.extract(DIRT, 5);
        assertEquals(afterInsert, inv.changes(), "nothing extracted");
        inv.extract(STONE, 1);
        long afterExtract = inv.changes();
        assertNotEquals(afterInsert, afterExtract);
        inv.set(1, Optional.of(new ItemAmount(SAND, 1)));
        assertNotEquals(afterExtract, inv.changes());
    }

    @Test
    void copyIsIndependentOfTheOriginal() {
        Inventory inv = new Inventory(2);
        inv.insert(new ItemAmount(STONE, 3), item -> 64);
        Inventory copy = inv.copy();
        inv.set(0, Optional.empty());
        assertEquals(2, copy.size());
        assertEquals(Optional.of(new ItemAmount(STONE, 3)), copy.slot(0));
    }

    @Test
    void aDamagedStackKeepsItsDamageAndMergesOnlyWithTheSameDamage() {
        Inventory inv = new Inventory(3);
        inv.insert(new ItemAmount(PICK, 1, 4), item -> 64);
        inv.insert(new ItemAmount(PICK, 1), item -> 64);
        inv.insert(new ItemAmount(PICK, 1, 4), item -> 64);
        assertEquals(List.of(new ItemAmount(PICK, 2, 4), new ItemAmount(PICK, 1)), inv.contents());
    }

    @Test
    void damageWearsTheSlotAndBreaksItExactlyAtItsDurability() {
        Inventory inv = new Inventory(2);
        inv.set(1, Optional.of(new ItemAmount(PICK, 1)));
        long before = inv.changes();
        assertFalse(inv.damage(1, 1, 3));
        assertFalse(inv.damage(1, 1, 3));
        assertEquals(Optional.of(new ItemAmount(PICK, 1, 2)), inv.slot(1));
        assertNotEquals(before, inv.changes());
        assertTrue(inv.damage(1, 1, 3)); // MC hurtAndBreak: the third use breaks it
        assertTrue(inv.slot(1).isEmpty());
    }

    @Test
    void anUnbreakableToolOrAnEmptySlotTakesNoDamage() {
        Inventory inv = new Inventory(2);
        inv.set(0, Optional.of(new ItemAmount(PICK, 1)));
        long before = inv.changes();
        assertFalse(inv.damage(0, 1, 0));
        assertFalse(inv.damage(1, 1, 3));
        assertEquals(Optional.of(new ItemAmount(PICK, 1)), inv.slot(0));
        assertEquals(before, inv.changes());
    }

    @Test
    void damageIsSavedOnlyWhenAboveZeroAndReadBack() {
        Inventory inv = new Inventory(2);
        inv.set(0, Optional.of(new ItemAmount(PICK, 1, 7)));
        inv.set(1, Optional.of(new ItemAmount(STONE, 3)));
        JsonArray json = inv.write();
        assertEquals(7, json.get(0).getAsJsonObject().get("damage").getAsInt());
        assertFalse(json.get(1).getAsJsonObject().has("damage"));
        assertEquals(inv.contents(), Inventory.read(json, 2).contents());
    }
}
