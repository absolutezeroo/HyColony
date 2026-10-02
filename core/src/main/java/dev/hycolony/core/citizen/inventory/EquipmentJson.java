package dev.hycolony.core.citizen.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.item.Inventory;

/**
 * A citizen's {@link CitizenEquipment} in its saved JSON (schema 10): {@code armor}, {@code heldMain} and {@code
 * heldOff}, as MC CitizenData saves TAG_HELD_ITEM_SLOT and its offhand twin.
 */
public final class EquipmentJson {
    private EquipmentJson() {}

    /** Writes {@code e} into the citizen object {@code o}. */
    public static void write(CitizenEquipment e, JsonObject o) {
        o.add("armor", e.armor().write());
        o.addProperty("heldMain", e.held(Hand.MAIN));
        o.addProperty("heldOff", e.held(Hand.OFF));
    }

    /**
     * The equipment saved in the citizen object {@code o}: the first 4 armour pieces; a missing or bad hand, or one
     * outside the inventory, holds nothing (CLAUDE.md § 5).
     *
     * <p>Deviation from MC: a missing hand holds nothing; MC's getInt reads it as slot 0.
     */
    public static CitizenEquipment read(JsonObject o) {
        CitizenEquipment e = new CitizenEquipment(Inventory.read(armor(o), CitizenEquipment.ARMOR_SLOTS));
        e.hold(Hand.MAIN, valid(slot(o.get("heldMain"))));
        e.hold(Hand.OFF, valid(slot(o.get("heldOff"))));
        return e;
    }

    /** Whether {@link #read} had to repair {@code o}: a hand outside the inventory, or more than 4 armour pieces. */
    public static boolean needsRepair(JsonObject o) {
        int main = slot(o.get("heldMain"));
        int off = slot(o.get("heldOff"));
        return valid(main) != main || valid(off) != off || armor(o).size() > CitizenEquipment.ARMOR_SLOTS;
    }

    private static JsonArray armor(JsonObject o) {
        return o.get("armor") instanceof JsonArray a ? a : new JsonArray();
    }

    private static int slot(JsonElement saved) {
        return saved instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : CitizenEquipment.NO_SLOT;
    }

    private static int valid(int slot) {
        return slot >= 0 && slot < CitizenData.INVENTORY_SLOTS ? slot : CitizenEquipment.NO_SLOT;
    }
}
