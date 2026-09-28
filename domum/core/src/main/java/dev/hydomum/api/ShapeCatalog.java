package dev.hydomum.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The shapes of the generated manifest ({@code hycolony/ornament/shapes.json}), looked up by id. */
public final class ShapeCatalog {
    /** Manifest format this code reads; a newer one gives an empty catalog rather than a wrong one. */
    static final int SCHEMA_VERSION = 1;
    /** DO blocks have one or two material components. */
    private static final int MAX_SLOTS = 2;

    private final List<OrnamentShape> shapes;
    private final Map<String, OrnamentShape> byId;
    private final int skipped;

    private ShapeCatalog(List<OrnamentShape> shapes, int skipped) {
        this.shapes = List.copyOf(shapes);
        this.skipped = skipped;
        this.byId = shapes.stream()
                .collect(Collectors.toUnmodifiableMap(
                        s -> s.id().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    }

    /** Reads a manifest leniently: incomplete entries are skipped; unreadable or newer input gives no shapes. */
    public static ShapeCatalog parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return new ShapeCatalog(List.of(), 0);
            }
            JsonObject object = root.getAsJsonObject();
            if (intOr(object, "schemaVersion", SCHEMA_VERSION) > SCHEMA_VERSION
                    || !(object.get("shapes") instanceof JsonArray entries)) {
                return new ShapeCatalog(List.of(), 0);
            }
            List<OrnamentShape> shapes = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonElement entry : entries) {
                // A repeated id keeps its first shape, as shape(id) does.
                shape(entry)
                        .filter(s -> seen.add(s.id().toLowerCase(Locale.ROOT)))
                        .ifPresent(shapes::add);
            }
            return new ShapeCatalog(shapes, entries.size() - shapes.size());
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return new ShapeCatalog(List.of(), 0);
        }
    }

    /** The shape with this id, case ignored. */
    public Optional<OrnamentShape> shape(String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<OrnamentShape> all() {
        return shapes;
    }

    /** How many manifest entries parse left out (incomplete, out of bounds or repeated): 0 for a sound manifest. */
    public int skipped() {
        return skipped;
    }

    /** This catalog without the shapes keep refuses (the plugin drops shapes whose template is not loaded). */
    public ShapeCatalog retain(Predicate<OrnamentShape> keep) {
        return new ShapeCatalog(shapes.stream().filter(keep).toList(), skipped);
    }

    /**
     * One manifest entry, or empty when its id, template, group or slots are missing or malformed, or its cutter
     * quantity is below 1 (absent: 1, one item per craft).
     */
    private static Optional<OrnamentShape> shape(JsonElement entry) {
        if (!(entry instanceof JsonObject object)) {
            return Optional.empty();
        }
        Optional<List<String>> tags = slotTags(object);
        Optional<String> id = string(object, "id");
        Optional<String> template = string(object, "template");
        Optional<String> group = string(object, "group");
        int cutterQuantity = intOr(object, "cutterQuantity", 1);
        if (tags.isEmpty() || id.isEmpty() || template.isEmpty() || group.isEmpty() || cutterQuantity < 1) {
            return Optional.empty();
        }
        boolean optionalSecond = primitive(object, "optionalSecond")
                .filter(JsonPrimitive::isBoolean)
                .map(JsonPrimitive::getAsBoolean)
                .orElse(false);
        return Optional.of(
                new OrnamentShape(id.get(), template.get(), group.get(), tags.get(), optionalSecond, cutterQuantity));
    }

    /** The entry's slot tags, or empty when there are none, more than DO's two, or one is not a string. */
    private static Optional<List<String>> slotTags(JsonObject object) {
        if (!(object.get("slots") instanceof JsonArray slots) || slots.isEmpty() || slots.size() > MAX_SLOTS) {
            return Optional.empty();
        }
        List<String> tags = new ArrayList<>();
        for (JsonElement slot : slots) {
            if (!(slot instanceof JsonPrimitive tag) || !tag.isString()) {
                return Optional.empty();
            }
            tags.add(tag.getAsString());
        }
        return Optional.of(tags);
    }

    private static Optional<String> string(JsonObject object, String key) {
        return primitive(object, key).filter(JsonPrimitive::isString).map(JsonPrimitive::getAsString);
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        return primitive(object, key)
                .filter(JsonPrimitive::isNumber)
                .map(JsonPrimitive::getAsInt)
                .orElse(fallback);
    }

    /** The value of key when it is a JSON primitive. */
    private static Optional<JsonPrimitive> primitive(JsonObject object, String key) {
        return object.get(key) instanceof JsonPrimitive value ? Optional.of(value) : Optional.empty();
    }
}
