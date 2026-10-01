package dev.hycolony.core.kernel.persist;

import com.google.gson.JsonObject;

/** Schema 9 (construction tape): the colony's construction tape setting, on (MC BuildingTownHall tape's default). */
final class MigrationV8ToV9 {
    private MigrationV8ToV9() {}

    static JsonObject apply(JsonObject doc) {
        JsonObject settings = doc.get("settings") instanceof JsonObject s ? s : new JsonObject();
        settings.addProperty("constructionTape", true);
        doc.add("settings", settings);
        return doc;
    }
}
