package dev.hydomum.plugin.persistence;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.api.VariantKey;
import dev.hydomum.core.SavedVariants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

/**
 * Reads and writes the saved variant list ({@link SavedVariants}, the format) so variants are registered again at
 * boot, before any chunk that holds one loads. Chunks save a block by its key: without this, a variant block
 * reloads as Hytale's "Unknown" block.
 *
 * <p>Writes go to a {@code .tmp} file moved over the old one; an unreadable file is moved aside to {@code .corrupt}
 * before the next write; a file of a newer schema is never rewritten.
 */
public final class VariantStore {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Path file;
    private SavedVariants saved = SavedVariants.empty();

    /** @param file the JSON file, created on the first {@link #add} */
    public VariantStore(Path file) {
        this.file = file;
    }

    /**
     * Reads the file and returns the entries shapes can read; a missing file is empty. An unknown entry is logged
     * and kept for rewriting; an unreadable file is logged SEVERE and renamed to {@code .corrupt}; a file of a newer
     * schema is logged SEVERE, returns empty and is never rewritten.
     */
    public synchronized List<VariantKey> load(ShapeCatalog shapes) {
        saved = SavedVariants.empty();
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            saved = SavedVariants.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: could not read %s, no variant restored", file);
            moveAside();
            return List.of();
        }
        if (saved.readOnly()) {
            LOG.at(Level.SEVERE).log("hyornament: %s is from a newer version, left untouched", file);
            return List.of();
        }
        List<VariantKey> keys = saved.keys(shapes);
        if (keys.size() < saved.ids().size()) {
            LOG.at(Level.WARNING).log(
                    "hyornament: %d saved variant(s) name no known shape, kept in %s",
                    saved.ids().size() - keys.size(), file);
        }
        return keys;
    }

    /** Records {@code keys} and rewrites the file once when one is new; a write failure is logged, not thrown. */
    public synchronized void add(List<VariantKey> keys) {
        int before = saved.ids().size();
        SavedVariants more = saved;
        for (VariantKey key : keys) {
            more = more.with(key.id());
        }
        if (more.equals(saved)) {
            return;
        }
        saved = more;
        if (saved.readOnly()) {
            LOG.at(Level.WARNING).log(
                    "hyornament: %d new variant(s) not saved, %s is read-only",
                    saved.ids().size() - before, file);
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, saved.toJson(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyornament: could not save %s", file);
        }
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
