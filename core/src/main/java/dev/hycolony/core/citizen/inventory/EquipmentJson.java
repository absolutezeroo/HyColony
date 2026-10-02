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
     */
    public static CitizenEquipment read(JsonObject o) {
        JsonArray armor = o.get("armor") instanceof JsonArray a ? a : new JsonArray();
        CitizenEquipment e = new CitizenEquipment(Inventory.read(armor, CitizenEquipment.ARMOR_SLOTS));
        e.hold(Hand.MAIN, slot(o.get("heldMain")));
        e.hold(Hand.OFF, slot(o.get("heldOff")));
        return e;
    }

    private static int slot(JsonElement saved) {
        int slot = saved instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : CitizenEquipment.NO_SLOT;
        return slot >= 0 && slot < CitizenData.INVENTORY_SLOTS ? slot : CitizenEquipment.NO_SLOT;
    }
}
