package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Schema 10 (citizen inventory as MC): each citizen gets empty armour and empty hands (MC InventoryCitizen's
 * armorInventory, mainItem and offhandItem, saved by CitizenData as TAG_HELD_ITEM_SLOT and its offhand twin).
 */
final class MigrationV9ToV10 {
    /** MC InventoryCitizen.NO_SLOT; kernel does not see the citizen package. */
    private static final int NO_SLOT = -1;

    private MigrationV9ToV10() {}

    static JsonObject apply(JsonObject doc) {
        if (doc.get("citizens") instanceof JsonArray citizens) {
            for (JsonElement e : citizens) {
                if (e instanceof JsonObject citizen) {
                    citizen.add("armor", new JsonArray());
                    citizen.addProperty("heldMain", NO_SLOT);
                    citizen.addProperty("heldOff", NO_SLOT);
                }
            }
        }
        return doc;
    }
}
