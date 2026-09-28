package dev.hycolony.plugin.ornament.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.hycolony.plugin.ornament.api.VariantKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * The variants ever created, kept as {@code {"schemaVersion":1,"variants":[<VariantKey#id()>...]}} so they are
 * registered again at boot, before any chunk that holds one loads. Chunks save a block by its key: without this, a
 * variant block reloads as Hytale's "Unknown" block.
 *
 * <p>Nothing is ever dropped: an entry it cannot read (material since renamed) is written back as it was, and an
 * unreadable file is moved aside to {@code .corrupt} before the next write, and a file of a newer schema is left
 * untouched.
 */
public final class VariantStore {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final int SCHEMA_VERSION = 1;

    private final Path file;
    /** Every text entry of the file, parsable or not, in file order. */
    private final Set<String> ids = new LinkedHashSet<>();
    /** Non-text entries, written back as they were. */
    private final List<JsonElement> foreign = new ArrayList<>();
    /** Set when the file comes from a newer schema: it is then never rewritten. */
    private boolean readOnly;

    /** @param file the JSON file, created on the first {@link #add} */
    public VariantStore(Path file) {
        this.file = file;
    }

    /**
     * Reads the file and returns the entries it can read; a missing file is empty. An unknown entry is logged and
     * kept for rewriting; an unreadable file is logged SEVERE and renamed to {@code .corrupt}; a file of a newer
     * schema is logged SEVERE, returns empty and is never rewritten.
     */
    public synchronized List<VariantKey> load() {
        ids.clear();
        foreign.clear();
        readOnly = false;
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (version(root) > SCHEMA_VERSION) {
                // Written by a newer HyColony: neither read it as v1 nor overwrite it.
                LOG.at(Level.SEVERE).log("hyornament: %s is from a newer version, left untouched", file);
                readOnly = true;
                return List.of();
            }
            for (JsonElement e : entries(root)) {
                if (e.isJsonPrimitive()) {
                    ids.add(e.getAsString());
                } else {
                    foreign.add(e);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: could not read %s, no variant restored", file);
            moveAside();
            return List.of();
        }
        List<VariantKey> keys = new ArrayList<>();
        for (String id : ids) {
            Optional<VariantKey> key = VariantKey.parse(id);
            key.ifPresentOrElse(
                    keys::add, () -> LOG.at(Level.WARNING).log("hyornament: unknown variant %s kept in %s", id, file));
        }
        return keys;
    }

    /** Records {@code key} and rewrites the file when it is new; a write failure is logged, not thrown. */
    public synchronized void add(VariantKey key) {
        if (!ids.add(key.id())) {
            return;
        }
        if (readOnly) {
            LOG.at(Level.WARNING).log("hyornament: %s not saved, %s is read-only", key.id(), file);
            return;
        }
        JsonArray variants = new JsonArray();
        ids.forEach(variants::add);
        foreign.forEach(variants::add);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.add("variants", variants);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: could not save %s", file);
        }
    }

    /** The file's {@code schemaVersion}; 0 for the bare-array format. */
    private static int version(JsonElement root) {
        return root.isJsonObject() && root.getAsJsonObject().has("schemaVersion")
                ? root.getAsJsonObject().get("schemaVersion").getAsInt()
                : 0;
    }

    /** The entry array of either format (versioned object, or bare array); throws on anything else. */
    private static JsonArray entries(JsonElement root) {
        return root.isJsonArray()
                ? root.getAsJsonArray()
                : root.getAsJsonObject().getAsJsonArray("variants");
    }

    /** Renames the unreadable file so the next {@link #add} cannot overwrite what it held. */
    private void moveAside() {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: could not move %s aside", file);
        }
    }
}
