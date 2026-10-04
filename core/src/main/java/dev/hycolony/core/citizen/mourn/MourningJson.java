package dev.hycolony.core.citizen.mourn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A citizen's mourning in its save (MC CitizenMournHandler.write/read: TAG_MOURNING and the deceased's names). Reading
 * is tolerant: a missing key or a name that is no string is left out (CLAUDE.md § 5).
 */
public final class MourningJson {
    private MourningJson() {}

    /** {@code mourning} as {@code {"mourning": bool, "deceased": [names]}}. */
    public static JsonObject write(CitizenMourning mourning) {
        JsonObject o = new JsonObject();
        o.addProperty("mourning", mourning.isMourning());
        JsonArray names = new JsonArray();
        mourning.deceased().forEach(names::add);
        o.add("deceased", names);
        return o;
    }

    /** Restores {@code saved} into {@code mourning}; nothing to grieve for when it is absent or no object. */
    public static void read(JsonElement saved, CitizenMourning mourning) {
        if (!(saved instanceof JsonObject o)) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        if (o.get("deceased") instanceof JsonArray deceased) {
            for (JsonElement e : deceased) {
                if (e instanceof JsonPrimitive p && p.isString()) {
                    names.add(p.getAsString());
                }
            }
        }
        boolean mourns = o.get("mourning") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
        mourning.restore(names, mourns && !names.isEmpty());
    }
}
