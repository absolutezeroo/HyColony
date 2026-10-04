package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Schema 11 (citizens' death): the colony gets no statistics yet (MC StatisticsManager), and each citizen grieves for
 * nobody (MC CitizenMournHandler).
 */
final class MigrationV10ToV11 {
    private MigrationV10ToV11() {}

    static JsonObject apply(JsonObject doc) {
        doc.add("statistics", new JsonObject());
        if (doc.get("citizens") instanceof JsonArray citizens) {
            for (JsonElement e : citizens) {
                if (e instanceof JsonObject citizen) {
                    JsonObject mourning = new JsonObject();
                    mourning.addProperty("mourning", false);
                    mourning.add("deceased", new JsonArray());
                    citizen.add("mourning", mourning);
                }
            }
        }
        return doc;
    }
}
