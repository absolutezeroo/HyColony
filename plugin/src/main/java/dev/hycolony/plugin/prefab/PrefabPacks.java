package dev.hycolony.plugin.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.construction.blueprint.PackInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code hycolony/packs.json} table (merged with the enabled sub-plugins' fragments): {@code layout}, the
 * blueprint folder of each hut type as MC's packs lay them out, and {@code packs}, each style's metadata as
 * Structurize's {@code pack.json} (name, desc, authors, icon, owner). Tolerant: a missing or malformed value takes its
 * default (PackInfo.defaults, PackInfo.DEFAULT_CATEGORY).
 */
public final class PrefabPacks {
    private final Map<String, String> layout;
    private final Map<String, JsonObject> packs;

    private PrefabPacks(Map<String, String> layout, Map<String, JsonObject> packs) {
        this.layout = layout;
        this.packs = packs;
    }

    /** The table read from {@code json}; an empty table for anything that is not a JSON object. */
    public static PrefabPacks of(JsonElement json) {
        Map<String, String> layout = new HashMap<>();
        Map<String, JsonObject> packs = new HashMap<>();
        if (json instanceof JsonObject root) {
            if (root.get("layout") instanceof JsonObject l) {
                l.entrySet().forEach(e -> string(e.getValue()).ifPresent(v -> layout.put(e.getKey(), v)));
            }
            if (root.get("packs") instanceof JsonObject p) {
                p.entrySet().stream()
                        .filter(e -> e.getValue() instanceof JsonObject)
                        .forEach(e -> packs.put(e.getKey(), e.getValue().getAsJsonObject()));
            }
        }
        return new PrefabPacks(layout, packs);
    }

    /** The style's metadata, each missing value taken from {@link PackInfo#defaults}. */
    public PackInfo pack(String style) {
        PackInfo d = PackInfo.defaults(style);
        JsonObject o = packs.get(style);
        if (o == null) {
            return d;
        }
        return new PackInfo(
                string(o.get("name")).orElse(d.name()),
                string(o.get("desc")).orElse(d.desc()),
                authors(o.get("authors")),
                string(o.get("icon")).orElse(d.icon()),
                string(o.get("owner")).orElse(d.owner()));
    }

    /** The hut type's blueprint folder; {@link PackInfo#DEFAULT_CATEGORY} when the layout names none. */
    public String category(String buildingTypeId) {
        return layout.getOrDefault(buildingTypeId, PackInfo.DEFAULT_CATEGORY);
    }

    private static List<String> authors(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e instanceof JsonArray a) {
            a.forEach(x -> string(x).ifPresent(out::add));
        }
        return out;
    }

    private static Optional<String> string(JsonElement e) {
        return e instanceof JsonPrimitive p && p.isString() ? Optional.of(p.getAsString()) : Optional.empty();
    }
}
