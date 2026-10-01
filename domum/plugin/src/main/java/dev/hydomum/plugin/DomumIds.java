package dev.hydomum.plugin;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.hypixel.hytale.logger.HytaleLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * HyDomum's logical keys -> Hytale asset ids, read from its own hydomum/id-map.json (written by tools/domum), as
 * HyColony's IdMap reads hycolony/id-map.json: the cutter's sounds, DO's material tags and the vanilla fence
 * families.
 */
final class DomumIds {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String FILE = "/hydomum/id-map.json";

    private record Data(
            @Nullable Map<String, String> sounds,
            @Nullable Map<String, List<String>> ornamentTags,
            @Nullable Map<String, List<String>> connections) {}

    private final Data data;

    private DomumIds(Data data) {
        this.data = data;
    }

    /** The id-map on the mod's classpath; empty, with a warning, when the file is missing or unreadable. */
    static DomumIds load() {
        try (InputStream in = DomumIds.class.getResourceAsStream(FILE)) {
            if (in == null) {
                LOG.at(Level.WARNING).log("HyDomum: %s is missing", FILE);
                return new DomumIds(new Data(null, null, null));
            }
            Data data = new Gson().fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), Data.class);
            return new DomumIds(Objects.requireNonNullElse(data, new Data(null, null, null)));
        } catch (IOException | JsonParseException e) {
            LOG.at(Level.WARNING).withCause(e).log("HyDomum: %s is unreadable", FILE);
            return new DomumIds(new Data(null, null, null));
        }
    }

    /** The sound event of {@code key} (e.g. cutter.open); empty when not mapped. */
    Optional<String> sound(String key) {
        return Optional.ofNullable(Objects.requireNonNullElse(data.sounds(), Map.<String, String>of())
                .get(key));
    }

    /** The vanilla fences, walls and bars by family (core NeighbourKind name -> block ids); empty when none. */
    Map<String, List<String>> connections() {
        return Map.copyOf(Objects.requireNonNullElse(data.connections(), Map.of()));
    }

    /** Domum Ornamentum's material tags (DO tag -> Hytale block ids); empty when the file has none. */
    Map<String, List<String>> ornamentTags() {
        return Map.copyOf(Objects.requireNonNullElse(data.ornamentTags(), Map.of()));
    }
}
