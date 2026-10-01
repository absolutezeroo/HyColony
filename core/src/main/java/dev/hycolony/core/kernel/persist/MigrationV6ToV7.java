package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Schema 7 (town hall): the colony's style is its town hall's (MC IColony.getStructurePack), new citizens move in (MC
 * MOVE_IN default) and past events have no position.
 */
final class MigrationV6ToV7 {
    private MigrationV6ToV7() {}

    static JsonObject apply(JsonObject doc) {
        doc.addProperty("style", townHallStyle(doc));
        JsonObject settings = doc.get("settings") instanceof JsonObject s ? s : new JsonObject();
        settings.addProperty("moveIn", true);
        doc.add("settings", settings);
        if (doc.get("eventLog") instanceof JsonArray log) {
            for (JsonElement el : log) {
                if (el instanceof JsonObject e) {
                    e.add("pos", JsonNull.INSTANCE);
                }
            }
        }
        return doc;
    }

    /** The saved town hall's style; "" without one. */
    private static String townHallStyle(JsonObject doc) {
        if (!(doc.get("buildings") instanceof JsonArray buildings)) {
            return "";
        }
        for (JsonElement el : buildings) {
            if (el instanceof JsonObject b
                    && b.get("type") instanceof JsonPrimitive type
                    && "hycolony:townhall".equals(type.getAsString())
                    && b.get("style") instanceof JsonPrimitive style) {
                return style.getAsString();
            }
        }
        return "";
    }
}
