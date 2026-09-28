package dev.hycolony.core.ornament;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The saved list of every variant ever created ({@code {"schemaVersion":1,"variants":[<VariantKey#id()>...]}}),
 * recreated at boot before chunks load: a chunk saves a block by its key. Nothing is dropped: an id no shape reads
 * any more and a non-text entry (raw JSON in {@code foreign}) are written back as they were; a file of a newer
 * schema is {@code readOnly}.
 */
public record SavedVariants(List<String> ids, List<String> foreign, boolean readOnly) {
    static final int SCHEMA_VERSION = 1;

    public SavedVariants {
        ids = List.copyOf(ids);
        foreign = List.copyOf(foreign);
    }

    /** No variant yet. */
    public static SavedVariants empty() {
        return new SavedVariants(List.of(), List.of(), false);
    }

    /**
     * Reads either format (the prototype's bare array, or the versioned object); a newer schema gives an empty,
     * read-only list.
     *
     * @throws IllegalArgumentException when json is not one of them
     */
    public static SavedVariants parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (isNewer(root)) {
                return new SavedVariants(List.of(), List.of(), true);
            }
            List<String> ids = new ArrayList<>();
            List<String> foreign = new ArrayList<>();
            for (JsonElement entry : entries(root)) {
                if (entry instanceof JsonPrimitive text && text.isString()) {
                    ids.add(text.getAsString());
                } else {
                    foreign.add(entry.toString());
                }
            }
            return new SavedVariants(ids, foreign, false);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("not a saved variant list", e);
        }
    }

    /** The file content: {@code ids}, then {@code foreign} as they were read. */
    public String toJson() {
        JsonArray variants = new JsonArray();
        ids.forEach(variants::add);
        foreign.forEach(raw -> variants.add(JsonParser.parseString(raw)));
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.add("variants", variants);
        return root.toString();
    }

    /** This list with id added at the end; itself when id is already there. */
    public SavedVariants with(String id) {
        if (ids.contains(id)) {
            return this;
        }
        List<String> more = new ArrayList<>(ids);
        more.add(id);
        return new SavedVariants(more, foreign, readOnly);
    }

    /** The keys of the ids catalog can read; the others stay in {@code ids}, absent here. */
    public List<VariantKey> keys(ShapeCatalog catalog) {
        return ids.stream()
                .map(id -> VariantKey.parse(id, catalog))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Whether root is a versioned object of a schema newer than this code reads. */
    private static boolean isNewer(JsonElement root) {
        return root instanceof JsonObject object
                && object.get("schemaVersion") instanceof JsonPrimitive version
                && version.isNumber()
                && version.getAsInt() > SCHEMA_VERSION;
    }

    /** The entry array of either format; throws IllegalStateException on anything else. */
    private static JsonArray entries(JsonElement root) {
        if (root instanceof JsonArray array) {
            return array;
        }
        if (root instanceof JsonObject object && object.get("variants") instanceof JsonArray array) {
            return array;
        }
        throw new IllegalStateException("no variant array");
    }
}
