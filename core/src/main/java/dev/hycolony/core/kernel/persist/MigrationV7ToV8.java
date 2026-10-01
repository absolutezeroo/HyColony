package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Schema 8 (hunger and happiness): every citizen has eaten nothing lately, has not just eaten, its job is idle (MC
 * JobStatus.IDLE) and its happiness modifiers start afresh.
 */
final class MigrationV7ToV8 {
    private MigrationV7ToV8() {}

    static JsonObject apply(JsonObject doc) {
        if (doc.get("citizens") instanceof JsonArray citizens) {
            for (JsonElement el : citizens) {
                if (el instanceof JsonObject c) {
                    c.addProperty("justAte", false);
                    c.add("foodHistory", new JsonArray());
                    c.addProperty("jobStatus", "IDLE");
                    c.add("happiness", new JsonArray());
                }
            }
        }
        return doc;
    }
}
