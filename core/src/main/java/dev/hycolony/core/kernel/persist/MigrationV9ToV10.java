package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Schema 10 (citizen inventory as MC): each citizen gets empty armour and empty hands (MC InventoryCitizen's
 * armorInventory, mainItem and offhandItem, saved by CitizenData as TAG_HELD_ITEM_SLOT and its offhand twin), and is
 * marked so that its armour's wear, counted in points until now, is read in hits (LegacyArmorWear, with the catalog).
 */
final class MigrationV9ToV10 {
    /** MC InventoryCitizen.NO_SLOT; kernel does not see the citizen package. */
    private static final int NO_SLOT = -1;
    /** LegacyArmorWear.KEY, which kernel does not see. */
    private static final String ARMOR_WEAR_IN_POINTS = "armorWearInPoints";

    private MigrationV9ToV10() {}

    static JsonObject apply(JsonObject doc) {
        if (doc.get("citizens") instanceof JsonArray citizens) {
            for (JsonElement e : citizens) {
                if (e instanceof JsonObject citizen) {
                    citizen.add("armor", new JsonArray());
                    citizen.addProperty("heldMain", NO_SLOT);
                    citizen.addProperty("heldOff", NO_SLOT);
                    citizen.addProperty(ARMOR_WEAR_IN_POINTS, true);
                }
            }
        }
        return doc;
    }
}
