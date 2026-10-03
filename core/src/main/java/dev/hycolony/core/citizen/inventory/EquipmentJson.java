package dev.hycolony.core.citizen.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's {@link CitizenEquipment} in its saved JSON (schema 10): {@code armor}, {@code heldMain} and {@code
 * heldOff}, as MC CitizenData saves TAG_HELD_ITEM_SLOT and its offhand twin; and the wear of the armour it carries.
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

    /**
     * Counts the wear of the armour pieces in the citizen's {@code inventory} in hits, as schema 10 does, when the
     * saved citizen {@code o} counted it in points ({@link LegacyArmorWear}).
     */
    public static void readArmorWear(JsonObject o, Inventory inventory, ArmorCatalog armors, ItemCatalog items) {
        if (LegacyArmorWear.marked(o)) {
            LegacyArmorWear.convert(inventory, armors, items);
        }
    }

    /**
     * Whether reading {@code o} repairs it: a hand that is no number or outside the inventory, more than 4 armour
     * pieces, or an armour wear counted in points. A missing key only takes its default.
     */
    public static boolean needsRepair(JsonObject o) {
        return badHand(o.get("heldMain"))
                || badHand(o.get("heldOff"))
                || armor(o).size() > CitizenEquipment.ARMOR_SLOTS
                || LegacyArmorWear.marked(o);
    }

    private static boolean badHand(@Nullable JsonElement saved) {
        if (saved == null) {
            return false;
        }
        int slot = slot(saved);
        return !(saved instanceof JsonPrimitive p && p.isNumber()) || valid(slot) != slot;
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
